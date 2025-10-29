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

import static org.codehaus.plexus.util.FileUtils.copyFile;
import static org.commonjava.maven.ext.integrationtest.ITestUtils.runCli;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.commonjava.maven.ext.core.ManipulationManager;
import org.commonjava.maven.ext.core.state.VersioningState;
import org.commonjava.maven.ext.io.rest.handler.AddSuffixJettyHandler;
import org.commonjava.maven.ext.io.rest.rule.MockServer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Manipulators may output data in a structured form in a JSON file
 * for further processing, by allowing some of the state fields to be serialized.
 * This test verifies that this works in case of {@link VersioningState}.
 *
 * @author Jakub Senko
 */
public class ResultJsonFileTest {
    private final File workingDirectory = new File(System.getProperty("user.dir"));

    private final ObjectMapper MAPPER = new ObjectMapper();

    @Rule
    public MockServer mockServer = new MockServer(new AddSuffixJettyHandler("/", AddSuffixJettyHandler.DEFAULT_SUFFIX));

    @Rule
    public TemporaryFolder tmpFolderRule = new TemporaryFolder();

    @Rule
    public TemporaryFolder tmpFolderWorkingRule = new TemporaryFolder(new File(workingDirectory, "target"));

    @Test
    public void testCliExitValue()
            throws Exception {
        // given

        File baseDir = tmpFolderRule.newFolder();
        File pomFile = getResourceFile();
        copyFile(pomFile, new File(baseDir, "pom.xml"));

        // when

        Map<String, String> params = new HashMap<>();
        params.put(VersioningState.INCREMENT_SERIAL_SUFFIX_SYSPROP, AddSuffixJettyHandler.SUFFIX);
        params.put("dependencyOverride.org.apache.qpid-proton-j-parent@*", "0.31.0.redhat-00001");
        params.put(VersioningState.INCREMENT_SERIAL_SUFFIX_PADDING_SYSPROP, "0");

        Integer exitValue = runCli(Collections.emptyList(), params, baseDir.getCanonicalPath());

        assertEquals(10, exitValue.intValue());
    }

    @Test
    public void testVersioningStateOutputJsonFile()
            throws Exception {
        // given

        File baseDir = tmpFolderRule.newFolder();
        String basePath = baseDir.getCanonicalPath();
        File pomFile = getResourceFile();
        copyFile(pomFile, new File(baseDir, "pom.xml"));

        // when

        Map<String, String> params = new HashMap<>();
        params.put("restURL", mockServer.getUrl());
        params.put(VersioningState.INCREMENT_SERIAL_SUFFIX_SYSPROP, AddSuffixJettyHandler.SUFFIX);
        params.put(VersioningState.INCREMENT_SERIAL_SUFFIX_PADDING_SYSPROP, "0");

        Integer exitValue = runCli(Collections.emptyList(), params, basePath);

        // then

        assertEquals((Integer) 0, exitValue);

        File outputJsonFile = Paths.get(basePath, "target", ManipulationManager.REPORT_JSON_DEFAULT).toFile();
        assertTrue(outputJsonFile.exists());

        JsonNode rootNode = MAPPER.readTree(outputJsonFile);

        JsonNode executionRootModified = rootNode.get("executionRoot");
        assertNotNull(executionRootModified);

        JsonNode groupId = executionRootModified.get("groupId");
        JsonNode artifactId = executionRootModified.get("artifactId");
        JsonNode version = executionRootModified.get("version");
        assertNotNull(groupId);
        assertNotNull(artifactId);
        assertNotNull(version);

        assertEquals("org.commonjava.maven.ext.versioning.test", groupId.textValue());
        assertEquals("project-version", artifactId.textValue());
        assertEquals("1.0.0.redhat-2", version.textValue());
    }

