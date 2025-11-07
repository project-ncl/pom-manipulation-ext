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
package org.jboss.pnc.maven_manipulator.integrationtest;

import static org.jboss.pnc.maven_manipulator.integrationtest.DefaultCliIntegrationTest.setupExists;
import static org.jboss.pnc.maven_manipulator.integrationtest.ITestUtils.DEFAULT_MVN_PARAMS;
import static org.jboss.pnc.maven_manipulator.integrationtest.ITestUtils.getDefaultTestLocation;
import static org.jboss.pnc.maven_manipulator.integrationtest.ITestUtils.runLikeInvoker;
import static org.jboss.pnc.maven_manipulator.integrationtest.ITestUtils.runMaven;

import java.io.File;

import org.jboss.pnc.maven_manipulator.io.rest.handler.AddSuffixJettyHandler;
import org.jboss.pnc.maven_manipulator.io.rest.rule.MockServer;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RESTIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultCliIntegrationTest.class);

    private final AddSuffixJettyHandler handler = new AddSuffixJettyHandler("/", null);

    @Rule
    public MockServer mockServer = new MockServer(handler);

    @BeforeClass
    public static void setUp()
            throws Exception {
        for (File setupTest : new File(getDefaultTestLocation("setup")).listFiles()) {
            LOGGER.info("Running install for {}", setupTest.toString());

            // Try to do some simplistic checks to see if this has already been done.
            if (!setupExists(setupTest)) {
                runMaven("install", DEFAULT_MVN_PARAMS, setupTest.toString());
            }
        }
    }

    @Before
    public void before() {
        handler.setSuffix(AddSuffixJettyHandler.DEFAULT_SUFFIX);
    }

    @Test
    public void testRESTVersionDepManip() throws Exception {
        String test = getDefaultTestLocation("rest-dependency-version-manip-child-module");
        runLikeInvoker(test, mockServer.getUrl());
    }

    @Test
    public void testRESTVersionDepManipProfile() throws Exception {
        String test = getDefaultTestLocation("rest-dependency-version-manip-profile");
        runLikeInvoker(test, mockServer.getUrl());
    }

    @Test
    public void testRESTVersionManip() throws Exception {
        String test = getDefaultTestLocation("rest-version-manip-only");
        runLikeInvoker(test, mockServer.getUrl());
    }

    @Test
    public void testRESTVersionManipSnapshot() throws Exception {
        String test = getDefaultTestLocation("rest-version-manip-snapshot");
        runLikeInvoker(test, mockServer.getUrl());
    }

    @Test
    public void testRESTVersionManipMixed() throws Exception {
        handler.setSuffix(AddSuffixJettyHandler.MIXED_SUFFIX);
        String test = getDefaultTestLocation("rest-version-manip-mixed-suffix");
        runLikeInvoker(test, mockServer.getUrl());
    }

    @Test
    public void testRESTVersionManipMixedTimestamp() throws Exception {
        handler.setSuffix(AddSuffixJettyHandler.TIMESTAMP_SUFFIX);
        String test = getDefaultTestLocation("rest-version-manip-mixed-suffix-timestamp");
        runLikeInvoker(test, mockServer.getUrl());
    }

    @Test
    public void testRESTVersionManipMixedOrigHasSuffix() throws Exception {
        try {
            handler.setUseCustomMixedSuffix(true);
            String test = getDefaultTestLocation("rest-version-manip-mixed-suffix-orig-rh");
            runLikeInvoker(test, mockServer.getUrl());
        } finally {
            handler.setUseCustomMixedSuffix(false);
        }
    }

    @Test
    public void testRESTVersionManipMixedOrigHasSuffixNoRHAlign() throws Exception {
        try {
            handler.setUsePartialCustomMixedSuffix(true);
            String test = getDefaultTestLocation("rest-version-manip-mixed-suffix-orig-rh-norhalign");
            runLikeInvoker(test, mockServer.getUrl());
        } finally {
            handler.setUsePartialCustomMixedSuffix(false);
        }
    }

    @Test
    public void testRESTVersionManipSuffixStripIncrement() throws Exception {
        String test = getDefaultTestLocation("rest-version-manip-suffix-strip-increment");
        runLikeInvoker(test, mockServer.getUrl());
    }

    @Test
    public void testRESTVersionManipBOMREST() throws Exception {
        String test = getDefaultTestLocation("rest-version-manip-bomrest");
        runLikeInvoker(test, mockServer.getUrl());
    }

    @Test
    public void testRESTVersionManipRESTBOM() throws Exception {
        String test = getDefaultTestLocation("rest-version-manip-restbom");
        runLikeInvoker(test, mockServer.getUrl());
    }

    @Test
    public void testRESTVersionManipRESTBOMAutodetectBom() throws Exception {
        String test = getDefaultTestLocation("rest-version-manip-restbom-autodetectbom");
        runLikeInvoker(test, mockServer.getUrl());
    }

    @Test
    public void testRESTVersionManipRESTPlugin() throws Exception {
        String test = getDefaultTestLocation("rest-version-manip-plugin-rest");
        runLikeInvoker(test, mockServer.getUrl());
    }

    @Test
    public void testRESTVersionManipRESTBOMPlugin() throws Exception {
        String test = getDefaultTestLocation("rest-version-manip-plugin-restbom");
        runLikeInvoker(test, mockServer.getUrl());
    }

    @Test
    public void testRESTVersionManipBOMRESTPlugin() throws Exception {
        String test = getDefaultTestLocation("rest-version-manip-plugin-bomrest");
        runLikeInvoker(test, mockServer.getUrl());
    }

    @Test
    public void testRESTVersionManipOverride() throws Exception {
        String test = getDefaultTestLocation("rest-version-manip-only-override");
        runLikeInvoker(test, mockServer.getUrl());
    }

    @Test
    public void testRESTVersionManipRelocate() throws Exception {
        String test = getDefaultTestLocation("rest-version-manip-only-relocate");
        runLikeInvoker(test, mockServer.getUrl());
    }

    @Test
    public void testRESTOverrideTransitiveDependencyManagement() throws Exception {
        String test = getDefaultTestLocation("rest-override-transitive-dependency-management");
        runLikeInvoker(test, mockServer.getUrl());
    }

    @Test
    public void testRESTHeaders() throws Exception {
        String test = getDefaultTestLocation("rest-headers");
        runLikeInvoker(test, mockServer.getUrl());
    }

    @Test
    public void testRESTVersionManipAlignmentDisabled() throws Exception {
        String test = getDefaultTestLocation("rest-version-manip-alignment-disabled");
        runLikeInvoker(test, mockServer.getUrl());
    }
}
