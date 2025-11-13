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
package org.jboss.pnc.mavenmanipulator.ext;

import java.io.File;
import java.util.Properties;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.ExecutionEvent.Type;
import org.jboss.pnc.mavenmanipulator.common.ManipulationException;
import org.jboss.pnc.mavenmanipulator.core.ManipulationManager;
import org.jboss.pnc.mavenmanipulator.core.ManipulationSession;
import org.jboss.pnc.mavenmanipulator.core.util.PropertiesUtils;
import org.jboss.pnc.mavenmanipulator.io.ConfigIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements hooks necessary to apply modifications in the Maven bootstrap, before the build starts.
 *
 * @author jdcasey
 */
@SuppressWarnings("unused")
@Named
@Singleton
public class ManipulatingEventSpy
        extends AbstractEventSpy {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private ManipulationManager manipulationManager;

    private ManipulationSession session;

    private ConfigIO configIO;

    @Inject
    public ManipulatingEventSpy(
            ManipulationManager manipulationManager,
            ManipulationSession session,
            ConfigIO configIO) {
        this.manipulationManager = manipulationManager;
        this.session = session;
        this.configIO = configIO;
    }

    @Override
    public void onEvent(final Object event)
            throws Exception {
        boolean required = false;
        try {
            if (event instanceof ExecutionEvent) {
                final ExecutionEvent ee = (ExecutionEvent) event;
                final ExecutionEvent.Type type = ee.getType();

                if (type == Type.ProjectDiscoveryStarted) {
                    if (ee.getSession() != null) {
                        session.setMavenSession(ee.getSession());

                        if (ee.getSession().getRequest().getPom() != null) {
                            Properties config = configIO.parse(ee.getSession().getRequest().getPom().getParentFile());
                            PropertiesUtils.handleConfigPrecedence(session.getUserProperties(), config);
                        }

                        manipulationManager.init(session);
                    } else {
                        logger.error("Null session ; unable to continue");
                        return;
                    }

                    if (!session.isEnabled()) {
                        logger.info("Manipulation engine disabled via command-line option");
                        return;
                    } else if (ee.getSession().getRequest().getPom() == null) {
                        logger.info("Manipulation engine disabled. No project found.");
                        return;
                    } else if (new File(
                            ee.getSession().getRequest().getPom().getParentFile(),
                            ManipulationManager.MARKER_FILE).exists()) {
                        logger.info("Skipping manipulation as previous execution found.");
                        return;
                    }

                    manipulationManager.scanAndApply(session);
                }
            }
        } catch (final ManipulationException e) {
            logger.error("Extension failure", e);
            session.setError(e);
        }
        // Catch any runtime exceptions and mark them to fail the build as well.
        catch (final RuntimeException e) {
            logger.error("Extension failure", e);
            session.setError(new ManipulationException("Caught runtime exception", e));
        } finally {
            super.onEvent(event);
        }
    }
}
