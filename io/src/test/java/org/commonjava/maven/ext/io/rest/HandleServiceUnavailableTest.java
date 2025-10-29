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
import static org.commonjava.maven.ext.io.rest.VersionTranslatorTest.loadALotOfGAVs;
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

import org.apache.commons.lang3.reflect.FieldUtils;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.io.rest.handler.SpyFailJettyHandler;
import org.commonjava.maven.ext.io.rest.rule.MockServer;
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
public class HandleServiceUnavailableTest {
    private static List<ProjectVersionRef> aLotOfGavs;

    private final Logger logger = LoggerFactory.getLogger(HandleServiceUnavailableTest.class);

    private DefaultTranslator versionTranslator;

    @Rule
    public TestName testName = new TestName();

    private final SpyFailJettyHandler handler = new SpyFailJettyHandler();

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

        handler.setStatusCode(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
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

    /**
     * Ensures that the version translation does not fail upon receiving 503
     * and follow the same behavior as that of a 504.
     */
    @Test
    public void testTranslateVersionsCorrectSplit() throws IllegalAccessException {
        List<ProjectVersionRef> data = aLotOfGavs.subList(0, 37);
        handler.getRequestData().clear();
        try {
            // Decrease the wait time so that the test does not take too long
            FieldUtils.writeField(versionTranslator, "retryDuration", 5, true);
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
}
