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

import static org.apache.commons.lang3.StringUtils.isEmpty;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Profile;
import org.commonjava.atlas.maven.ident.ref.ArtifactRef;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectVersionRef;
import org.jboss.pnc.mavenmanipulator.common.ManipulationException;
import org.jboss.pnc.mavenmanipulator.common.model.Project;
import org.jboss.pnc.mavenmanipulator.common.model.SimpleScopedArtifactRef;
import org.jboss.pnc.mavenmanipulator.common.util.ProfileUtils;
import org.jboss.pnc.mavenmanipulator.common.util.WildcardMap;
import org.jboss.pnc.mavenmanipulator.core.ManipulationSession;
import org.jboss.pnc.mavenmanipulator.core.state.RelocationState;
import org.jboss.pnc.mavenmanipulator.core.state.State;
import org.jboss.pnc.mavenmanipulator.core.util.DependencyPluginUtils;
import org.jboss.pnc.mavenmanipulator.core.util.PluginReference;
import org.jboss.pnc.mavenmanipulator.io.resolver.GalleyAPIWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link Manipulator} implementation that can relocation specified groupIds. It will also handle version changes by
 * delegating to dependencyExclusions.
 * Configuration is stored in a {@link RelocationState} instance, which is in turn stored in the
 * {@link ManipulationSession}.
 */
