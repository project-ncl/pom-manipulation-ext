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
package org.jboss.pnc.mavenmanipulator.io.rest.handler;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StaticResourceHandler
        extends ResourceHandler
        implements Handler {
    private final Logger logger = LoggerFactory.getLogger(StaticResourceHandler.class);

    public StaticResourceHandler(String target) {
        logger.info("Handling: {}", target);

        setDirectoriesListed(true);
        setWelcomeFiles(new String[] { target });
        setResourceBase(".");
    }
}
