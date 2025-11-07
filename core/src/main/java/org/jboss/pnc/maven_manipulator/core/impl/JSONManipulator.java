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

import static org.apache.commons.lang3.StringUtils.isEmpty;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.jboss.pnc.maven_manipulator.common.ManipulationException;
import org.jboss.pnc.maven_manipulator.common.model.Project;
import org.jboss.pnc.maven_manipulator.core.ManipulationSession;
import org.jboss.pnc.maven_manipulator.core.state.JSONState;
import org.jboss.pnc.maven_manipulator.io.JSONIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPathException;

/**
 * {@link Manipulator} implementation that can modify JSON files. Configuration
 * is stored in a {@link JSONState} instance, which is in turn stored in the {@link ManipulationSession}.
 */
@Named("json-manipulator")
@Singleton
public class JSONManipulator
        implements Manipulator {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JSONIO jsonIO;

    private ManipulationSession session;

    @Inject
    public JSONManipulator(JSONIO jsonIO) {
        this.jsonIO = jsonIO;
    }

    /**
     * Initialize the {@link JSONState} state holder in the {@link ManipulationSession}. This state holder detects
     * configuration from the Maven user properties (-D properties from the CLI) and makes it available for
     * later.
     */
    @Override
    public void init(final ManipulationSession session)
            throws ManipulationException {
        this.session = session;
        session.setState(new JSONState(session.getUserProperties()));
    }

    /**
     * Apply the json changes to the specified file(s).
     */
    @Override
    public Set<Project> applyChanges(final List<Project> projects)
            throws ManipulationException {
        final JSONState state = session.getState(JSONState.class);
        if (!session.isEnabled() || !state.isEnabled()) {
            logger.debug("{}: Nothing to do!", getClass().getSimpleName());
            return Collections.emptySet();
        }

        final Set<Project> changed = new HashSet<>();
        final List<JSONState.JSONOperation> scripts = state.getJSONOperations();

        for (final Project project : projects) {
            if (project.isExecutionRoot()) {
                for (JSONState.JSONOperation operation : scripts) {
                    internalApplyChanges(project, operation);

                    changed.add(project);
                }

                break;
            }
        }
        return changed;
    }

    // Package accessible so tests can use it.
    void internalApplyChanges(Project project, JSONState.JSONOperation operation) throws ManipulationException {
        File target = new File(project.getPom().getParentFile(), operation.getFile());

        logger.info(
                "Attempting to start JSON update to file {} with xpath {} and replacement '{}' ",
                target,
                operation.getXPath(),
                operation.getUpdate());

        DocumentContext dc = null;
        try {
            if (!target.exists()) {
                logger.error("Unable to locate JSON file {}", target);
                throw new ManipulationException("Unable to locate JSON file {}", target);
            }

            dc = jsonIO.parseJSON(target);

            List<?> o = dc.read(operation.getXPath());
            if (o.size() == 0) {
                if (project.isIncrementalPME()) {
                    logger.warn("Did not locate JSON using XPath {}", operation.getXPath());
                    return;
                } else {
                    logger.error(
                            "XPath {} did not find any expressions within {}",
                            operation.getXPath(),
                            operation.getFile());
                    throw new ManipulationException("XPath did not resolve to a valid value");
                }
            }

            if (isEmpty(operation.getUpdate())) {
                // Delete
                logger.info("Deleting {} on {}", operation.getXPath(), dc);
                dc.delete(operation.getXPath());
            } else {
                // Update
                logger.info("Updating {} on {}", operation.getXPath(), dc);
                dc.set(operation.getXPath(), operation.getUpdate());
            }

            jsonIO.writeJSON(target, dc);
        } catch (JsonPathException e) {
            logger.error("Caught JSON exception processing file {}, document context {}", target, dc, e);
            throw new ManipulationException("Caught JsonPath", e);
        }
    }

    @Override
    public int getExecutionIndex() {
        return 90;
    }
}
