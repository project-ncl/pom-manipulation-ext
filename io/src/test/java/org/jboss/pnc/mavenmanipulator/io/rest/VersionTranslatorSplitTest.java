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

import static org.jboss.pnc.mavenmanipulator.io.rest.Translator.DEFAULT_CONNECTION_TIMEOUT_SEC;
import static org.jboss.pnc.mavenmanipulator.io.rest.Translator.DEFAULT_SOCKET_TIMEOUT_SEC;
import static org.jboss.pnc.mavenmanipulator.io.rest.Translator.RETRY_DURATION_SEC;
import static org.jboss.pnc.mavenmanipulator.io.rest.VersionTranslatorTest.loadALotOfGAVs;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;

import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.jboss.pnc.mavenmanipulator.io.rest.handler.SpyFailJettyHandler;
import org.jboss.pnc.mavenmanipulator.io.rest.rule.MockServer;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kong.unirest.Unirest;

/**
 * @author Jakub Senko <jsenko@redhat.com>
 */
public class VersionTranslatorSplitTest {
    private static List<ProjectVersionRef> aLotOfGavs;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private DefaultTranslator versionTranslator;

    private final SpyFailJettyHandler handler = new SpyFailJettyHandler();

    @Rule
    public TestName testName = new TestName();

    @Rule
    public MockServer mockServer = new MockServer(handler);

    @BeforeClass
    public static void startUp() throws IOException {
        aLotOfGavs = loadALotOfGAVs();
        assertTrue(aLotOfGavs.size() >= 37);
    }

