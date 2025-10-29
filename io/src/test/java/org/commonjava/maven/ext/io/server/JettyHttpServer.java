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
package org.commonjava.maven.ext.io.server;

import java.net.InetSocketAddress;

import org.commonjava.maven.ext.io.server.exception.ServerInternalException;
import org.commonjava.maven.ext.io.server.exception.ServerSetupException;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author vdedik@redhat.com
 */
public class JettyHttpServer
        implements HttpServer {
    private static final Logger logger = LoggerFactory.getLogger(JettyHttpServer.class);

    private final Server jettyServer;

    private final Handler handler;

    private Integer port;

    public JettyHttpServer(Handler handler) {
        this.handler = handler;
        this.jettyServer = createAndStartJetty();
    }

    public Integer getPort() {
        return this.port;
    }

    public void shutdown() {
        try {
            this.jettyServer.stop();
        } catch (Exception e) {
            throw new ServerInternalException("Error shutting down jetty", e);
        }
    }

    private Server createAndStartJetty() {
        Server jetty = new Server(new InetSocketAddress("127.0.0.1", 0));
        jetty.setHandler(handler);

        try {
            jetty.start();
        } catch (Exception e) {
            throw new ServerSetupException("Error starting jetty", e);
        }

        logger.debug("Returning local port for Jetty {}", ((ServerConnector) jetty.getConnectors()[0]).getLocalPort());
        port = ((ServerConnector) jetty.getConnectors()[0]).getLocalPort();

        return jetty;
    }
}
