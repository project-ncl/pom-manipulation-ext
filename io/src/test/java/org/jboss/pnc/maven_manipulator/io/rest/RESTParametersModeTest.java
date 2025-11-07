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
package org.jboss.pnc.maven_manipulator.io.rest;

import static org.jboss.pnc.maven_manipulator.io.rest.Translator.DEFAULT_CONNECTION_TIMEOUT_SEC;
import static org.jboss.pnc.maven_manipulator.io.rest.Translator.DEFAULT_SOCKET_TIMEOUT_SEC;
import static org.jboss.pnc.maven_manipulator.io.rest.Translator.RETRY_DURATION_SEC;
import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectVersionRef;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.jboss.pnc.maven_manipulator.io.rest.handler.AddSuffixJettyHandler;
import org.jboss.pnc.maven_manipulator.io.rest.handler.GAVSchema;
import org.jboss.pnc.maven_manipulator.io.rest.rule.MockServer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

public class RESTParametersModeTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(AddSuffixJettyHandler.class);

    private DefaultTranslator versionTranslator;

    private GAVSchema gavSchema;

    @Rule
    public final TestName testName = new TestName();

    @Rule
    public final MockServer mockServer = new MockServer(new AbstractHandler() {
        @Override
        public void handle(
                String target,
                Request baseRequest,
                HttpServletRequest request,
                HttpServletResponse response) throws IOException {
            ObjectMapper objectMapper = new ObjectMapper();
            StringBuilder jb = new StringBuilder();
            String line;
            BufferedReader reader = request.getReader();
            while ((line = reader.readLine()) != null) {
                jb.append(line);
            }
            gavSchema = objectMapper.readValue(jb.toString(), GAVSchema.class);
            LOGGER.info("Read request body '{}' and read parameters '{}' ", jb, request.getParameterMap());
            baseRequest.setHandled(true);

        }
    });

    @Before
    public void before() {
        LoggerFactory.getLogger(RESTParametersModeTest.class).info("Executing test " + testName.getMethodName());
    }

    @Test
    public void testVerifyMode() throws RestException {
        String suffix = "rebuild";
        this.versionTranslator = new DefaultTranslator(
                mockServer.getUrl(),
                0,
                Translator.CHUNK_SPLIT_COUNT,
                false,
                suffix,
                Collections.emptyMap(),
                DEFAULT_CONNECTION_TIMEOUT_SEC,
                DEFAULT_SOCKET_TIMEOUT_SEC,
                RETRY_DURATION_SEC);
        List<ProjectVersionRef> gavs = Collections.singletonList(
                new SimpleProjectVersionRef("com.example", "example", "1.0"));

        versionTranslator.lookupVersions(gavs);
        assertEquals(suffix, gavSchema.mode);
    }

    @Test
    public void testVerifyNoMode() throws RestException {
        String mode = "";
        this.versionTranslator = new DefaultTranslator(
                mockServer.getUrl(),
                0,
                Translator.CHUNK_SPLIT_COUNT,
                false,
                mode,
                Collections.emptyMap(),
                DEFAULT_CONNECTION_TIMEOUT_SEC,
                DEFAULT_SOCKET_TIMEOUT_SEC,
                RETRY_DURATION_SEC);
        List<ProjectVersionRef> gavs = Collections.singletonList(
                new SimpleProjectVersionRef("com.example", "example", "1.0"));

        versionTranslator.lookupVersions(gavs);
        assertEquals(mode, gavSchema.mode);
    }
}
