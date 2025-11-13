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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectVersionRef;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.jboss.pnc.mavenmanipulator.io.rest.rule.MockServer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TestName;
import org.slf4j.LoggerFactory;

public class HttpHeaderHeaderTest {
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog();

    @Rule
    public final TestName testName = new TestName();

    @Rule
    public final MockServer mockServer = new MockServer(new AbstractHandler() {
        @Override
        public void handle(
                String target,
                Request baseRequest,
                HttpServletRequest request,
                HttpServletResponse response)
                throws IOException {
            response.setStatus(HttpServletResponse.SC_GATEWAY_TIMEOUT);
            response.getWriter().print(HttpHeaderHeaderTest.this.generateResponse());
            baseRequest.setHandled(true);
        }
    });

    protected DefaultTranslator versionTranslator;

    protected String testResponseStart = "{\"errorType\":\"";
    protected String testResponseEnd = "\"}";

    @Before
    public void before() {
        LoggerFactory.getLogger(HttpHeaderHeaderTest.class).info("Executing test " + testName.getMethodName());

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

    private String generateResponse() {
        return testResponseStart + (testResponseEnd == null ? "" : "pme" + testResponseEnd);
    }

    @Test
    public void testVerifyContentHeaderMessage() {
        List<ProjectVersionRef> gavs = Collections.singletonList(
                new SimpleProjectVersionRef("com.example", "example", "1.0"));

        try {
            versionTranslator.lookupVersions(gavs);
            fail("Failed to throw RestException.");
        } catch (RestException ex) {
            assertTrue(systemOutRule.getLog().contains("errorType"));
        }
    }

    @Test
    public void testVerifyContentHeaderMessageNoEscape() {
        testResponseStart = "{\"errorMessage\":\"";
        testResponseEnd = "\"}";

        List<ProjectVersionRef> gavs = Collections.singletonList(
                new SimpleProjectVersionRef("com.example", "example", "1.0"));

        try {
            versionTranslator.lookupVersions(gavs);
            fail("Failed to throw RestException.");
        } catch (RestException ex) {
            assertTrue(systemOutRule.getLog().contains("errorMessage"));
        }
    }

    @Test
    public void testVerifyContentHeaderMessageContents() {
        testResponseStart = "{\"errorType\":\"";
        testResponseEnd = "\"}";

        List<ProjectVersionRef> gavs = Collections.singletonList(
                new SimpleProjectVersionRef("com.example", "example", "1.0"));

        try {
            versionTranslator.lookupVersions(gavs);
            fail("Failed to throw RestException.");
        } catch (RestException ex) {
            assertTrue(ex.getMessage().contains("pme"));
        }
    }

    @Test
    public void testVerifyContentHeaderMultipleMessageContents() {
        testResponseStart = "{\"errorType\":\"MY-TYPE\",\"errorMessage\":\"MY-MESSAGE\"}";
        testResponseEnd = null;

        List<ProjectVersionRef> gavs = Collections.singletonList(
                new SimpleProjectVersionRef("com.example", "example", "1.0"));

        try {
            versionTranslator.lookupVersions(gavs);
            fail("Failed to throw RestException.");
        } catch (RestException ex) {
            assertTrue(systemOutRule.getLog().contains("MY-TYPE MY-MESSAGE"));
            assertTrue(ex.getMessage().contains("MY-TYPE MY-MESSAGE"));
        }
    }

    @Test
    public void testVerifyContentHeaderMessageContentsHTML() {
        testResponseStart = "<html><body><h1>504 Gateway Time-out</h1>\n" +
                "The server didn't respond in time.\n" +
                "</body></html>";
        testResponseEnd = null;

        List<ProjectVersionRef> gavs = Collections.singletonList(
                new SimpleProjectVersionRef("com.example", "example", "1.0"));

        try {
            versionTranslator.lookupVersions(gavs);
            fail("Failed to throw RestException.");
        } catch (RestException ex) {
            assertTrue(systemOutRule.getLog().contains("504 Gateway Time-out The server didn't respond in time"));
        }
    }
}