    @Test
    public void testVersioningStateOutputJsonFileSubDirectory()
            throws Exception {
        // given

        File baseDir = tmpFolderWorkingRule.newFolder();
        String basePath = baseDir.getCanonicalPath();
        File pomFile = getResourceFile();
        copyFile(pomFile, new File(baseDir, "pom.xml"));

        // when

        Map<String, String> params = new HashMap<>();
        params.put("restURL", mockServer.getUrl());
        params.put(VersioningState.INCREMENT_SERIAL_SUFFIX_SYSPROP, AddSuffixJettyHandler.SUFFIX);
        params.put(VersioningState.INCREMENT_SERIAL_SUFFIX_PADDING_SYSPROP, "0");

        List<String> args = new ArrayList<>();
        args.add(
                "--file=" + Paths.get(workingDirectory.getCanonicalPath()).relativize(Paths.get(basePath))
                        + File.separator + "pom.xml");

        Integer exitValue = runCli(args, params, baseDir.getCanonicalPath());

        // then

        assertEquals((Integer) 0, exitValue);

        File outputJsonFile = Paths.get(basePath, "target", ManipulationManager.REPORT_JSON_DEFAULT).toFile();

        assertTrue(outputJsonFile.exists());

        JsonNode rootNode = MAPPER.readTree(outputJsonFile);

        JsonNode executionRootModified = rootNode.get("executionRoot");
        assertNotNull(executionRootModified);

        JsonNode groupId = executionRootModified.get("groupId");
        JsonNode artifactId = executionRootModified.get("artifactId");
        JsonNode version = executionRootModified.get("version");
        assertNotNull(groupId);
        assertNotNull(artifactId);
        assertNotNull(version);

        assertEquals("org.commonjava.maven.ext.versioning.test", groupId.textValue());
        assertEquals("project-version", artifactId.textValue());
        assertEquals("1.0.0.redhat-2", version.textValue());
    }

    @Test
    public void testNoVersioningChangeOutputJsonFile()
            throws Exception {
        // given

        File baseDir = tmpFolderRule.newFolder();
        File pomFile = getResourceFile();
        String basePath = baseDir.getCanonicalPath();
        copyFile(pomFile, new File(baseDir, "pom.xml"));

        // when

        Map<String, String> params = new HashMap<>();
        params.put("restURL", mockServer.getUrl());
        params.put("repo-reporting-removal", "true");
        params.put(VersioningState.INCREMENT_SERIAL_SUFFIX_PADDING_SYSPROP, "0");

        Integer exitValue = runCli(Collections.emptyList(), params, basePath);

        // then

        assertEquals((Integer) 0, exitValue);

        File outputJsonFile = Paths.get(basePath, "target", ManipulationManager.REPORT_JSON_DEFAULT).toFile();

        assertTrue(outputJsonFile.exists());

        JsonNode rootNode = MAPPER.readTree(outputJsonFile);

        JsonNode executionRootModified = rootNode.get("executionRoot");
        assertNotNull(executionRootModified);

        JsonNode groupId = executionRootModified.get("groupId");
        JsonNode artifactId = executionRootModified.get("artifactId");
        JsonNode version = executionRootModified.get("version");
        assertNotNull(groupId);
        assertNotNull(artifactId);
        assertNotNull(version);

        assertEquals("org.commonjava.maven.ext.versioning.test", groupId.textValue());
        assertEquals("project-version", artifactId.textValue());
        assertEquals("1.0", version.textValue());
    }

    @SuppressWarnings("ConstantConditions")
    private File getResourceFile() {
        ClassLoader classLoader = getClass().getClassLoader();
        return new File(classLoader.getResource("result-json-test/pom.xml").getFile());
    }

    @Test
    public void testVersioningStateCustomOutputJsonFile()
            throws Exception {
        // given

        File outputDir = tmpFolderRule.newFolder();

        File baseDir = tmpFolderRule.newFolder();
        File pomFile = getResourceFile();
        copyFile(pomFile, new File(baseDir, "pom.xml"));

        // when

        Map<String, String> params = new HashMap<>();
        params.put("restURL", mockServer.getUrl());
        params.put(VersioningState.INCREMENT_SERIAL_SUFFIX_SYSPROP, AddSuffixJettyHandler.SUFFIX);
        params.put(VersioningState.INCREMENT_SERIAL_SUFFIX_PADDING_SYSPROP, "0");
        params.put(
                ManipulationManager.REPORT_JSON_OUTPUT_FILE,
                outputDir.toString() + File.separator
                        + ManipulationManager.REPORT_JSON_DEFAULT);

        Integer exitValue = runCli(Collections.emptyList(), params, baseDir.getCanonicalPath());

        // then

        assertEquals((Integer) 0, exitValue);

        File outputJsonFile = new File(outputDir, ManipulationManager.REPORT_JSON_DEFAULT);
        assertTrue(outputJsonFile.exists());

        JsonNode rootNode = MAPPER.readTree(outputJsonFile);

        JsonNode executionRootModified = rootNode.get("executionRoot");
        assertNotNull(executionRootModified);

        JsonNode groupId = executionRootModified.get("groupId");
        JsonNode artifactId = executionRootModified.get("artifactId");
        JsonNode version = executionRootModified.get("version");
        assertNotNull(groupId);
        assertNotNull(artifactId);
        assertNotNull(version);

        assertEquals("org.commonjava.maven.ext.versioning.test", groupId.textValue());
        assertEquals("project-version", artifactId.textValue());
        assertEquals("1.0.0.redhat-2", version.textValue());
    }

}
