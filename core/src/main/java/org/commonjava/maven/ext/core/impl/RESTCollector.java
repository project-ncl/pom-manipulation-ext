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
package org.commonjava.maven.ext.core.impl;

import static org.apache.commons.lang3.StringUtils.isEmpty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Profile;
import org.commonjava.atlas.maven.ident.ref.ArtifactRef;
import org.commonjava.atlas.maven.ident.ref.ProjectRef;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectVersionRef;
import org.commonjava.atlas.maven.ident.ref.SimpleTypeAndClassifier;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.model.Project;
import org.commonjava.maven.ext.common.model.SimpleScopedArtifactRef;
import org.commonjava.maven.ext.core.ManipulationSession;
import org.commonjava.maven.ext.core.state.DependencyState;
import org.commonjava.maven.ext.core.state.PluginState;
import org.commonjava.maven.ext.core.state.RESTState;
import org.commonjava.maven.ext.core.state.VersioningState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This Manipulator runs very early. It makes a REST call to an external service to loadRemoteOverrides the GAVs to
 * align the project version
 * and dependencies to. It will prepopulate Project GA versions into the VersioningState in case the
 * VersioningManipulator has been activated
 * and the remote overrides into the DependencyState for those as well.
 */
@Named("rest-manipulator")
@Singleton
public class RESTCollector
        implements Manipulator {
    private static final Logger logger = LoggerFactory.getLogger(RESTCollector.class);

    private ManipulationSession session;

    @Override
    public void init(final ManipulationSession session) {
        this.session = session;
        session.setState(new RESTState(session));
    }

    /**
     * Prescans the Project to build up a list of Project GAs and also the various Dependencies.
     */
    private void collect(final List<Project> projects)
            throws ManipulationException {
        final RESTState state = session.getState(RESTState.class);
        final VersioningState vs = session.getState(VersioningState.class);
        final DependencyState ds = session.getState(DependencyState.class);
        final PluginState ps = session.getState(PluginState.class);

        if (!session.isEnabled() || !state.isEnabled()) {
            logger.debug("{}: Nothing to do!", getClass().getSimpleName());
            return;
        }

        final List<ProjectVersionRef> restLookupProjectVersionParamList = new ArrayList<>();
        final String override = vs.getOverride();

        for (final Project project : projects) {
            if (isEmpty(override)) {
                // Strip SNAPSHOT and handle OSGi and alternate suffixes from the version for matching.
                restLookupProjectVersionParamList.add(
                        new SimpleProjectVersionRef(
                                project.getKey().asProjectRef(),
                                handlePotentialSnapshotVersion(
                                        vs,
                                        Version.getOsgiVersion(
                                                VersionCalculator.handleAlternate(vs, project.getVersion())))));
            } else if (project.isExecutionRoot()) {
                // We want to manually override the version ; therefore ignore what is in the project and calculate potential
                // matches for that instead.
                Project p = projects.get(0);
                restLookupProjectVersionParamList
                        .add(new SimpleProjectVersionRef(p.getGroupId(), p.getArtifactId(), override));
            }
        }

        final List<ProjectVersionRef> restLookupVersionsParamList = new ArrayList<>();
        final Set<ArtifactRef> localDeps = establishAllDependencies(session, projects, null);

        // Ok we now have a defined list of top level project plus a unique list of all possible dependencies.
        // Need to send that to the rest interface to get a translation.
        for (ArtifactRef p : localDeps) {
            restLookupVersionsParamList.add(p.asProjectVersionRef());
        }

        Map<ProjectVersionRef, String> vRestResult;
        // Call the REST to populate the result if dependency manipulation is enabled. Can't use ds.isEnabled as this
        // code partly establishes whether it is enabled.
        if (ds.getPrecedence() != DependencyState.DependencyPrecedence.NONE) {
            logger.debug(
                    "Passing {} GAVs into the REST client api {}",
                    restLookupVersionsParamList.size(),
                    restLookupVersionsParamList);
            vRestResult = state.getVersionTranslator().lookupVersions(restLookupVersionsParamList);
            logger.info("REST Client returned: {}", vRestResult);
        } else {
            vRestResult = Collections.emptyMap();
        }
        logger.debug(
                "Passing {} Project GAVs into the REST client api {}",
                restLookupProjectVersionParamList.size(),
                restLookupProjectVersionParamList);
        Map<ProjectVersionRef, String> pvResultResult = state.getVersionTranslator()
                .lookupProjectVersions(restLookupProjectVersionParamList);
        logger.info("REST Client returned for project versions: {}", pvResultResult);

        Map<ProjectRef, Set<String>> versionStates = new HashMap<>();
        pvResultResult.forEach((key, value) -> {
            Set<String> versions = versionStates.computeIfAbsent(key.asProjectRef(), k -> new HashSet<>());
            versions.add(value);
        });
        vs.setRESTMetadata(versionStates);

        final Map<ArtifactRef, String> overrides = new HashMap<>();

        // Convert the loaded remote ProjectVersionRefs to the original ArtifactRefs
        for (ArtifactRef a : localDeps) {
            ProjectVersionRef pvr = a.asProjectVersionRef();
            if (vRestResult.containsKey(pvr)) {
                overrides.put(a, vRestResult.get(pvr));
            }
            // If a dependency is a project version also check those results as well
            if (pvResultResult.containsKey(pvr)) {
                overrides.put(a, pvResultResult.get(pvr));
            }
        }
        logger.debug("Setting REST Overrides {}", overrides);
        ds.setRemoteRESTOverrides(overrides);
        // Unfortunately as everything is just GAVs we have to send everything to the PluginManipulator as well.
        ps.setRemoteRESTOverrides(overrides);
    }

    /**
     * No-op in this case - any changes, if configured, would happen in Versioning or Dependency Manipulators.
     */
    @Override
    public Set<Project> applyChanges(final List<Project> projects)
            throws ManipulationException {
        collect(projects);

        return Collections.emptySet();
    }

    @Override
    public int getExecutionIndex() {
        // Low value index so it runs very early in order to call the REST API.
        return 10;
    }

    /**
     * Scans a list of projects and accumulates all dependencies and returns them.
     *
     * @param session the ManipulationSession
     * @param projects the projects to scan.
     * @param activeProfiles which profiles to check
     * @return an unsorted set of ArtifactRefs used.
     * @throws ManipulationException if an error occurs
     */
    public static Set<ArtifactRef> establishAllDependencies(
            ManipulationSession session,
            final List<Project> projects,
            Set<String> activeProfiles) throws ManipulationException {
        final VersioningState vs = session.getState(VersioningState.class);

        Set<ArtifactRef> localDeps = new HashSet<>();
        Set<String> activeModules = new HashSet<>();
        boolean scanAll = false;

        if (activeProfiles != null && !activeProfiles.isEmpty()) {
            for (final Project project : projects) {
                if (project.isInheritanceRoot()) {
                    activeModules.addAll(project.getModel().getModules());

                    List<Profile> profiles = project.getModel().getProfiles();

                    if (profiles != null) {
                        for (Profile p : profiles) {
                            if (activeProfiles.contains(p.getId())) {
                                logger.debug("Adding modules for profile {}", p.getId());
                                activeModules.addAll(p.getModules());
                            }
                        }
                    }
                }
            }
            logger.debug("Found {} active modules with {} active profiles.", activeModules, activeProfiles);
        } else {
            scanAll = true;
        }

        // Iterate over current project set and populate list of dependencies
        for (final Project project : projects) {
            if (project.isInheritanceRoot() || scanAll
                    || activeModules.contains(project.getPom().getParentFile().getName())) {
                if (project.getModelParent() != null) {
                    SimpleProjectVersionRef parent = new SimpleProjectVersionRef(
                            project.getModelParent().getGroupId(),
                            project.getModelParent().getArtifactId(),
                            handlePotentialSnapshotVersion(vs, project.getModelParent().getVersion()));
                    localDeps.add(new SimpleScopedArtifactRef(parent, new SimpleTypeAndClassifier("pom", null), null));
                }

                recordDependencies(session, localDeps, project.getResolvedManagedDependencies(session));
                recordDependencies(session, localDeps, project.getResolvedDependencies(session));
                recordPlugins(session, localDeps, project.getResolvedManagedPlugins(session));
                recordPlugins(session, localDeps, project.getResolvedPlugins(session));

                List<Profile> profiles = project.getModel().getProfiles();
                if (profiles != null) {
                    for (Profile p : profiles) {
                        if (!scanAll && !activeProfiles.contains(p.getId())) {
                            continue;
                        }
                        recordDependencies(
                                session,
                                localDeps,
                                project.getResolvedProfileManagedDependencies(session)
                                        .getOrDefault(p, Collections.emptyMap()));
                        recordDependencies(
                                session,
                                localDeps,
                                project.getResolvedProfileDependencies(session)
                                        .getOrDefault(p, Collections.emptyMap()));
                        recordPlugins(
                                session,
                                localDeps,
                                project.getResolvedProfileManagedPlugins(session)
                                        .getOrDefault(p, Collections.emptyMap()));
                        recordPlugins(
                                session,
                                localDeps,
                                project.getResolvedProfilePlugins(session).getOrDefault(p, Collections.emptyMap()));

                    }
                }
            }
        }
        return localDeps;
    }

    /**
     * Translate a given set of pvr:plugins into ArtifactRefs.
     * 
     * @param session the current Session.
     * @param deps the list of ArtifactRefs to return
     * @param plugins the plugins to transform.
     */
    private static void recordPlugins(
            ManipulationSession session,
            Set<ArtifactRef> deps,
            Map<ProjectVersionRef, Plugin> plugins) {
        final VersioningState vs = session.getState(VersioningState.class);

        for (ProjectVersionRef pvr : plugins.keySet()) {
            deps.add(
                    new SimpleScopedArtifactRef(
                            new SimpleProjectVersionRef(
                                    pvr.asProjectRef(),
                                    handlePotentialSnapshotVersion(vs, pvr.getVersionString())),
                            new SimpleTypeAndClassifier("maven-plugin", null),
                            null));
        }
    }

    /**
     * Translate a given set of pvr:dependencies into ArtifactRefs.
     * 
     * @param session the ManipulationSession
     * @param deps Set of ArtifactRef to store the results in.
     * @param dependencies dependencies to examine
     */
    private static void recordDependencies(
            ManipulationSession session,
            Set<ArtifactRef> deps,
            Map<ArtifactRef, Dependency> dependencies) {
        final VersioningState vs = session.getState(VersioningState.class);
        final RESTState state = session.getState(RESTState.class);

        for (final Entry<ArtifactRef, Dependency> entry : dependencies.entrySet()) {
            final ArtifactRef pvr = entry.getKey();
            final Dependency d = entry.getValue();
            final SimpleScopedArtifactRef sa = new SimpleScopedArtifactRef(
                    new SimpleProjectVersionRef(
                            pvr.asProjectRef(),
                            handlePotentialSnapshotVersion(vs, pvr.getVersionString())),
                    new SimpleTypeAndClassifier(d.getType(), d.getClassifier()),
                    d.getScope());

            boolean validate = true;

            // Don't bother adding an artifact with a property that couldn't be resolved.
            if (sa.getVersionString().contains("$")) {
                validate = false;
            }
            // If we are not (re)aligning suffixed dependencies then ignore them.
            // Null check to avoid problems with some tests where state is not instantiated.
            else if (state != null && !state.isRestSuffixAlign() &&
                    (sa.getVersionString().contains(vs.getRebuildSuffix()) ||
                            vs.getSuffixAlternatives().stream().anyMatch(s -> sa.getVersionString().contains(s)))) {
                validate = false;
            }
            if (validate) {
                deps.add(sa);
            }
        }
    }

    private static String handlePotentialSnapshotVersion(VersioningState vs, String version) {
        if (vs != null && !vs.isPreserveSnapshot()) {
            return Version.removeSnapshot(version);
        }
        return version;
    }
}
