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
package org.jboss.pnc.mavenmanipulator.integrationtest;

import static org.jboss.pnc.mavenmanipulator.integrationtest.DefaultCliIntegrationTest.setupExists;
import static org.jboss.pnc.mavenmanipulator.integrationtest.ITestUtils.DEFAULT_MVN_PARAMS;
import static org.jboss.pnc.mavenmanipulator.integrationtest.ITestUtils.getDefaultTestLocation;
import static org.jboss.pnc.mavenmanipulator.integrationtest.ITestUtils.runLikeInvoker;
import static org.jboss.pnc.mavenmanipulator.integrationtest.ITestUtils.runMaven;

import java.io.File;

import org.jboss.pnc.mavenmanipulator.io.rest.handler.StaticResourceHandler;
import org.jboss.pnc.mavenmanipulator.io.rest.rule.MockServer;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GroovyIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultCliIntegrationTest.class);

    private final StaticResourceHandler handler = new StaticResourceHandler("src/it/setup/depMgmt2/POMModifier.groovy");

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

    @Test
    public void testRunGroovyScript() throws Exception {
        String test = getDefaultTestLocation("groovy-manipulator-first-http");
        runLikeInvoker(test, mockServer.getUrl() + "/src/it/setup/depMgmt2/POMModifier.groovy");
    }
}
