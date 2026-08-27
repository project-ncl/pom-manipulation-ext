/*
 * Copyright © 2012 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.mavenmanipulator.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.io.FileUtils;
import org.jboss.pnc.mavenmanipulator.common.exception.ManipulationException;
import org.jboss.pnc.mavenmanipulator.common.json.PME;
import org.jboss.pnc.mavenmanipulator.common.util.JSONUtils;
import org.jboss.pnc.mavenmanipulator.core.fixture.PlexusTestRunner;
import org.jboss.pnc.mavenmanipulator.core.fixture.TestUtils;
import org.jboss.pnc.mavenmanipulator.core.impl.Manipulator;
import org.jboss.pnc.mavenmanipulator.core.state.CommonState;
import org.jboss.pnc.mavenmanipulator.core.state.DependencyState;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

@RunWith(PlexusTestRunner.class)
@Named
public class ManipulationManagerTest {
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Rule
    public final SystemOutRule systemRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @SuppressWarnings("unused")
    @Inject
    private Map<String, Manipulator> manipulators;

    @Test
    public void testListManipulators() {
        assertNotNull(manipulators);

        for (final Map.Entry<String, Manipulator> entry : manipulators.entrySet()) {
            assertTrue(entry.getValue().getExecutionIndex() > 0 && entry.getValue().getExecutionIndex() < 100);
        }
    }

    @Test
    public void testSessionStartupMessage() {
        new ManipulationSession();
        assertTrue(systemRule.getLog().contains("Running Maven Manipulation Extension (PME)"));
    }

    @Test
    public void testDepOverrideAndStrictPropertyValidation()
            throws IOException, ManipulationException {
        final File projectroot = folder.newFile();
        final File resource = TestUtils.resolveFileResource("", "pom-variables.xml");
        FileUtils.copyFile(resource, projectroot);
        Properties p = new Properties();
        p.put(DependencyState.DEPENDENCY_OVERRIDE_PREFIX + ".com.fasterxml.jackson.dataformat:*@*", "");
        p.put(CommonState.DEPENDENCY_PROPERTY_VALIDATION, "true");

        CommonState commonState = TestUtils.createSessionAndManager(p, projectroot)
                .getSession()
                .getState(CommonState.class);

        assertTrue(
                systemRule.getLog().contains("Disabling strictPropertyValidation as dependencyOverrides are enabled"));
        assertEquals(0, (int) commonState.getStrictDependencyPluginPropertyValidation());
    }

    @Test
    public void testReportJsonUnchanged_disabledByDefault()
            throws IOException, ManipulationException {
        final File root = folder.newFolder();
        final File base = TestUtils.resolveFileResource("groovy-project-removal", "");
        FileUtils.copyDirectory(base, root);
        final File projectRoot = new File(root, "pom.xml");

        // No versionIncrementalSuffix → nothing changes; flag not set → default false
        Properties p = new Properties();
        TestUtils.SMContainer smc = TestUtils.createSessionAndManager(p, projectRoot);
        smc.getManager().scanAndApply(smc.getSession());

        final File reportFile = new File(root, "target/" + ManipulationManager.REPORT_JSON_DEFAULT);
        assertFalse("JSON report must NOT be written when reportJsonUnchanged is false", reportFile.exists());
    }

    @Test
    public void testReportJsonUnchanged_writesFileWhenEnabled()
            throws IOException, ManipulationException {
        final File root = folder.newFolder();
        final File base = TestUtils.resolveFileResource("groovy-project-removal", "");
        FileUtils.copyDirectory(base, root);
        final File projectRoot = new File(root, "pom.xml");

        // No versionIncrementalSuffix → nothing changes; flag enabled
        Properties p = new Properties();
        p.setProperty(ManipulationManager.REPORT_JSON_UNCHANGED, "true");

        TestUtils.SMContainer smc = TestUtils.createSessionAndManager(p, projectRoot);
        smc.getManager().scanAndApply(smc.getSession());

        final File reportFile = new File(root, "target/" + ManipulationManager.REPORT_JSON_DEFAULT);
        assertTrue("JSON report must be written when reportJsonUnchanged is true", reportFile.exists());

        final PME pme = JSONUtils.fileToJSON(reportFile);
        assertEquals("com.example", pme.getGav().getGroupId());
        assertEquals("groovy-project-removal", pme.getGav().getArtifactId());
        assertEquals("1.0.0", pme.getGav().getVersion());
        assertEquals("com.example:groovy-project-removal:1.0.0", pme.getGav().getOriginalGAV());
        assertTrue("modules list must be empty for unchanged project", pme.getModules().isEmpty());
    }

    @Test
    public void testReportJsonUnchanged_resolvesVersionProperty()
            throws IOException, ManipulationException {
        final File root = folder.newFolder();
        final File resource = TestUtils.resolveFileResource("", "pom-version-property.xml");
        final File projectRoot = new File(root, "pom.xml");
        FileUtils.copyFile(resource, projectRoot);

        // No versionIncrementalSuffix → nothing changes; flag enabled
        Properties p = new Properties();
        p.setProperty(ManipulationManager.REPORT_JSON_UNCHANGED, "true");

        TestUtils.SMContainer smc = TestUtils.createSessionAndManager(p, projectRoot);
        smc.getManager().scanAndApply(smc.getSession());

        final File reportFile = new File(root, "target/" + ManipulationManager.REPORT_JSON_DEFAULT);
        assertTrue("JSON report must be written when reportJsonUnchanged is true", reportFile.exists());

        final PME pme = JSONUtils.fileToJSON(reportFile);
        // ${myVersion} must be resolved to its declared value "2.0.0", not left as a literal expression
        assertEquals("com.example", pme.getGav().getGroupId());
        assertEquals("version-property-project", pme.getGav().getArtifactId());
        assertEquals("2.0.0", pme.getGav().getVersion());
        assertEquals("com.example:version-property-project:2.0.0", pme.getGav().getOriginalGAV());
    }

    @Test
    public void testRewriteChanged()
            throws IOException, ManipulationException {
        final File root = folder.newFolder();
        final File base = TestUtils.resolveFileResource("groovy-project-removal", "");
        FileUtils.copyDirectory(base, root);
        final File projectRoot = new File(root, "pom.xml");
        final File projectRootBackup = new File(root, "pom.backup.xml");
        FileUtils.copyFile(projectRoot, projectRootBackup);

        Properties p = new Properties();
        p.setProperty("versionIncrementalSuffix", "rebuild");
        p.put(ManipulationManager.REWRITE_CHANGED, "false");

        TestUtils.SMContainer smc = TestUtils.createSessionAndManager(p, projectRoot);
        smc.getManager().scanAndApply(smc.getSession());

        assertTrue(systemRule.getLog().contains("Maven-Manipulation-Extension: Finished"));
        assertTrue(FileUtils.contentEquals(projectRoot, projectRootBackup));
        assertFalse(systemRule.getLog().contains("Maven-Manipulation-Extension: Rewrite changed"));
    }
}
