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
package org.jboss.pnc.mavenmanipulator.io.rest;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;

import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectVersionRef;
import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BMUnitRunner.class)
public class HttpEndpointTest extends HttpHeaderHeaderTest {
    static {
        //        System.setProperty( "org.jboss.byteman.verbose", "true" );
        //        System.setProperty( "org.jboss.byteman.debug", "true" );
    }

    @Test
    @BMRule(
            name = "check-duplicate-endpoint",
            targetClass = "DefaultTranslator$Task",
            targetMethod = "executeTranslate()",
            targetLocation = "AT ENTRY",
            condition = "$this.endpointUrl.contains(\"lookup/gavs/reports/lookup/gavs\")",
            action = "throw new RuntimeException()")
    public void testVerifyEndpoint() {
        testResponseStart = "<html><body><h1>504 Gateway Time-out</h1>\n" +
                "The server didn't respond in time.\n" +
                "</body></html>";
        testResponseEnd = null;

        List<ProjectVersionRef> gavs = Arrays.asList(
                new SimpleProjectVersionRef("com.example", "example", "1.0"),
                new SimpleProjectVersionRef("com.example", "example-one", "1.1"),
                new SimpleProjectVersionRef("com.example", "example-two", "1.0"),
                new SimpleProjectVersionRef("com.example", "example-three", "1.1"),
                new SimpleProjectVersionRef("com.example", "example-four", "1.0"),
                new SimpleProjectVersionRef("com.example", "example-five", "1.1"),
                new SimpleProjectVersionRef("com.example", "example-six", "1.0"),
                new SimpleProjectVersionRef("com.example", "example-seven", "1.1"));

        try {
            versionTranslator.lookupVersions(gavs);
            fail("Failed to throw RestException.");
        } catch (RestException ex) {
            assertTrue(systemOutRule.getLog().contains("504 Gateway Time-out The server didn't respond in time"));
        }
    }
}
