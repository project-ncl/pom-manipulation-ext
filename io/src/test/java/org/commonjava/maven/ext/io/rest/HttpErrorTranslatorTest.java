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
package org.commonjava.maven.ext.io.rest;

import static org.commonjava.maven.ext.io.rest.Translator.DEFAULT_CONNECTION_TIMEOUT_SEC;
import static org.commonjava.maven.ext.io.rest.Translator.DEFAULT_SOCKET_TIMEOUT_SEC;
import static org.commonjava.maven.ext.io.rest.Translator.RETRY_DURATION_SEC;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectVersionRef;
import org.commonjava.maven.ext.io.rest.rule.MockServer;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runners.MethodSorters;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class HttpErrorTranslatorTest {
    private static List<ProjectVersionRef> aLotOfGavs;

    private DefaultTranslator versionTranslator;

    @Rule
    public TestName testName = new TestName();

    @ClassRule
    public static MockServer mockServer = new MockServer(new AbstractHandler() {
        @Override
        public void handle(
                String target,
                Request baseRequest,
                HttpServletRequest request,
                HttpServletResponse response)
                throws IOException, ServletException {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            baseRequest.setHandled(true);
            response.getWriter().println("<html><head><title>404</title></head></html>");
        }
    });

    @BeforeClass
    public static void startUp()
            throws IOException {
        aLotOfGavs = new ArrayList<>();
        String longJsonFile = readFileFromClasspath("example-response-performance-test.json");

        ObjectMapper objectMapper = new ObjectMapper();
        List<Map<String, String>> gavs = objectMapper.readValue(
                longJsonFile,
                new TypeReference<List<Map<String, String>>>() {
                });
        for (Map<String, String> gav : gavs) {
            ProjectVersionRef project = new SimpleProjectVersionRef(
                    gav.get("groupId"),
                    gav.get("artifactId"),
                    gav.get("version"));
            aLotOfGavs.add(project);
        }
    }

    @Before
    public void before() {
        LoggerFactory.getLogger(HttpErrorTranslatorTest.class).info("Executing test " + testName.getMethodName());

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
    public void testTranslateVersionsWith404() {
        List<ProjectVersionRef> gavs = Arrays.asList(
                new SimpleProjectVersionRef("com.example", "example", "1.0"),
                new SimpleProjectVersionRef("org.commonjava", "example", "1.1"));

        try {
            versionTranslator.lookupVersions(gavs);
            fail("Failed to throw RestException when server failed to respond.");
        } catch (RestException ex) {
            // Pass
            assertTrue(ex.getMessage().equals("Received response status 404 with message: 404"));
        }
    }

    private static String readFileFromClasspath(String filename) {
        StringBuilder fileContents = new StringBuilder();
        String lineSeparator = System.lineSeparator();

        try (Scanner scanner = new Scanner(HttpErrorTranslatorTest.class.getResourceAsStream(filename))) {
            while (scanner.hasNextLine()) {
                fileContents.append(scanner.nextLine());
                fileContents.append(lineSeparator);
            }
            return fileContents.toString();
        }
    }
}
