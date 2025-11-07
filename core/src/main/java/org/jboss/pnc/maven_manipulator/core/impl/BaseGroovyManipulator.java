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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.codehaus.groovy.control.CompilationFailedException;
import org.commonjava.atlas.maven.ident.ref.ArtifactRef;
import org.commonjava.atlas.maven.ident.ref.SimpleArtifactRef;
import org.jboss.pnc.maven_manipulator.common.ManipulationException;
import org.jboss.pnc.maven_manipulator.common.model.Project;
import org.jboss.pnc.maven_manipulator.common.model.SimpleScopedArtifactRef;
import org.jboss.pnc.maven_manipulator.core.ManipulationSession;
import org.jboss.pnc.maven_manipulator.core.groovy.BaseScript;
import org.jboss.pnc.maven_manipulator.core.groovy.InvocationPoint;
import org.jboss.pnc.maven_manipulator.core.groovy.InvocationStage;
import org.jboss.pnc.maven_manipulator.core.state.GroovyState;
import org.jboss.pnc.maven_manipulator.io.FileIO;
import org.jboss.pnc.maven_manipulator.io.ModelIO;
import org.jboss.pnc.maven_manipulator.io.PomIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import groovy.lang.GroovyShell;
import groovy.lang.MissingMethodException;
import groovy.lang.Script;

/**
 * {@link Manipulator} implementation that can resolve a remote groovy file and execute it on executionRoot.
 * Configuration is stored in a {@link GroovyState} instance, which is in turn stored in the {@link
 * ManipulationSession}.
 */
public abstract class BaseGroovyManipulator {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @SuppressWarnings("WeakerAccess")
    protected ModelIO modelIO;

    @SuppressWarnings("WeakerAccess")
    protected FileIO fileIO;

    @SuppressWarnings("WeakerAccess")
    protected PomIO pomIO;

    protected ManipulationSession session;

    BaseGroovyManipulator(ModelIO modelIO, FileIO fileIO, PomIO pomIO) {
        this.modelIO = modelIO;
        this.fileIO = fileIO;
        this.pomIO = pomIO;
    }

    public abstract int getExecutionIndex();

    /**
     * Splits the value on ',', then wraps each value in {@link SimpleArtifactRef#parse(String)} and prints a
     * warning/skips in the event of a parsing error. Returns null if the input value is null.
     *
     * @param value a comma separated list of GAVTC to parse
     * @return a collection of parsed ArtifactRef.
     * @throws ManipulationException if an error occurs.
     */
    List<File> parseGroovyScripts(final String value) throws ManipulationException {
        if (isEmpty(value)) {
            return Collections.emptyList();
        } else {
            final String[] scripts = value.split(",");
            final List<File> result = new ArrayList<>(scripts.length);

            logger.debug("Processing groovy scripts {}", value);
            try {
                for (final String script : scripts) {
                    File found;
                    if (script.startsWith("http") || script.startsWith("file")) {
                        logger.info("Attempting to read URL {}", script);
                        found = fileIO.resolveURL(script);
                    } else {
                        final ArtifactRef ar = SimpleScopedArtifactRef.parse(script);
                        logger.info(
                                "Attempting to read GAV {} with classifier {} and type {}",
                                ar.asProjectVersionRef(),
                                ar.getClassifier(),
                                ar.getType());
                        found = modelIO.resolveRawFile(ar);
                    }
                    result.add(found);
                }
            } catch (IOException e) {
                throw new ManipulationException("Unable to parse groovyScripts", e);
            }
            return result;
        }
    }

    void applyGroovyScript(List<Project> projects, Project project, File groovyScript) throws ManipulationException {
        final GroovyShell shell = new GroovyShell();
        final Script script;
        final InvocationStage stage;

        try {
            script = shell.parse(groovyScript);

            InvocationPoint invocationPoint = script.getClass().getAnnotation(InvocationPoint.class);
            if (invocationPoint != null) {
                logger.debug("InvocationPoint is {}", invocationPoint.invocationPoint());
                stage = invocationPoint.invocationPoint();
            } else {
                stage = null;
            }
            if (stage == null) {
                throw new ManipulationException(
                        "Mandatory annotation '@InvocationPoint(invocationPoint = ' not declared");
            }
            if (script instanceof BaseScript) {
                InvocationStage currentStage;

                if (stage == InvocationStage.ALL) {
                    currentStage = InvocationStage.valueOf(getExecutionIndex());
                } else {
                    currentStage = stage;
                }

                ((BaseScript) script).setValues(pomIO, fileIO, modelIO, session, projects, project, currentStage);
            } else {
                throw new ManipulationException("Cannot cast {} to a BaseScript to set values", groovyScript);
            }
        } catch (MissingMethodException e) {
            try {
                logger.error(
                        "Failure when injecting into script {}",
                        FileUtils.readFileToString(groovyScript, StandardCharsets.UTF_8),
                        e);
            } catch (IOException e1) {
                logger.error("Unable to read script file {} for debugging!", groovyScript, e1);
            }
            throw new ManipulationException("Unable to inject values into base script", e);
        } catch (CompilationFailedException e) {
            try {
                logger.error(
                        "Failure when parsing script {}",
                        FileUtils.readFileToString(groovyScript, StandardCharsets.UTF_8),
                        e);
            } catch (IOException e1) {
                logger.error("Unable to read script file {} for debugging!", groovyScript, e1);
            }
            throw new ManipulationException("Unable to parse script", e);
        } catch (IOException e) {
            throw new ManipulationException("Unable to parse script", e);
        }

        if (getExecutionIndex() == stage.getStageValue() || stage == InvocationStage.ALL) {
            try {
                logger.info("Executing {} on {} at invocation point {}", groovyScript, project, stage);

                script.run();

                logger.info("Completed {}", groovyScript);
            } catch (Exception e) {
                //noinspection ConstantConditions
                if (e instanceof ManipulationException) {
                    throw (ManipulationException) e;
                } else {
                    throw new ManipulationException("Problem running script", e);
                }
            }
        } else {
            logger.debug(
                    "Ignoring script {} as invocation point {} does not match index {}",
                    groovyScript,
                    stage,
                    getExecutionIndex());
        }
    }
}
