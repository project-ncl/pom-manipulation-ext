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
package org.commonjava.maven.ext.integrationtest;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.commonjava.maven.ext.cli.Cli;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.integrationtest.invoker.DefaultExecutionParser;
import org.commonjava.maven.ext.integrationtest.invoker.Execution;
import org.commonjava.maven.ext.integrationtest.invoker.ExecutionParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import groovy.lang.Binding;
import groovy.util.GroovyScriptEngine;

/**
 * @author vdedik@redhat.com
 */
public class ITestUtils {
    private static final Logger logger = LoggerFactory.getLogger(ITestUtils.class);

    private static final String BUILD_DIR = System.getProperty("buildDirectory");

    private static final String MVN_LOCATION = System.getProperty("maven.home");

    private static final String LOCAL_REPO = System.getProperty("localRepositoryPath");

    static final File IT_LOCATION = new File(BUILD_DIR, "it-cli");

    static final Map<String, String> DEFAULT_MVN_PARAMS = new HashMap<String, String>() {
        {
            put("maven.repo.local", LOCAL_REPO);
        }
    };

    static final List<String> EXCLUDED_FILES = new ArrayList<String>() {
        {
            add("setup");
            if (IT_LOCATION.exists()) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(
                        IT_LOCATION.toPath(),
                        p -> p.getFileName().toString().startsWith("rest-") ||
                                p.getFileName().toString().startsWith("circular-"))) {
                    // Run in a separate test so a Mock server may be started.
                    stream.forEach(p -> add(p.getFileName().toString()));
                } catch (IOException e) {
                    logger.error("Unable to process excluded files", e);
                    throw new RuntimeException("Unable to process excluded files", e);
                }
            }
            add("groovy-manipulator-first-http");
            add("pom-extension");
        }
    };

    static final Map<String, String> LOCATION_REWRITE = new HashMap<String, String>() {
        {
            put("simple-numeric-directory-path", "simple-numeric-directory-path" + File.separator + "parent");
        }
    };

    /**
     * Run the same/similar execution to what invoker plugin would run.
     *
     * @param workingDir - Working directory where the invoker properties are.
     * @param url The URL to either the REST server that manages versions, or HTTP server for static Groovy scripts or
     *        null
     * @throws Exception if an error occurs
     */
    static void runLikeInvoker(String workingDir, String url)
            throws Exception {
        ExecutionParser executionParser = new DefaultExecutionParser();
        Collection<Execution> executions = executionParser.parse(workingDir);

        // Execute
        for (Execution e : executions) {
            logger.debug("Running execution on location {} with command {}", e.getLocation(), e.getMvnCommand());
            if (e.isSkip()) {
                logger.debug("Skipping test {}", e.getLocation());
                continue;
            }
            // Setup
            runScript(workingDir, "setup");

            if (url != null) {
                logger.info("Resetting URL to: {}", url);
                if (url.matches(".*[0-9]+$")) {
                    e.getJavaParams().put("restURL", url);
                } else {
                    e.getJavaParams().put("groovyScripts", url);
                }
            }

            List<String> args = new ArrayList<>();
            args.add("-s");
            args.add(getDefaultTestLocation("settings.xml"));
            args.add("-d");
            if (e.getFlags() != null) {
                args.add(toFlagsParams(e.getFlags()));
            }
            // Run PME-Cli
            int cliExitValue = 0;
            if (!StringUtils.contains(e.getMvnCommand(), "manipulation.disable=true")) {
                cliExitValue = runCli(args, e.getJavaParams(), e.getLocation());
            } else {
                logger.info("Skipping running the CLI as Maven goals have the Manipulator disabled.");
            }
            // Run Maven
            Map<String, String> mavenParams = new HashMap<>();
            mavenParams.putAll(DEFAULT_MVN_PARAMS);
            mavenParams.putAll(e.getJavaParams());
            int mavenExitValue = runMaven(e.getMvnCommand(), mavenParams, e.getLocation());

            // Test return codes
            if (e.isSuccess()) {
                if (cliExitValue != 0) {
                    throw new ManipulationException(
                            "PME-Cli (running in: {}) exited with a non zero value : {}",
                            workingDir,
                            cliExitValue);
                }
                if (mavenExitValue != 0) {
                    throw new ManipulationException(
                            "Maven (running in: {}) exited with a non zero value : {}",
                            workingDir,
                            mavenExitValue);
                }
            } else {
                if (cliExitValue == 0 && mavenExitValue == 0) {
                    throw new ManipulationException(
                            "Exit value of either PME-Cli ({}) or Maven ({}) (running in: {}) must be non-zero.",
                            cliExitValue,
                            mavenExitValue,
                            workingDir);
                }
            }
        }

        if (executions.stream().noneMatch(Execution::isSkip)) {
            // Verify
            runScript(workingDir, "verify");
        }
    }

    /**
     * Run pom-manipulation-cli via run() with java params (-D arguments) in workingDir directory.
     *
     * @param args - List of additional command line arguments
     * @param params - Map of String keys and String values representing -D arguments
     * @param workingDir - Working directory in which you want the cli to be run.
     * @return Exit value
     */
    static Integer runCli(List<String> args, Map<String, String> params, String workingDir) {
        ArrayList<String> arguments = new ArrayList<>(args);
        if (!params.isEmpty()) {
            Collections.addAll(arguments, toJavaParams(params).split("\\s+"));
        }
        arguments.add("--log=" + workingDir + File.separator + "build.log");

        if (args.stream().noneMatch(s -> s.startsWith("--file"))) {
            arguments.add("--file=" + workingDir + File.separator + "pom.xml");
        }
        logger.info("Invoking CLI with {}", arguments);
        int result = new Cli().run(arguments.toArray(new String[0]));

        // This is a bit of a hack. The CLI, if log-to-file is enabled resets the logging. As we don't fork and run
        // in the same process this means we need to reset it back again. The benefit of not forking is a simpler test
        // harness and it saves time when running the tests.
        final ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory
                .getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

        root.detachAndStopAllAppenders();

        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

        PatternLayoutEncoder ple = new PatternLayoutEncoder();
        ple.setPattern("[%t] %level %logger{32} - %msg%n");
        ple.setContext(loggerContext);
        ple.start();

        ConsoleAppender<ILoggingEvent> consoleAppender = new ConsoleAppender<>();
        consoleAppender.setEncoder(ple);
        consoleAppender.setContext(loggerContext);
        consoleAppender.start();

        root.addAppender(consoleAppender);
        root.setLevel(Level.DEBUG);

        return result;
    }

    /**
     * Run maven process with commands and params (-D arguments) in workingDir directory.
     *
     * @param commands - String representing maven command(s), e.g. "clean install".
     * @param params - Map of String keys and values representing -D arguments.
     * @param workingDir - Working directory.
     * @return Exit value
     * @throws Exception if an error occurs
     */
    static Integer runMaven(String commands, Map<String, String> params, String workingDir)
            throws Exception {
        String stringParams = toJavaParams(params);
        String commandMaven;

        if (SystemUtils.IS_OS_WINDOWS) {
            commandMaven = String.format("cmd.exe /X /C \"mvn %s %s\"", commands, stringParams);
        } else {
            commandMaven = String.format("mvn %s %s", commands, stringParams);
        }

        logger.info("Maven command: {} (cwd: {})", commandMaven, workingDir);

        return runCommandAndWait(commandMaven, workingDir, MVN_LOCATION + File.separator + "bin");
    }

    /**
     * Run runScript.groovy script in workingDir directory.
     *
     * @param workingDir - Directory with groovy script.
     * @param file - groovy script to run
     * @throws Exception if an error occurs
     */
    private static void runScript(String workingDir, String file)
            throws Exception {
        File verify = new File(workingDir + File.separator + file + ".groovy");
        if (!verify.isFile()) {
            return;
        }
        Binding binding = new Binding();
        binding.setVariable("basedir", workingDir);
        GroovyScriptEngine engine = new GroovyScriptEngine(workingDir);
        engine.run(file + ".groovy", binding);
    }

    /**
     * Get default location of integration test by test name.
     *
     * @param test - Test name.
     * @return Default location of integration test, e.g. ~/pom-manipulation-ext/integration-test/target/it-cli/it-test
     */
    static String getDefaultTestLocation(String test) throws IOException {
        return IT_LOCATION.getCanonicalPath() + File.separator + test;
    }

    /**
     * Convert string parameters in a Map to a String of -D arguments
     *
     * @param params - Map of java parameters
     * @return - String of -D arguments
     */
    private static String toJavaParams(Map<String, String> params) {
        StringBuilder stringParams = new StringBuilder();
        Iterator<String> it = params.keySet().iterator();
        while (it.hasNext()) {
            String key = it.next();
            stringParams.append(String.format("-D%s=%s", key, params.get(key)));
            if (it.hasNext()) {
                stringParams.append(" ");
            }
        }
        return stringParams.toString();
    }

    /**
     * Convert string parameters in a Map to a String of -- arguments
     *
     * @param params - Map of java parameters
     * @return - String of - arguments
     */
    private static String toFlagsParams(Map<String, String> params) {
        StringBuilder stringParams = new StringBuilder();
        Iterator<String> it = params.keySet().iterator();
        while (it.hasNext()) {
            String key = it.next();
            stringParams.append(String.format("--%s=%s", key, params.get(key)));
            if (it.hasNext()) {
                stringParams.append(" ");
            }
        }
        return stringParams.toString();
    }

    /**
     * Run command in another process and wait for it to finish.
     *
     * @param command - Command to be run in another process, e.g. "mvn clean install"
     * @param workingDir - Working directory in which to run the command.
     * @param extraPath - Extra path settings
     * @return exit value.
     * @throws Exception if an error occurs
     */
    private static Integer runCommandAndWait(String command, String workingDir, String extraPath)
            throws Exception {
        String path = System.getenv("PATH");
        if (extraPath != null) {
            path = extraPath + File.pathSeparator + path;
        }

        Process proc = Runtime.getRuntime().exec(command, new String[] { "PATH=" + path }, new File(workingDir));
        File buildlog = new File(workingDir, "build.log");

        try (BufferedReader stdout = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                BufferedReader stderr = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
                PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(buildlog, true)))) {
            String line;
            String errline = null;
            while ((line = stdout.readLine()) != null || (errline = stderr.readLine()) != null) {
                if (line != null) {
                    out.println(line);
                }
                if (errline != null) {
                    out.println(errline);
                }
            }

            return proc.waitFor();
        }
    }
}
