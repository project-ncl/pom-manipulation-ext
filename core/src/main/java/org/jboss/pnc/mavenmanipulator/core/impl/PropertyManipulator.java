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
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Properties;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.jboss.pnc.mavenmanipulator.common.ManipulationException;
import org.jboss.pnc.mavenmanipulator.common.model.Project;
import org.jboss.pnc.mavenmanipulator.core.ManipulationSession;
import org.jboss.pnc.mavenmanipulator.core.state.PropertyState;
import org.jboss.pnc.mavenmanipulator.io.ModelIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link Manipulator} implementation that can alter property sections in a project's pom file.
 * Configuration is stored in a {@link PropertyState} instance, which is in turn stored in the
 * {@link ManipulationSession}.
 */
@Named("property-manipulator")
@Singleton
public class PropertyManipulator
        implements Manipulator {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private ModelIO effectiveModelBuilder;

    private ManipulationSession session;

    @Inject
    public PropertyManipulator(ModelIO effectiveModelBuilder) {
        this.effectiveModelBuilder = effectiveModelBuilder;
    }

    @Override
    public void init(final ManipulationSession session) {
        this.session = session;
        session.setState(new PropertyState(session.getUserProperties()));
    }

    /**
     * Apply the property changes to the list of {@link Project}'s given.
     */
    @Override
    public Set<Project> applyChanges(final List<Project> projects)
            throws ManipulationException {
        final PropertyState state = session.getState(PropertyState.class);

        if (!session.isEnabled() || !state.isEnabled()) {
            logger.debug("{}: Nothing to do!", getClass().getSimpleName());
            return Collections.emptySet();
        }

        final Properties overrides = loadRemotePOMProperties(state.getRemotePropertyMgmt());
        final Set<Project> changed = new HashSet<>();

        for (final Project project : projects) {
            if (!overrides.isEmpty()) {
                // Only inject the new properties at the top level.
                if (project.isInheritanceRoot()) {
                    logger.info(
                            "Applying property changes to: {}:{} with {}",
                            project.getGroupId(),
                            project.getArtifactId(),
                            overrides);

                    project.getModel().getProperties().putAll(overrides);

                    changed.add(project);
                } else {
                    // For any matching property that exists in the current project overwrite that value.
                    @SuppressWarnings({ "unchecked", "rawtypes" })
                    final Set<String> keyClone = new HashSet(project.getModel().getProperties().keySet());
                    keyClone.retainAll(overrides.keySet());

                    if (!keyClone.isEmpty()) {
                        final Iterator<String> keys = keyClone.iterator();
                        while (keys.hasNext()) {
                            final String matchingKey = keys.next();
                            logger.info(
                                    "Overwriting property ({} in: {}:{} with value {}",
                                    matchingKey,
                                    project.getGroupId(),
                                    project.getArtifactId(),
                                    overrides.get(matchingKey));
                            project.getModel().getProperties().put(matchingKey, overrides.get(matchingKey));

                            changed.add(project);
                        }
                    }
                }
            }
        }

        return changed;
    }

    private Properties loadRemotePOMProperties(final List<ProjectVersionRef> remoteMgmt)
            throws ManipulationException {
        final Properties overrides = new Properties();

        if (remoteMgmt == null || remoteMgmt.isEmpty()) {
            return overrides;
        }

        // Iterate in reverse order so that the first GAV in the list overwrites the last
        final ListIterator<ProjectVersionRef> listIterator = remoteMgmt.listIterator(remoteMgmt.size());
        while (listIterator.hasPrevious()) {
            final ProjectVersionRef ref = listIterator.previous();
            overrides.putAll(effectiveModelBuilder.getRemotePropertyMappingOverrides(ref));
        }

        return overrides;
    }

    @Override
    public int getExecutionIndex() {
        return 30;
    }
}
