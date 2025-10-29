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
package org.commonjava.maven.ext.integrationtest;

import static org.commonjava.maven.ext.integrationtest.ITestUtils.DEFAULT_MVN_PARAMS;
import static org.commonjava.maven.ext.integrationtest.ITestUtils.getDefaultTestLocation;
import static org.commonjava.maven.ext.integrationtest.ITestUtils.runLikeInvoker;
import static org.commonjava.maven.ext.integrationtest.ITestUtils.runMaven;

import java.io.File;

import org.commonjava.maven.ext.io.rest.handler.AddSuffixJettyHandler;
import org.commonjava.maven.ext.io.rest.rule.MockServer;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

public class CircularIntegrationTest {
    private final AddSuffixJettyHandler handler = new AddSuffixJettyHandler("/", null);

    @Rule
    public MockServer mockServer = new MockServer(handler);

    @BeforeClass
    public static void setUp()
            throws Exception {
        runMaven(
                "install",
                DEFAULT_MVN_PARAMS,
                ITestUtils.IT_LOCATION + File.separator + "circular-dependencies-test-parent");
    }

    @Before
    public void before() {
        handler.setSuffix(AddSuffixJettyHandler.DEFAULT_SUFFIX);
    }

    @Test
    public void testCircular() throws Exception {
        String test = getDefaultTestLocation("circular-dependencies-test-second");
        runLikeInvoker(test, mockServer.getUrl());
    }
}
