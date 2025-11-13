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
package org.jboss.pnc.mavenmanipulator.core.groovy;

import java.io.File;
import java.util.Properties;

import org.jboss.pnc.mavenmanipulator.common.ManipulationException;
import org.jboss.pnc.mavenmanipulator.io.FileIO;
import org.jboss.pnc.mavenmanipulator.io.rest.Translator;

/**
 * Common API for developers implementing Groovy scripts for PME or GME.
 */
public interface CommonBaseScript {
    /**
     * Get the working directory (the execution root).
     *
     * @return a {@link File} reference.
     */
    File getBaseDir();

    /**
     * Get the user properties
     *
     * @return a {@link Properties} reference.
     */
    Properties getUserProperties();

    /**
     * Get the current stage
     *
     * @return a {@link InvocationStage} reference.
     */
    InvocationStage getInvocationStage();

    /**
     * Return a FileIO instance to read a raw file from a given URL
     *
     * @return a {@link FileIO} reference.
     */
    FileIO getFileIO();

    /**
     * Gets a configured VersionTranslator to make REST calls to DA
     *
     * @throws ManipulationException if an error occurs
     * @return a VersionTranslator
     */
    Translator getRESTAPI() throws ManipulationException;
}
