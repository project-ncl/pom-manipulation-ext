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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectVersionRef;
import org.commonjava.maven.ext.io.rest.rule.MockServer;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.redhat.resilience.otel.OTelCLIHelper;

public class RESTHeaderTest {
    @Rule
    public TestName name = new TestName();

    @Rule
    public final MockServer mockServer = new MockServer(new AbstractHandler() {
        @Override
        public void handle(
                String target,
                Request baseRequest,
                HttpServletRequest request,
                HttpServletResponse response) {
            if (name.getMethodName().equals("testVerifyHeader")) {
                assertEquals("bar", request.getHeader("Foo"));
                assertEquals("baz", request.getHeader("Bar"));
            } else {
                assertNotNull(request.getHeader("traceparent"));
                assertNotNull(request.getHeader("trace-id"));
                assertNotNull(request.getHeader("span-id"));

            }
            baseRequest.setHandled(true);
        }
    });

    @Test
    public void testVerifyHeader()
            throws RestException {
        Map<String, String> headers = new LinkedHashMap<>(2);
        headers.put("Foo", "bar");
        headers.put("Bar", "baz");
        Translator translator = new DefaultTranslator(
                mockServer.getUrl(),
                0,
                Translator.CHUNK_SPLIT_COUNT,
                false,
                "rebuild",
                headers,
                DEFAULT_CONNECTION_TIMEOUT_SEC,
                DEFAULT_SOCKET_TIMEOUT_SEC,
                RETRY_DURATION_SEC);

        List<ProjectVersionRef> gavs = Collections.singletonList(
                new SimpleProjectVersionRef("com.example", "example", "1.0"));

        translator.lookupVersions(gavs);
    }

    @Test
    public void testVerifyHeaderOtel()
            throws RestException {
        OTelCLIHelper.startOTel(
                "test",
                "cli",
                OTelCLIHelper.defaultSpanProcessor(
                        OTelCLIHelper.defaultSpanExporter(
                                "http://localhost:9090")));
        Translator t = new DefaultTranslator(
                mockServer.getUrl(),
                0,
                Translator.CHUNK_SPLIT_COUNT,
                false,
                "rebuild",
                Collections.emptyMap(),
                DEFAULT_CONNECTION_TIMEOUT_SEC,
                DEFAULT_SOCKET_TIMEOUT_SEC,
                RETRY_DURATION_SEC);
        List<ProjectVersionRef> gavs = Collections.singletonList(
                new SimpleProjectVersionRef("com.example", "example", "1.0"));

        t.lookupVersions(gavs);

        OTelCLIHelper.stopOTel();
    }
}
