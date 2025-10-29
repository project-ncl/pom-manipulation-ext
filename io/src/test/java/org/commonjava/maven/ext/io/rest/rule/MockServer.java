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
package org.commonjava.maven.ext.io.rest.rule;

import org.commonjava.maven.ext.io.rest.handler.AddSuffixJettyHandler;
import org.commonjava.maven.ext.io.server.HttpServer;
import org.commonjava.maven.ext.io.server.JettyHttpServer;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.junit.rules.ExternalResource;

/**
 * @author vdedik@redhat.com
 */
public class MockServer
        extends ExternalResource {
    private HttpServer httpServer;
    private AbstractHandler handler;

    public MockServer(AbstractHandler handler) {
        this.handler = handler;
    }

    @Override
    public void before() {
        httpServer = new JettyHttpServer(handler);
    }

    @Override
    public void after() {
        httpServer.shutdown();
    }

    public String getUrl() {
        return "http://127.0.0.1:" + getPort();
    }

    public Integer getPort() {
        return httpServer.getPort();
    }

    public static void main(String[] args) {
        final MockServer ms = new MockServer(new AddSuffixJettyHandler());
        ms.before();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.out.println("Shutting down JettyServer");
                ms.after();
            }
        });
    }
}
