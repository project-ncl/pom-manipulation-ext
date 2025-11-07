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
package org.jboss.pnc.maven_manipulator.io.rest.handler;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Jetty handler that records requests and always fails with HTTP error 504.
 *
 * @author Jakub Senko <jsenko@redhat.com>
 */
public class SpyFailJettyHandler extends AbstractHandler implements Handler {
    private static final String ENDPOINT = "/";

    private static final String METHOD = "POST";

    private final Logger logger = LoggerFactory.getLogger(SpyFailJettyHandler.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final List<List<Map<String, Object>>> requestData = new ArrayList<>();

    private int responseCode = HttpServletResponse.SC_GATEWAY_TIMEOUT;

    @Override
    public void handle(
            String target,
            Request baseRequest,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {

        logger.info("Handling: {} {}", request.getMethod(), request.getPathInfo());

        if (target.startsWith(ENDPOINT) && request.getMethod().equals(METHOD)) {
            logger.info("Handling with SpyFailJettyHandler");

            // Get Request Body
            StringBuilder jb = new StringBuilder();
            try {
                String line;
                BufferedReader reader = request.getReader();
                while ((line = reader.readLine()) != null) {
                    jb.append(line);
                }

            } catch (Exception e) {
                logger.warn("Error reading request body. {}", e.getMessage());
                return;
            }

            List<Map<String, Object>> requestBody;

            // Protocol analysis
            GAVSchema gavSchema = objectMapper.readValue(jb.toString(), GAVSchema.class);
            requestBody = gavSchema.artifacts;

            logger.debug("Adding to requestBody of size {}", requestBody.size());

            requestData.add(requestBody);

            response.setStatus(responseCode);
            baseRequest.setHandled(true);

        } else {
            logger.info("Handling with SpyFailJettyHandler failed.");
        }
    }

    public List<List<Map<String, Object>>> getRequestData() {
        return requestData;
    }

    public void setStatusCode(int responseCode) {
        this.responseCode = responseCode;
    }
}
