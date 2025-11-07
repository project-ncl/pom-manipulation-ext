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
package org.jboss.pnc.maven_manipulator.core.impl;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Named;
import javax.inject.Singleton;

import org.jboss.pnc.maven_manipulator.common.model.Project;
import org.jboss.pnc.maven_manipulator.core.ManipulationSession;
import org.jboss.pnc.maven_manipulator.core.state.ParentInjectionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link Manipulator} implementation that inject a specified parent into the project's top level pom file.
 * Configuration is stored in a {@link ParentInjectionState} instance, which is in turn stored in the
 * {@link ManipulationSession}.
 */
@Named("parent-injection")
@Singleton
public class ParentInjectionManipulator
        implements Manipulator {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private ManipulationSession session;

    /**
     * Initialize the {@link ParentInjectionState} state holder in the {@link ManipulationSession}. This state holder
     * detects
     * version-change configuration from the Maven user properties (-D properties from the CLI) and makes it available
     * for
     * later.
     */
    @Override
    public void init(final ManipulationSession session) {
        this.session = session;
        session.setState(new ParentInjectionState(session.getUserProperties()));
    }

    /**
     * Apply the parent injection changes to the the top level pom.
     */
    @Override
    public Set<Project> applyChanges(final List<Project> projects) {
        final ParentInjectionState state = session.getState(ParentInjectionState.class);
        if (!session.isEnabled() || !state.isEnabled()) {
            logger.debug("{}: Nothing to do!", getClass().getSimpleName());
            return Collections.emptySet();
        }

        final Set<Project> changed = new HashSet<>();

        projects.stream().filter(Project::isInheritanceRoot).forEach(p -> {
            logger.debug("Injecting parent reference {}", state.getParentInjection());
            p.getModel().setParent(state.getParentInjection());
            changed.add(p);
        });

        return changed;
    }

    @Override
    public int getExecutionIndex() {
        return 25;
    }
}
