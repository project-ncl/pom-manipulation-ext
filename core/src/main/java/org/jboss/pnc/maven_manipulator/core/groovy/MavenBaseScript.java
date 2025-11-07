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
package org.jboss.pnc.maven_manipulator.core.groovy;

import java.util.List;

import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.jboss.pnc.maven_manipulator.common.model.Project;
import org.jboss.pnc.maven_manipulator.common.session.MavenSessionHandler;
import org.jboss.pnc.maven_manipulator.io.ModelIO;
import org.jboss.pnc.maven_manipulator.io.PomIO;

/**
 * Common API for developers wishing to implement groovy scripts for PME.
 */
public interface MavenBaseScript extends CommonBaseScript {
    /**
     * Obtain the GAV of the current project
     *
     * @return a {@link ProjectVersionRef}
     */
    ProjectVersionRef getGAV();

    /**
     * Return the current Project
     *
     * @return a {@link Project} instance.
     */
    Project getProject();

    /**
     * Returns the entire collection of Projects
     *
     * @return an {@link java.util.ArrayList} of {@link Project} instances.
     */
    List<Project> getProjects();

    /**
     * Get the modelIO instance for remote artifact resolving.
     *
     * @return a {@link ModelIO} reference.
     */
    ModelIO getModelIO();

    /**
     * Get the pom io instance for Project resolving.
     *
     * @return a {@link PomIO} reference.
     */
    PomIO getPomIO();

    /**
     * Get the MavenSessionHandler instance
     *
     * @return a {@link MavenSessionHandler} reference.
     */
    MavenSessionHandler getSession();
}
