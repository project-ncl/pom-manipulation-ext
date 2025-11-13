/*
 * Copyright (C) 2012 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.mavenmanipulator.core.impl;

import static org.jboss.pnc.mavenmanipulator.core.impl.Version.findHighestMatchingBuildNumber;
import static org.jboss.pnc.mavenmanipulator.core.util.IdUtils.gav;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.commonjava.atlas.maven.ident.ref.ProjectRef;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectRef;
import org.commonjava.maven.galley.maven.model.view.meta.MavenMetadataView;
import org.jboss.pnc.mavenmanipulator.common.ManipulationException;
import org.jboss.pnc.mavenmanipulator.common.model.Project;
import org.jboss.pnc.mavenmanipulator.common.util.PropertyResolver;
import org.jboss.pnc.mavenmanipulator.core.ManipulationSession;
import org.jboss.pnc.mavenmanipulator.core.state.VersioningState;
import org.jboss.pnc.mavenmanipulator.io.resolver.GalleyAPIWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Component that calculates project version modifications, based on configuration stored in {@link VersioningState}.
 * Snapshots may/may not be preserved, and either a static or incremental (calculated) version qualifier may / may not
 * be incorporated in the version. The calculator strives for OSGi compatibility, so the use of '.' and '-' qualifier
 * separators will vary accordingly. See: <a href="http://www.aqute.biz/Bnd/Versioning">Versioning</a> and
 * <a href="http://www.osgi.org/wiki/uploads/Links/SemanticVersioning.pdf">SemanticVersioning</a> for an explanation of
 * OSGi versioning.
 *
 * @author jdcasey
 */
@Named
@Singleton
@SuppressWarnings("WeakerAccess") // Public API.
public class VersionCalculator {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private GalleyAPIWrapper readerWrapper;

    @Inject
    public VersionCalculator(final GalleyAPIWrapper readerWrapper) {
        this.readerWrapper = readerWrapper;
    }

    // Used by GME.
    public VersionCalculator() {
    }

    /**
     * Calculate any project version changes for the given set of projects, and return them in a Map keyed by project
     * GA.
     *
     * @param projects the Projects to adjust.
     * @param session the container session.
     * @return a collection of GAV : new Version
     * @throws ManipulationException if an error occurs.
     */
    public Map<ProjectVersionRef, String> calculateVersioningChanges(
            final List<Project> projects,
            final ManipulationSession session)
            throws ManipulationException {
        final VersioningState state = session.getState(VersioningState.class);
        final Map<ProjectVersionRef, String> versionsByGAV = new HashMap<>();
        final Set<String> versionsWithBuildNums = new HashSet<>();

        if (logger.isDebugEnabled()) {
            logger.debug(
                    "Got the following version suffixes:{}  Static: {}{}  Incremental: {}",
                    System.lineSeparator(),
                    state.getSuffix(),
                    System.lineSeparator(),
                    state.getIncrementalSerialSuffix());
            logger.debug("Got the following version override: {}", state.getOverride());
        }

        for (final Project project : projects) {
            String originalVersion = PropertyResolver
                    .resolveInheritedProperties(session, project, project.getVersion());
            String modifiedVersion = calculate(project.getGroupId(), project.getArtifactId(), originalVersion, session);

            logger.debug(
                    "Caching version against project {} with parent {} and modified version {}",
                    project.getKey(),
                    project.getModelParent(),
                    modifiedVersion);

            versionsByGAV.put(project.getKey(), modifiedVersion);

            if (Version.hasBuildNumber(modifiedVersion)) {
                versionsWithBuildNums.add(modifiedVersion);
            }
        }

        // Have to loop through the versions a second time to make sure that the versions are in sync
        // between projects in the reactor.
        logger.debug("Syncing projects within reactor...");
        for (final Project project : projects) {
            final String originalVersion = project.getVersion();

            String modifiedVersion = versionsByGAV.get(project.getKey());

            // If there is only a single version there is no real need to try and find the highest matching.
            // This also fixes the problem where there is a single version and leading zeros.
            int buildNumber = findHighestMatchingBuildNumber(modifiedVersion, versionsWithBuildNums);

            // If the buildNumber is greater than zero, it means we found a match and have to
            // set the build number to avoid version conflicts.
            if (buildNumber > 0) {
                // We ONLY pass the incrementalSerialSuffixPadding if we are using incrementalSuffix (i.e. not static suffix).
                // This keeps it consistent with the 'calculate' method..
                String paddedBuildNum = StringUtils.leftPad(
                        Integer.toString(buildNumber),
                        Version.getBuildNumberPadding(
                                (state.getSuffix() == null ? state.getIncrementalSerialSuffixPadding() : 0),
                                versionsWithBuildNums),
                        '0');
                modifiedVersion = Version.setBuildNumber(modifiedVersion, paddedBuildNum);
            }

            versionsWithBuildNums.add(modifiedVersion);

            if (logger.isDebugEnabled()) {
                logger.debug("{} has updated version: {}. Marking for rewrite.", gav(project), modifiedVersion);
            }

            if (!originalVersion.equals(modifiedVersion)) {
                versionsByGAV.put(project.getKey(), modifiedVersion);
            }
        }

        return versionsByGAV;
    }

    /**
     * Calculate the version modification for a given GAV.
     *
     * @param groupId the groupId to search for
     * @param artifactId the artifactId to search for.
     * @param version the original version to search for.
     * @param session the container session.
     * @return the new version string
     * @throws ManipulationException if an error occurs.
     */
    protected String calculate(
            final String groupId,
            final String artifactId,
            final String version,
            final ManipulationSession session)
            throws ManipulationException {
        return calculate(groupId, artifactId, version, session.getState(VersioningState.class));
    }

