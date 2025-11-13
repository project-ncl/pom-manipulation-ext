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

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jboss.pnc.mavenmanipulator.common.ManipulationException;
import org.jboss.pnc.mavenmanipulator.common.model.Project;
import org.jboss.pnc.mavenmanipulator.core.ManipulationSession;
import org.jboss.pnc.mavenmanipulator.core.state.DependencyInjectionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link Manipulator} implementation that can inject specified dependencies into a project's top
 * level pom file in a {@code dependencyManagement} block.
 *
 * Configuration is stored in a {@link DependencyInjectionState} instance, which is in turn stored
 * in the {@link ManipulationSession}. See {@link DependencyInjectionState#DEPENDENCY_INJECTION_PROPERTY}
 * for details on the supported dependency specification formats.
 */
@Named("dependency-injection-manipulator")
@Singleton
public class DependencyInjectionManipulator
        implements Manipulator {
    private static final String UNUSED_DECLARED = "ignoredUnusedDeclaredDependencies";
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private ManipulationSession session;

    /**
     * Initialize the {@link DependencyInjectionState} state holder in the {@link ManipulationSession}. This state
     * holder
     * detects
     * version-change configuration from the Maven user properties (-D properties from the CLI) and makes it available
     * for
     * later.
     */
    @Override
    public void init(final ManipulationSession session) {
        session.setState(new DependencyInjectionState(session.getUserProperties()));
        this.session = session;
    }

    /**
     * Apply the alignment changes to the list of {@link Project}'s given.
     */
    @Override
    public Set<Project> applyChanges(final List<Project> projects)
            throws ManipulationException {
        final DependencyInjectionState state = session.getState(DependencyInjectionState.class);

        if (!session.isEnabled() || !state.isEnabled()) {
            logger.debug("{}: Nothing to do!", getClass().getSimpleName());
            return Collections.emptySet();
        }

        final Set<Project> changed = new HashSet<>();

        projects.stream().filter(Project::isInheritanceRoot).forEach(project -> {
            logger.info("Applying injection changes to {}", project);

            DependencyManagement dependencyManagement = project.getModel().getDependencyManagement();
            List<Dependency> dependencies = state.getDependencyInjection();
            if (dependencyManagement == null) {
                dependencyManagement = new DependencyManagement();
                project.getModel().setDependencyManagement(dependencyManagement);
                logger.debug("Added <DependencyManagement/> for current project");
            } else {
                for (Dependency d : dependencies) {
                    dependencyManagement.getDependencies()
                            .removeIf(
                                    existing -> existing.getGroupId().equals(d.getGroupId()) && existing.getArtifactId()
                                            .equals(d.getArtifactId()));
                }
            }
            dependencyManagement.getDependencies().addAll(0, dependencies);

            if (state.isAddIgnoreUnusedAnalzyePlugin()) {
                Optional<Plugin> mdpPlugin = project.getModel()
                        .getBuild()
                        .getPlugins()
                        .stream()
                        .filter(p -> p.getArtifactId().equals("maven-dependency-plugin"))
                        .findFirst();

                if (mdpPlugin.isPresent()) {
                    logger.info(
                            "Found plugin {} to add ignoredUnusedDeclaredDependency for {}",
                            mdpPlugin,
                            state.getDependencyInjection());
                    PluginExecution execution = mdpPlugin.get().getExecutionsAsMap().get("analyze");
                    if (execution != null) {
                        Xpp3Dom originalConfiguration = (Xpp3Dom) execution.getConfiguration();
                        for (Dependency dep : state.getDependencyInjection()) {
                            Xpp3Dom ignoreToAdd = new Xpp3Dom("ignoredUnusedDeclaredDependency");
                            ignoreToAdd.setValue(dep.getGroupId() + ":" + dep.getArtifactId());
                            Xpp3Dom ignoredUnusedDeclaredDeps;

                            if (originalConfiguration == null) {
                                originalConfiguration = new Xpp3Dom("configuration");
                                execution.setConfiguration(originalConfiguration);
                            }
                            if (originalConfiguration.getChild(UNUSED_DECLARED) == null) {
                                ignoredUnusedDeclaredDeps = new Xpp3Dom(UNUSED_DECLARED);
                                originalConfiguration.addChild(ignoredUnusedDeclaredDeps);
                            } else {
                                ignoredUnusedDeclaredDeps = originalConfiguration.getChild(UNUSED_DECLARED);
                            }
                            ignoredUnusedDeclaredDeps.addChild(ignoreToAdd);
                        }
                        logger.debug(
                                "ignoreUnusedDeclaredDependencies is now {}",
                                originalConfiguration.getChild(UNUSED_DECLARED));
                    }
                }
            }
            changed.add(project);
        });

        return changed;
    }

    @Override
    public int getExecutionIndex() {
        return 8;
    }
}
