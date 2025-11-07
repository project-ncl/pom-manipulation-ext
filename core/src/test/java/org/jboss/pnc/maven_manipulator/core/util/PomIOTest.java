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
package org.jboss.pnc.maven_manipulator.core.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.jboss.pnc.maven_manipulator.common.model.Project;
import org.jboss.pnc.maven_manipulator.core.fixture.TestUtils;
import org.jboss.pnc.maven_manipulator.io.PomIO;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;

public class PomIOTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Test
    public void testVerifyParsePomTemplatesDefault() throws Exception {
        final File projectroot = folder.newFile();
        final File resource = TestUtils.resolveFileResource("", "pom-variables.xml");
        assertNotNull(resource);
        FileUtils.copyFile(resource, projectroot);

        PomIO pomIO = new PomIO(TestUtils.createSessionAndManager(new Properties(), projectroot).getSession());
        List<Project> projects = pomIO.parseProject(projectroot);
        assertEquals(1, projects.size());
        assertEquals("${one}", projects.get(0).getGroupId());
    }

    @Test
    public void testVerifyParsePomTemplatesFalse() throws Exception {
        final File projectroot = folder.newFile();
        final File resource = TestUtils.resolveFileResource("", "pom-variables.xml");
        assertNotNull(resource);
        FileUtils.copyFile(resource, projectroot);

        Properties p = new Properties();
        p.put(PomIO.PARSE_POM_TEMPLATES, "false");
        PomIO pomIO = new PomIO(TestUtils.createSessionAndManager(p, projectroot).getSession());
        List<Project> projects = pomIO.parseProject(projectroot);
        assertEquals(0, projects.size());
        assertTrue(systemOutRule.getLog().contains("PomPeek - Could not peek at POM coordinate for"));
    }

    @Test
    public void testVerifyParsePom() throws Exception {
        final File projectroot = folder.newFile();
        final File resource = TestUtils.resolveFileResource("", "pom-quarkus.xml");
        assertNotNull(resource);
        FileUtils.copyFile(resource, projectroot);

        Properties p = new Properties();
        PomIO pomIO = new PomIO(TestUtils.createSessionAndManager(p, projectroot).getSession());
        List<Project> projects = pomIO.parseProject(projectroot);
        assertEquals(1, projects.size());
    }
}
