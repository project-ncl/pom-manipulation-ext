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

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.model.Project;
import org.commonjava.maven.ext.core.ManipulationSession;
import org.commonjava.maven.ext.core.groovy.InvocationStage;
import org.commonjava.maven.ext.core.state.GroovyState;
import org.commonjava.maven.ext.core.state.State;
import org.commonjava.maven.ext.io.FileIO;
import org.commonjava.maven.ext.io.ModelIO;
import org.commonjava.maven.ext.io.PomIO;

/**
 * {@link Manipulator} implementation that can resolve a remote groovy file and execute it on executionRoot.
 * Configuration
 * is stored in a {@link GroovyState} instance, which is in turn stored in the {@link ManipulationSession}.
 */
@Named("groovy-injection-last")
@Singleton
public class FinalGroovyManipulator
        extends BaseGroovyManipulator
        implements Manipulator {
    @Inject
    public FinalGroovyManipulator(ModelIO modelIO, FileIO fileIO, PomIO pomIO) {
        super(modelIO, fileIO, pomIO);
    }

    /**
     * Initialize the {@link GroovyState} state holder in the {@link ManipulationSession}. This state holder detects
     * version-change configuration from the Maven user properties (-D properties from the CLI) and makes it available
     * for later.
     */
    @Override
    public void init(final ManipulationSession session) throws ManipulationException {
        final State state = new GroovyState(session.getUserProperties());
        this.session = session;
        session.setState(state);
    }

    /**
     * Apply the groovy script changes to the top level pom.
     */
    @Override
    public Set<Project> applyChanges(final List<Project> projects)
            throws ManipulationException {
        final GroovyState state = session.getState(GroovyState.class);
        if (!session.isEnabled() || !state.isEnabled()) {
            logger.debug("{}: Nothing to do!", getClass().getSimpleName());
            return Collections.emptySet();
        }

        final List<File> groovyScripts = parseGroovyScripts(state.getGroovyScripts());
        final Set<Project> changed = new HashSet<>(groovyScripts.size());

        for (final File groovyScript : groovyScripts) {
            final Project project = projects.stream()
                    .filter(Project::isExecutionRoot)
                    .findFirst()
                    .orElseThrow(
                            () -> new ManipulationException(
                                    "Unable to find execution"
                                            + " root"));
            applyGroovyScript(projects, project, groovyScript);
            changed.add(project);
        }
        return changed;
    }

    @Override
    public int getExecutionIndex() {
        return InvocationStage.LAST.getStageValue();
    }
}
