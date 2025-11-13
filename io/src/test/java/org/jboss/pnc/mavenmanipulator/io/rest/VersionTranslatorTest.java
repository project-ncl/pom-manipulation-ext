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
package org.jboss.pnc.mavenmanipulator.io.rest;

import static org.hamcrest.CoreMatchers.is;
import static org.jboss.pnc.mavenmanipulator.io.rest.Translator.DEFAULT_CONNECTION_TIMEOUT_SEC;
import static org.jboss.pnc.mavenmanipulator.io.rest.Translator.DEFAULT_SOCKET_TIMEOUT_SEC;
import static org.jboss.pnc.mavenmanipulator.io.rest.Translator.RETRY_DURATION_SEC;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectVersionRef;
import org.jboss.pnc.mavenmanipulator.io.rest.handler.AddSuffixJettyHandler;
import org.jboss.pnc.mavenmanipulator.io.rest.rule.MockServer;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TestName;
import org.junit.runners.MethodSorters;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import kong.unirest.Unirest;

/**
 * @author vdedik@redhat.com
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class VersionTranslatorTest {
    private static List<ProjectVersionRef> aLotOfGavs;

    private DefaultTranslator versionTranslator;

    @Rule
    public TestName testName = new TestName();

    @Rule
    public MockServer mockServer = new MockServer(new AddSuffixJettyHandler());

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @BeforeClass
    public static void startUp()
            throws IOException {
        aLotOfGavs = loadALotOfGAVs();
    }

    @Before
    public void before() {
        LoggerFactory.getLogger(VersionTranslatorTest.class).info("Executing test " + testName.getMethodName());

        this.versionTranslator = new DefaultTranslator(
                mockServer.getUrl(),
                0,
                Translator.CHUNK_SPLIT_COUNT,
                false,
                "",
                Collections.emptyMap(),
                DEFAULT_CONNECTION_TIMEOUT_SEC,
                DEFAULT_SOCKET_TIMEOUT_SEC,
                RETRY_DURATION_SEC);
    }

    @Test
    public void testConnection() {
        try {
            Unirest.post(mockServer.getUrl()).asString();
        } catch (Exception e) {
            fail("Failed to connect to server, exception message: " + e.getMessage());
        }
    }

    @Test
    public void testTranslateVersions() throws RestException {
        List<ProjectVersionRef> gavs = Arrays.asList(
                new SimpleProjectVersionRef("com.example", "example", "1.0"),
                new SimpleProjectVersionRef("com.example", "example-dep", "2.0"),
                new SimpleProjectVersionRef("org.commonjava", "example", "1.0"),
                new SimpleProjectVersionRef("org.commonjava", "example", "1.1"));

        Map<ProjectVersionRef, String> actualResult = versionTranslator.lookupVersions(gavs);
        Map<ProjectVersionRef, String> expectedResult = new HashMap<ProjectVersionRef, String>() {
            {
                put(new SimpleProjectVersionRef("com.example", "example", "1.0"), "1.0-redhat-1");
                put(new SimpleProjectVersionRef("com.example", "example-dep", "2.0"), "2.0-redhat-1");
                put(new SimpleProjectVersionRef("org.commonjava", "example", "1.0"), "1.0-redhat-1");
                put(new SimpleProjectVersionRef("org.commonjava", "example", "1.1"), "1.1-redhat-1");
            }
        };

        assertThat(actualResult, is(expectedResult));
    }

    @Test
    public void testTranslateVersionsWithNulls() throws RestException {
        this.versionTranslator = new DefaultTranslator(
                mockServer.getUrl(),
                0,
                Translator.CHUNK_SPLIT_COUNT,
                false,
                "NullBestMatchVersion",
                Collections.emptyMap(),
                DEFAULT_CONNECTION_TIMEOUT_SEC,
                DEFAULT_SOCKET_TIMEOUT_SEC,
                RETRY_DURATION_SEC);
        List<ProjectVersionRef> gavs = Arrays.asList(
                new SimpleProjectVersionRef("com.example", "example", "1.0"),
                new SimpleProjectVersionRef("com.example", "example-dep", "2.0"),
                new SimpleProjectVersionRef("org.commonjava", "example", "1.0"),
                new SimpleProjectVersionRef("org.commonjava", "example", "1.1"));

        Map<ProjectVersionRef, String> actualResult = versionTranslator.lookupVersions(gavs);

        System.out.println("### actual " + actualResult);

        // All values with null bestMatchVersion should have been filtered out.
        Map<ProjectVersionRef, String> expectedResult = new HashMap<ProjectVersionRef, String>() {
        };
        assertEquals(expectedResult, actualResult);
    }

    @Test
    public void testTranslateVersionsFailNoResponse() {
        // Some url that doesn't exist used here
        Translator translator = new DefaultTranslator(
                "http://127.0.0.2",
                0,
                Translator.CHUNK_SPLIT_COUNT,
                false,
                "",
                Collections.emptyMap(),
                DEFAULT_CONNECTION_TIMEOUT_SEC,
                DEFAULT_SOCKET_TIMEOUT_SEC,
                RETRY_DURATION_SEC);

        List<ProjectVersionRef> gavs = Collections.singletonList(
                new SimpleProjectVersionRef("com.example", "example", "1.0"));

        try {
            translator.lookupVersions(gavs);
            fail("Failed to throw RestException when server failed to respond.");
        } catch (RestException ex) {
            System.out.println("Caught ex" + ex);
            // Pass
        } catch (Exception ex) {
            fail("Expected exception is RestException, instead " + ex.getClass().getSimpleName() + "thrown.");
        }
    }

    @Test(timeout = 2000)
    public void testTranslateVersionsPerformance() throws RestException {
        Logger logbackLogger = ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME));
        Level originalLevel = logbackLogger.getLevel();

        try {
            // Disable logging for this test as impacts timing.
            logbackLogger.setLevel(Level.OFF);
            versionTranslator.lookupVersions(aLotOfGavs);
        } finally {
            logbackLogger.setLevel(originalLevel);
        }
    }

    static List<ProjectVersionRef> loadALotOfGAVs() throws IOException {
        List<ProjectVersionRef> result = new ArrayList<>();
        String result1;
        StringBuilder fileContents = new StringBuilder();
        String lineSeparator = System.lineSeparator();

        try (Scanner scanner = new Scanner(
                VersionTranslatorTest.class.getResourceAsStream(
                        "example-response-performance-test.json"))) {
            while (scanner.hasNextLine()) {
                fileContents.append(scanner.nextLine()).append(lineSeparator);
            }
            result1 = fileContents.toString();
        }
        String longJsonFile = result1;

        ObjectMapper objectMapper = new ObjectMapper();
        List<Map<String, String>> gavs = objectMapper
                .readValue(longJsonFile, new TypeReference<List<Map<String, String>>>() {
                });

        for (Map<String, String> gav : gavs) {
            ProjectVersionRef project = new SimpleProjectVersionRef(
                    gav.get("groupId"),
                    gav.get("artifactId"),
                    gav.get("version"));
            result.add(project);
        }
        return result;
    }
}
