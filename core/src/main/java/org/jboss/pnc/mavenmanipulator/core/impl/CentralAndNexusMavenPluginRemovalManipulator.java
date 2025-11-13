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

import java.util.List;
import java.util.Set;

import javax.inject.Named;
import javax.inject.Singleton;

import org.jboss.pnc.mavenmanipulator.common.model.Project;
import org.jboss.pnc.mavenmanipulator.core.ManipulationSession;
import org.jboss.pnc.mavenmanipulator.core.state.CentralAndNexusMavenPluginRemovalState;
import org.jboss.pnc.mavenmanipulator.core.state.PluginState;

/**
 * {@link Manipulator} implementation that can remove nexus-staging-maven-plugin from a project's pom file.
 * Configuration is stored in a {@link PluginState} instance, which is in turn stored in the
 * {@link ManipulationSession}.
 */
@Named("nexus-staging-maven-plugin-removal-manipulator")
@Singleton
public class CentralAndNexusMavenPluginRemovalManipulator extends BasePluginRemovalManipulator
        implements Manipulator {
    /**
     * Initialize the {@link PluginState} state holder in the {@link ManipulationSession}. This state holder detects
     * version-change configuration from the Maven user properties (-D properties from the CLI) and makes it available
     * for later.
     *
     * @param session the session
     */
    @Override
    public void init(ManipulationSession session) {
        this.session = session;
        session.setState(new CentralAndNexusMavenPluginRemovalState(session.getUserProperties()));
    }

    /**
     * Apply the alignment changes to the list of {@link Project}s given.
     *
     * @param projects the projects to apply changes to
     */
    @Override
    public Set<Project> applyChanges(final List<Project> projects) {
        return applyChanges(projects, session.getState(CentralAndNexusMavenPluginRemovalState.class));
    }

    /**
     * Determines the order in which manipulators are run, with the lowest number running first.
     * Uses a 100-point scale.
     *
     * @return one greater than current index for {@code PluginRemovalManipulator}
     */
    @Override
    public int getExecutionIndex() {
        return 53;
    }
}