@Named("relocations-manipulator")
@Singleton
public class RelocationManipulator
        implements Manipulator {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final GalleyAPIWrapper galleyWrapper;

    private ManipulationSession session;

    @Inject
    public RelocationManipulator(GalleyAPIWrapper galleyWrapper) {
        this.galleyWrapper = galleyWrapper;
    }

    /**
     * Initialize the {@link RelocationState} state holder in the {@link ManipulationSession}. This state holder detects
     * relocation configuration from the Maven user properties (-D properties from the CLI) and makes it available for
     * later.
     */
    @Override
    public void init(final ManipulationSession session)
            throws ManipulationException {
        this.session = session;
        session.setState(new RelocationState(session.getUserProperties()));
    }

    /**
     * Apply the relocation changes to the list of {@link Project}'s given.
     */
    @Override
    public Set<Project> applyChanges(final List<Project> projects)
            throws ManipulationException {
        final State state = session.getState(RelocationState.class);

        if (!session.isEnabled() || !state.isEnabled()) {
            logger.debug("{}: Nothing to do!", getClass().getSimpleName());
            return Collections.emptySet();
        }

        final Set<Project> changed = new HashSet<>();

        for (final Project project : projects) {
            final Model model = project.getModel();

            if (apply(project, model)) {
                changed.add(project);
            }
        }

        return changed;
    }

    private boolean apply(final Project project, final Model model) throws ManipulationException {
        boolean result = false;
        final RelocationState state = session.getState(RelocationState.class);
        final WildcardMap<ProjectVersionRef> dependencyRelocations = state.getDependencyRelocations();
        final WildcardMap<ProjectVersionRef> pluginRelocations = state.getPluginRelocations();

        logger.debug(
                "Applying relocation changes for dependencies ({}) and for plugins ({}) to: {}:{}",
                dependencyRelocations,
                pluginRelocations,
                project.getGroupId(),
                project.getArtifactId());

        DependencyManagement dependencyManagement = model.getDependencyManagement();
        if (dependencyManagement != null) {
            result = updateDependencies(
                    project,
                    dependencyRelocations,
                    project.getResolvedManagedDependencies(session));
        }
        result |= updateDependencies(project, dependencyRelocations, project.getAllResolvedDependencies(session));

        for (final Profile profile : ProfileUtils.getProfiles(session, model)) {
            dependencyManagement = profile.getDependencyManagement();
            if (dependencyManagement != null) {
                result |= updateDependencies(
                        project,
                        dependencyRelocations,
                        project.getResolvedProfileManagedDependencies(session).get(profile));
            }
            result |= updateDependencies(
                    project,
                    dependencyRelocations,
                    project.getAllResolvedProfileDependencies(session).get(profile));

        }

        result |= updatePlugins(
                pluginRelocations,
                dependencyRelocations,
                project,
                project.getResolvedManagedPlugins(session));

        result |= updatePlugins(
                pluginRelocations,
                dependencyRelocations,
                project,
                project.getAllResolvedPlugins(session));

        for (Profile profile : project.getAllResolvedProfilePlugins(session).keySet()) {
            result |= updatePlugins(
                    pluginRelocations,
                    dependencyRelocations,
                    project,
                    project.getAllResolvedProfilePlugins(session).get(profile));
        }

        for (Profile profile : project.getResolvedProfileManagedPlugins(session).keySet()) {
            result |= updatePlugins(
                    pluginRelocations,
                    dependencyRelocations,
                    project,
                    project.getResolvedProfileManagedPlugins(session).get(profile));
        }

        return result;
    }

    private boolean updateDependencies(
            Project project,
            WildcardMap<ProjectVersionRef> relocations,
            Map<ArtifactRef, Dependency> dependencies)
            throws ManipulationException {
        final Map<ArtifactRef, Dependency> postFixUp = new HashMap<>();
        boolean result = false;

        // If we do a single pass over the dependencies that will handle the relocations *but* it will not handle
        // where one relocation alters the dependency and a subsequent relocation alters it again. For instance, the
        // first might wildcard alter the groupId and the second, more specifically alters one with the artifactId
        for (int i = 0; i < relocations.size(); i++) {
            Iterator<ArtifactRef> it = dependencies.keySet().iterator();
            while (it.hasNext()) {
                final ArtifactRef pvr = it.next();
                if (relocations.containsKey(pvr.asProjectRef())) {
                    ProjectVersionRef relocation = relocations.get(pvr.asProjectRef());
                    Dependency dependency = dependencies.get(pvr);

                    logger.info(
                            "For dependency {}, replacing groupId {} by {} and artifactId {} with {}",
                            dependency,
                            dependency.getGroupId(),
                            relocation.getGroupId(),
                            dependency.getArtifactId(),
                            relocation.getArtifactId());

                    if (!relocation.getArtifactId().equals(WildcardMap.WILDCARD)) {
                        DependencyPluginUtils.updateString(
                                project,
                                session,
                                dependency.getArtifactId(),
                                relocation,
                                relocation.getArtifactId(),
                                d -> dependency.setArtifactId(relocation.getArtifactId()));
                    }
                    if (relocation.getVersionString().equals(WildcardMap.WILDCARD)) {
                        logger.debug("No version alignment to perform for relocation {}", relocation);
                    } else {
                        for (final String target : relocation.getVersionString().split(",")) {
                            if (target.startsWith("+")) {
                                dependency.addExclusion(CommonManipulator.processExclusion(logger, target, dependency));
                            } else {
                                String originalVersion = dependency.getVersion();

                                if (originalVersion != null) {
                                    DependencyPluginUtils.updateString(
                                            project,
                                            session,
                                            originalVersion,
                                            relocation,
                                            target,
                                            dependency::setVersion);
                                } else {
                                    // Do not add a version element where none was originally present.
                                    logger.debug(
                                            "For dependency {}, no version present for relocation {}",
                                            dependency,
                                            relocation);
                                }
                            }
                        }
                    }

                    DependencyPluginUtils.updateString(
                            project,
                            session,
                            dependency.getGroupId(),
                            relocation,
                            relocation.getGroupId(),
                            d -> dependency.setGroupId(relocation.getGroupId()));

                    // Unfortunately because we iterate using the resolved project keys if the relocation updates those
                    // keys multiple iterations will not work. Therefore we need to remove the original key:dependency
                    // to map to the relocated form.
                    postFixUp.put(new SimpleScopedArtifactRef(dependency), dependency);
                    it.remove();

                    result = true;
                }
            }
            dependencies.putAll(postFixUp);
            postFixUp.clear();
        }
        return result;
    }

    private boolean updatePlugins(
            WildcardMap<ProjectVersionRef> pluginRelocations,
            final WildcardMap<ProjectVersionRef> dependencyRelocations,
            final Project project,
            final Map<ProjectVersionRef, Plugin> pluginMap) throws ManipulationException {
        final Map<ProjectVersionRef, Plugin> postFixUp = new HashMap<>();
        boolean result = false;

        // Handles plugin configurations
        final List<PluginReference> refs = DependencyPluginUtils
                .findPluginReferences(galleyWrapper, project, pluginMap);
        final int size = dependencyRelocations.size();

        for (int i = 0; i < size; i++) {
            for (PluginReference pluginReference : refs) {
                final Dependency dependency = new Dependency();

                dependency.setGroupId(pluginReference.getGroupId());
                dependency.setArtifactId(pluginReference.getArtifactId());

                final ProjectVersionRef relocation = dependencyRelocations.get(dependency);

                if (relocation != null) {
                    DependencyPluginUtils.updateString(
                            project,
                            session,
                            pluginReference.getGroupId(),
                            relocation,
                            relocation.getGroupId(),
                            d -> pluginReference.groupIdNode.setTextContent(relocation.getGroupId()));

                    if (!relocation.getArtifactId().equals(WildcardMap.WILDCARD)) {
                        DependencyPluginUtils.updateString(
                                project,
                                session,
                                pluginReference.getArtifactId(),
                                relocation,
                                relocation.getArtifactId(),
                                d -> pluginReference.artifactIdNode.setTextContent(relocation.getArtifactId()));
                    }

                    if (pluginReference.versionNode != null) {
                        if (relocation.getVersionString().equals(WildcardMap.WILDCARD)) {
                            logger.debug("No version alignment to perform for relocation {}", relocation);
                        } else {
                            String originalVersion = pluginReference.versionNode.getTextContent();

                            if (originalVersion != null) {
                                DependencyPluginUtils.updateString(
                                        project,
                                        session,
                                        originalVersion,
                                        relocation,
                                        relocation.getVersionString(),
                                        pluginReference.versionNode::setTextContent);
                            } else {
                                // Do not add a version element where none was originally present.
                                logger.debug(
                                        "For plugin reference {}, no version present for relocation {}",
                                        pluginReference,
                                        relocation);
                            }
                        }
                    }
                    pluginReference.container.setConfiguration(
                            DependencyPluginUtils.getConfigXml(
                                    galleyWrapper,
                                    pluginReference.groupIdNode));

                    logger.debug("Update plugin: set {} to {}", relocation, pluginReference);

                    result = true;
                }
            }
        }

        // Handles plugins themselves
        for (int i = 0; i < pluginRelocations.size(); i++) {
            Iterator<ProjectVersionRef> it = pluginMap.keySet().iterator();
            while (it.hasNext()) {
                final ProjectVersionRef pvr = it.next();

                if (pluginRelocations.containsKey(pvr.asProjectRef())) {
                    Plugin plugin = pluginMap.get(pvr);
                    ProjectVersionRef relocation = pluginRelocations.get(pvr.asProjectRef());

                    logger.info(
                            "For plugin {}, replacing groupId {} by {} and artifactId {} with {}",
                            plugin.getId(),
                            plugin.getGroupId(),
                            relocation.getGroupId(),
                            plugin.getArtifactId(),
                            relocation.getArtifactId());

                    if (!relocation.getArtifactId().equals(WildcardMap.WILDCARD)) {
                        DependencyPluginUtils.updateString(
                                project,
                                session,
                                plugin.getArtifactId(),
                                relocation,
                                relocation.getArtifactId(),
                                d -> plugin.setArtifactId(relocation.getArtifactId()));
                    }
                    if (relocation.getVersionString().equals(WildcardMap.WILDCARD)) {
                        logger.debug("No version alignment to perform for relocation {}", relocation);
                    } else {
                        String originalVersion = plugin.getVersion();

                        if (originalVersion != null) {
                            DependencyPluginUtils.updateString(
                                    project,
                                    session,
                                    originalVersion,
                                    relocation,
                                    relocation.getVersionString(),
                                    plugin::setVersion);
                        } else {
                            // Do not add a version element where none was originally present.
                            logger.debug(
                                    "For plugin {}, no version present for relocation {}",
                                    plugin,
                                    relocation);
                        }
                    }

                    DependencyPluginUtils.updateString(
                            project,
                            session,
                            plugin.getGroupId(),
                            relocation,
                            relocation.getGroupId(),
                            d -> plugin.setGroupId(relocation.getGroupId()));

                    postFixUp.put(
                            new SimpleProjectVersionRef(
                                    plugin.getGroupId(),
                                    plugin.getArtifactId(),
                                    isEmpty(plugin.getVersion()) ? "*" : plugin.getVersion()),
                            plugin);
                    it.remove();
                    result = true;
                }
            }
            pluginMap.putAll(postFixUp);
            postFixUp.clear();
        }

        return result;
    }

    @Override
    public int getExecutionIndex() {
        return 7;
    }
}
