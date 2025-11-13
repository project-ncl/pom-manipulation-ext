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
package org.jboss.pnc.mavenmanipulator.common.util;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.maven.model.Model;
import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.jboss.pnc.mavenmanipulator.common.ManipulationUncheckedException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.runner.RunWith;

@RunWith(BMUnitRunner.class)
public class ManifestUtilsTest {
    @Rule
    public final SystemOutRule systemRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Test
    public void testNoClass() {
        try {
            ManifestUtils.getManifestInformation(null);
            fail("No exception thrown");
        } catch (ManipulationUncheckedException e) {
            assertTrue(e.getMessage().contains("No target specified"));
        }
    }

    @Test
    public void testThisClass() {
        ManifestUtils.getManifestInformation(ManifestUtilsTest.class);
        assertTrue(
                systemRule.getLog()
                        .contains(
                                "Unable to retrieve manifest for class "
                                        + "org.jboss.pnc.mavenmanipulator.common.util.ManifestUtilsTest as "
                                        + "location is a directory not a jar"));
    }

    @Test
    public void testThirdPartyClass() {
        String result = ManifestUtils.getManifestInformation(Model.class);
        assertTrue(result.matches("3.[.\\d+]+ [(] SHA: null [)]"));
    }

    @Test
    public void testJarClass() {
        String m = ManifestUtils.getManifestInformation(Test.class);
        assertTrue(m.contains("4"));
    }

    // If this is used from Gradle (e.g. GME) Gradle might create a test-kit jar of the form
    // /tmp/.gradle-test-kit-rnc/caches/jars-8/a16e8d7cbf89ad68270cbc6dd442f546/main.jar which
    // has no manifest.
    @Test
    @BMRule(
            name = "return-empty-manifest",
            targetClass = "JarInputStream",
            targetMethod = "getManifest()",
            targetLocation = "AT ENTRY",
            action = "RETURN null")
    public void testNoManifestJar() {
        String result = ManifestUtils.getManifestInformation(Model.class);

        assertTrue(result.isEmpty());
    }

    @Test
    @BMRule(
            name = "jar-stream",
            targetClass = "URL",
            targetMethod = "openStream()",
            targetLocation = "AT ENTRY",
            action = "throw new IOException()")
    public void testJarStreamFailure() {
        try {
            ManifestUtils.getManifestInformation(Model.class);
            fail("No exception thrown");
        } catch (ManipulationUncheckedException e) {
            assertTrue(e.getMessage().contains("Error retrieving information"));
        }
    }
}