    /**
     * Calculate the version modification for a given GAV. Convenience method to avoid
     * creating a manipulation session.
     *
     * @param groupId the groupId to search for
     * @param artifactId the artifactId to search for.
     * @param version the original version to search for.
     * @param state the VersioningState
     * @return the new version string
     * @throws ManipulationException if an error occurs.
     */
    public String calculate(
            final String groupId,
            final String artifactId,
            final String version,
            final VersioningState state)
            throws ManipulationException {
        final String incrementalSuffix = state.getIncrementalSerialSuffix();
        final String staticSuffix = state.getSuffix();
        final String override = state.getOverride();

        if (logger.isDebugEnabled()) {
            logger.debug(
                    "Got the following original version: {} for groupId:artifactId {}:{}",
                    version,
                    groupId,
                    artifactId);
        }

        String newVersion = version;

        if (state.getSuffixAlternatives().size() > 0) {
            logger.debug("Got alternate suffixes of {}", state.getSuffixAlternatives());
            newVersion = handleAlternate(state, version);
            logger.debug("Resetting version {} to {}", version, newVersion);
        }

        if (override != null) {
            newVersion = override;
        }

        if (staticSuffix != null) {
            newVersion = Version.appendQualifierSuffix(newVersion, staticSuffix);
        } else if (incrementalSuffix != null) {
            final Set<String> versionCandidates = getVersionCandidates(state, groupId, artifactId);

            newVersion = Version.appendQualifierSuffix(newVersion, incrementalSuffix);
            int highestRemoteBuildNumPlusOne = findHighestMatchingBuildNumber(newVersion, versionCandidates) + 1;

            if (highestRemoteBuildNumPlusOne > Version.getIntegerBuildNumber(newVersion)) {
                String paddedBuildNumber = StringUtils.leftPad(
                        Integer.toString(highestRemoteBuildNumPlusOne),
                        Version.getBuildNumberPadding(state.getIncrementalSerialSuffixPadding(), versionCandidates),
                        '0');
                newVersion = Version.setBuildNumber(newVersion, paddedBuildNumber);
            }
        }
        if (!state.isPreserveSnapshot()) {
            newVersion = Version.removeSnapshot(newVersion);
        }
        if (state.isOsgi()) {
            newVersion = Version.getOsgiVersion(newVersion);
        }

        return newVersion;
    }

    /**
     * Find matching version strings in the remote repo. This might be a result of a REST call, local maven
     * repo, or a cached result file.
     *
     * @param state Current VersionState configuration
     * @param groupId to look for
     * @param artifactId to look for
     * @return the set of potential candidates
     * @throws ManipulationException if an error occurs.
     */
    protected Set<String> getVersionCandidates(VersioningState state, String groupId, String artifactId)
            throws ManipulationException {
        final Set<String> versionCandidates = new HashSet<>();

        Map<ProjectRef, Set<String>> rm = state.getRESTMetadata();
        if (rm != null) {
            // If the REST Client has prepopulated incremental data use that instead of the examining the repository.
            if (!rm.isEmpty()) {
                // Use preloaded metadata from remote repository, loaded via a REST Call.
                if (rm.get(new SimpleProjectRef(groupId, artifactId)) != null) {
                    versionCandidates.addAll(rm.get(new SimpleProjectRef(groupId, artifactId)));
                }
            }
        } else {
            // Load metadata from local repository
            versionCandidates.addAll(getMetadataVersions(groupId, artifactId));
        }
        return versionCandidates;

    }

    /**
     * Accumulate all available versions for a given GAV from all available repositories.
     *
     * @param groupId the groupId to search for
     * @param artifactId the artifactId to search for
     * @return Collection of versions for the specified group:artifact
     * @throws ManipulationException if an error occurs.
     */
    private Set<String> getMetadataVersions(final String groupId, final String artifactId)
            throws ManipulationException {
        logger.debug("Reading available versions from repository metadata for: {}:{}", groupId, artifactId);

        Set<String> versions = new HashSet<>();
        try {
            if (readerWrapper != null) {
                final MavenMetadataView metadataView = readerWrapper
                        .readMetadataView(new SimpleProjectRef(groupId, artifactId));

                versions.addAll(
                        metadataView
                                .resolveXPathToAggregatedStringList("/metadata/versioning/versions/version", true, -1));
            }
        } catch (final Exception e) {
            throw new ManipulationException("Failed to resolve metadata for: {}:{}", groupId, artifactId, e);
        }
        return versions;
    }

    /**
     * This is used by the calculator and {@link RESTCollector}. It takes a version and examines the
     * alternate suffixes. If for example we have an incremental suffix of "foo" and the alternate
     * contains "bar" then this will strip the alternate suffix from the version. That will allow
     * the alignment / interaction with DA to correctly work.
     *
     * @param state the current VersioningState
     * @param version the current version
     * @return a processed version
     */
    protected static String handleAlternate(VersioningState state, String version) {
        for (String suffix : state.getSuffixAlternatives()) {
            final String suffixStripRegExp = "(.*)([.|-])(" + suffix + "-\\d+)";
            final Pattern suffixStripPattern = Pattern.compile(suffixStripRegExp);
            final Matcher suffixMatcher = suffixStripPattern.matcher(version);

            if (suffixMatcher.matches() && !version.contains(state.getRebuildSuffix())) {
                return suffixMatcher.group(1);
            }
        }
        return version;
    }
}
