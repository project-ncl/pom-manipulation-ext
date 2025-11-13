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

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.jboss.da.lookup.model.MavenLatestResult;
import org.jboss.da.lookup.model.MavenLookupRequest;
import org.jboss.da.lookup.model.MavenLookupResult;
import org.jboss.da.model.rest.GAV;
import org.jboss.pnc.mavenmanipulator.common.util.JSONUtils;
import org.jboss.pnc.mavenmanipulator.io.rest.DefaultTranslator.Endpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Setter;

/**
 * @author vdedik@redhat.com
 */
public class AddSuffixJettyHandler
        extends AbstractHandler
        implements Handler {
    public static final String SUFFIX = "redhat";

    public static final String DEFAULT_SUFFIX = SUFFIX + "-1";

    public static final String EXTENDED_SUFFIX = SUFFIX + "-2";

    public static final String MIXED_SUFFIX = "temporary-" + DEFAULT_SUFFIX;

    public static final String TIMESTAMP_SUFFIX = "t20180920-163311-423-" + DEFAULT_SUFFIX;

    private final Logger logger = LoggerFactory.getLogger(AddSuffixJettyHandler.class);

    private final String endpoint;

    private final JSONUtils.InternalObjectMapper objectMapper = new JSONUtils.InternalObjectMapper(new ObjectMapper());

    @Setter
    private String suffix;

    @Setter
    private boolean useCustomMixedSuffix;

    @Setter
    private boolean usePartialCustomMixedSuffix;

    public AddSuffixJettyHandler() {
        this(Endpoint.LOOKUP_GAVS.getEndpoint(), DEFAULT_SUFFIX);
    }

    public AddSuffixJettyHandler(String endpoint, String suffix) {
        this.endpoint = endpoint;
        this.suffix = suffix;
    }

    @Override
    public void handle(
            String target,
            Request baseRequest,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {
        logger.info("Handling with AddSuffixJettyHandler: {} {}", request.getMethod(), request.getPathInfo());

        if (logger.isDebugEnabled()) {
            Enumeration<String> e = request.getHeaderNames();
            while (e.hasMoreElements()) {
                String name = e.nextElement();
                logger.debug("Request has header: {}: {}", name, request.getHeader(name));
            }
        }

        if (target.contains(this.endpoint)) {
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
            logger.info("Read request body '{}' and read parameters '{}'.", jb, request.getParameterMap());

            Set<GAV> requestBody;

            List<Object> responseBody = new ArrayList<>();

            // Protocol analysis
            MavenLookupRequest lookupGAVsRequest = objectMapper.readValue(jb.toString(), MavenLookupRequest.class);
            requestBody = lookupGAVsRequest.getArtifacts();

            boolean returnNullBestMatch = "NullBestMatchVersion".equals(lookupGAVsRequest.getMode());

            // Prepare Response
            for (GAV gav : requestBody) {
                Object lr;

                String version = gav.getVersion();
                String bestMatchVersion;

                logger.debug("Processing artifactId {} with version {}", gav.getArtifactId(), version);

                // Specific to certain integration tests. For the SNAPSHOT test we want to verify it can handle
                // a already built version. The PME code should remove SNAPSHOT before sending it.
                if (gav.getArtifactId().startsWith("rest-dependency-version-manip-child-module")) {
                    bestMatchVersion = version + "-" + EXTENDED_SUFFIX;
                } else if (gav.getGroupId().equals("org.goots.maven.circulardependencies-test-parent")) {
                    bestMatchVersion = version + "." + SUFFIX + "-3";
                } else if (gav.getArtifactId().equals("rest-version-manip-mixed-suffix-orig-rh")) {
                    bestMatchVersion = version + "-" + this.suffix;
                } else if (gav.getArtifactId().startsWith("depMgmt2") ||
                        gav.getArtifactId().startsWith("pluginMgmt3")) {
                    bestMatchVersion = "1.0.0-" + EXTENDED_SUFFIX;
                } else if (gav.getArtifactId().startsWith("rest-version-manip-suffix-strip-increment")) {
                    if (version.contains("jbossorg")) {
                        bestMatchVersion = "";
                    } else {
                        bestMatchVersion = version + "-" + EXTENDED_SUFFIX;
                    }
                } else if (useCustomMixedSuffix || usePartialCustomMixedSuffix
                        || "GroovyWithTemporary".equals(lookupGAVsRequest.getMode())) {
                    if (useCustomMixedSuffix && gav.getArtifactId().equals("commons-lang")) {
                        // We know its redhat-1 (hardcoded in the pom).
                        int separatorIndex = version.indexOf(SUFFIX) - 1;
                        bestMatchVersion = version.substring(0, separatorIndex) + version.substring(
                                separatorIndex,
                                separatorIndex + 1)
                                + MIXED_SUFFIX;
                    } else if (useCustomMixedSuffix && gav.getArtifactId().equals("errai-tools")) {
                        int separatorIndex = version.indexOf(SUFFIX) - 1;
                        bestMatchVersion = version.substring(0, separatorIndex) + version.substring(
                                separatorIndex,
                                separatorIndex + 1)
                                + EXTENDED_SUFFIX;
                    } else if (useCustomMixedSuffix && gav.getArtifactId().equals("commons-httpclient")) {
                        int separatorIndex = version.indexOf("temporary-redhat") - 1;
                        bestMatchVersion = version.substring(0, separatorIndex) + version.substring(
                                separatorIndex,
                                separatorIndex + 1)
                                + "temporary-redhat-2";
                    }
                    // For GroovyFunctionsTest::testTempOverrideWithNonTemp
                    else if (gav.getArtifactId().equals("groovy-project-removal")) {
                        bestMatchVersion = version + "-" + this.suffix;
                    }
                    // For GroovyFunctionsTest::testOverrideWithTemp
                    else if (gav.getGroupId().equals("io.hawt")) {
                        bestMatchVersion = version + "-" + MIXED_SUFFIX;
                    }
                    // For GroovyFunctionsTest::testOverrideWithTemp
                    else if (gav.getArtifactId().equals("testTempOverrideWithNonTemp")) {
                        bestMatchVersion = version + "-" + SUFFIX + "-5";
                    } else {
                        bestMatchVersion = version + "-" + MIXED_SUFFIX;
                    }
                } else if (gav.getVersion().equals("2.7.2_3-fuse")) {
                    // For RESTVersionManipOnly - simulate whether version modification has been already performed or not.
                    bestMatchVersion = version;
                } else {
                    bestMatchVersion = version + "-" + this.suffix;
                }
                logger.info("For GA {}, requesting version {} and got bestMatch {}", gav, version, bestMatchVersion);

                if (request.getPathInfo().contains(Endpoint.LOOKUP_LATEST.getEndpoint())) {
                    lr = new MavenLatestResult(gav, !returnNullBestMatch ? bestMatchVersion : null);
                } else {
                    lr = new MavenLookupResult(gav, !returnNullBestMatch ? bestMatchVersion : null);
                }

                responseBody.add(lr);
            }

            logger.info("Returning response body size {}", responseBody.size());

            response.getWriter().println(objectMapper.writeValue(responseBody));

            // Set Response
            response.setContentType("application/json;charset=utf-8");
            response.setStatus(HttpServletResponse.SC_OK);
            baseRequest.setHandled(true);
        } else {
            logger.info(
                    "Handling: {} with AddSuffixJettyHandler failed, because expected method was {} and endpoint {}",
                    request.getMethod(),
                    request.getPathInfo(),
                    this.endpoint);
        }
    }
}