    @Before
    public void before() {
        logger.info("Executing test " + testName.getMethodName());

        handler.setStatusCode(HttpServletResponse.SC_GATEWAY_TIMEOUT);
        versionTranslator = new DefaultTranslator(
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
    public void testTranslateVersionsCorrectSplit() {
        List<ProjectVersionRef> data = aLotOfGavs.subList(0, 37);
        handler.getRequestData().clear();
        try {
            versionTranslator.lookupVersions(data);
            fail();
        } catch (RestException ex) {
            // ok
        }
        List<List<Map<String, Object>>> requestData = handler.getRequestData();
        // split 37 -> 9, 9, 9, 10
        // split 9 -> 2, 2, 2, 3 (x3)
        // split 10 -> 2, 2, 2, 4
        // split 4 -> 1, 1, 1, 1
        // 37, 9, 9, 9, 10, 2, 2, 2, 3, ..., 2, 2, 2, 4, 1, 1, 1, 1 -> total 21 chunks
        // However, split fails after the 6th attempt
        logger.debug(requestData.toString());
        assertEquals(6, requestData.size());
        assertEquals(37, requestData.get(0).size());
        for (int i = 1; i < 4; i++) {
            assertEquals(9, requestData.get(i).size());
        }
        assertEquals(10, requestData.get(4).size());
        assertEquals(2, requestData.get(5).size());

        Set<Map<String, Object>> original = new HashSet<>(requestData.get(0));

        Set<Map<String, Object>> chunks = new HashSet<>();
        for (List<Map<String, Object>> e : requestData.subList(1, 5)) {
            chunks.addAll(e);
        }
        assertEquals(original, chunks);
    }

    @Test
    public void testTranslateVersionsCorrectSplit2() {
        List<ProjectVersionRef> data = aLotOfGavs.subList(0, 36);
        handler.getRequestData().clear();
        try {
            versionTranslator.lookupVersions(data);
            fail();
        } catch (RestException ex) {
            // ok
        }
        List<List<Map<String, Object>>> requestData = handler.getRequestData();
        // split 36 -> 9, 9, 9, 9
        // split 9 -> 2, 2, 2, 3 (x4)
        // 36, 9, 9, 9, 9, 2, 2, 2, 3, ... -> total 21 chunks
        // Split fails after the 6th attempt
        logger.debug(requestData.toString());
        assertEquals(6, requestData.size());
        assertEquals(36, requestData.get(0).size());
        for (int i = 1; i < 5; i++) {
            assertEquals(9, requestData.get(i).size());
        }
        assertEquals(2, requestData.get(5).size());

        Set<Map<String, Object>> original = new HashSet<>(requestData.get(0));

        Set<Map<String, Object>> chunks = new HashSet<>();
        for (List<Map<String, Object>> e : requestData.subList(1, 5)) {
            chunks.addAll(e);
        }
        assertEquals(original, chunks);
    }

    @Test
    public void testTranslateVersionsCorrectSplitMaxSize() {
        this.versionTranslator = new DefaultTranslator(
                mockServer.getUrl(),
                10,
                Translator.CHUNK_SPLIT_COUNT,
                false,
                "",
                Collections.emptyMap(),
                DEFAULT_CONNECTION_TIMEOUT_SEC,
                DEFAULT_SOCKET_TIMEOUT_SEC,
                RETRY_DURATION_SEC);

        List<ProjectVersionRef> data = aLotOfGavs.subList(0, 30);
        handler.getRequestData().clear();
        try {
            versionTranslator.lookupVersions(data);
            fail();
        } catch (RestException ignored) {
        }
        List<List<Map<String, Object>>> requestData = handler.getRequestData();

        // split 10,10,10
        // split    10,10,2,2,2,4
        // split       10,2,2,2,4,2,2,2,4
        // split          2,2....
        // = 10 : 10 : 10 : 2
        logger.debug(requestData.toString());
        assertEquals(4, requestData.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(10, requestData.get(i).size());
        }
        assertEquals(2, requestData.get(3).size());

        Set<Map<String, Object>> chunks = new HashSet<>();
        for (List<Map<String, Object>> e : requestData.subList(0, 3)) {
            chunks.addAll(e);
        }
        assertEquals(data.size(), chunks.size());
    }

    @Test
    public void testTranslateVersionsNoSplitOnNon504() {
        List<ProjectVersionRef> data = aLotOfGavs.subList(0, 36);
        handler.getRequestData().clear();
        handler.setStatusCode(HttpServletResponse.SC_BAD_GATEWAY);
        try {
            versionTranslator.lookupVersions(data);
            fail();
        } catch (RestException ex) {
            // ok
        }
        List<List<Map<String, Object>>> requestData = handler.getRequestData();

        logger.debug(requestData.toString());

        // Due to this returning a non-504 it shouldn't do any splits so we should get size 1 containing 36 back
        assertEquals(1, requestData.size());
        assertEquals(36, requestData.get(0).size());
    }

    @Test
    public void testTranslateVersionsCorrectSplitMaxSizeWithMin() {
        this.versionTranslator = new DefaultTranslator(
                mockServer.getUrl(),
                10,
                1,
                false,
                "",
                Collections.emptyMap(),
                DEFAULT_CONNECTION_TIMEOUT_SEC,
                DEFAULT_SOCKET_TIMEOUT_SEC,
                RETRY_DURATION_SEC);

        List<ProjectVersionRef> data = aLotOfGavs.subList(0, 30);
        handler.getRequestData().clear();
        try {
            versionTranslator.lookupVersions(data);
            fail();
        } catch (RestException ignored) {
        }
        List<List<Map<String, Object>>> requestData = handler.getRequestData();

        // split 30 ->
        //        10,10,10
        // split    10,10,2,2,2,4
        // split       10,2,2,2,4,2,2,2,4
        // split          2,2,2,4,2,2,2,4
        // split            2,2,4,2,2,2,4,2,2,2,4,1
        // split              2,4,2,2,2,4,2,2,2,4,1...
        // split                4,2,2,2,4,2,2,2,4,1...
        // split                  2,2,2,4,2,2,2,4,1...
        // split                    2,2,4,2,2,2,4,1...
        // split                      2,4,2,2,2,4,1...
        // split                        4,2,2,2,4,1...
        // split                          2,2,2,4,1...
        // split                            2,2,4,1...
        // split                              2,4,1...
        // split                                4,1...
        // split                                   1...
        // Count of 16 (all outer edges )
        logger.debug(requestData.toString());
        assertEquals(16, requestData.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(10, requestData.get(i).size());
        }
        assertEquals(1, requestData.get(15).size());
    }
}
