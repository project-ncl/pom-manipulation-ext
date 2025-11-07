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
package org.jboss.pnc.maven_manipulator.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.maven.model.Model;
import org.jboss.pnc.maven_manipulator.common.model.Project;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;

public class PomIOTest {
    private static final String filename = "pom.xml";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    private PomIO pomIO;

    @Before
    public void setup() {
        pomIO = new PomIO();
    }

    @Test
    public void testRoundTripPOMs()
            throws Exception {
        URL resource = PomIOTest.class.getResource(filename);
        assertNotNull(resource);
        File pom = new File(resource.getFile());
        assertTrue(pom.exists());

        File targetFile = folder.newFile("target.xml");
        FileUtils.copyFile(pom, targetFile);

        List<Project> projects = pomIO.parseProject(targetFile);

        assertNull(projects.get(0).getModel().getModelEncoding());

        // We don't want this to be the execution root so that it doesn't add "Modified by" which breaks the comparison
        FieldUtils.writeDeclaredField(projects.get(0), "executionRoot", false, true);
        HashSet<Project> changed = new HashSet<>(projects);
        pomIO.rewritePOMs(changed);

        assertTrue(FileUtils.contentEqualsIgnoreEOL(pom, targetFile, StandardCharsets.UTF_8.toString()));
        assertTrue(FileUtils.contentEquals(targetFile, pom));
    }

    @Test
    public void testRewritePOMs()
            throws Exception {
        URL resource = PomIOTest.class.getResource(filename);
        assertNotNull(resource);
        File pom = new File(resource.getFile());
        assertTrue(pom.exists());

        File targetFile = folder.newFile("target.xml");
        FileUtils.writeStringToFile(
                targetFile,
                FileUtils.readFileToString(pom, StandardCharsets.UTF_8)
                        .replaceAll("dospom", "newdospom"),
                StandardCharsets.UTF_8);
        FileUtils.copyFile(pom, targetFile);

        List<Project> projects = pomIO.parseProject(targetFile);
        Model model = projects.get(0).getModel();
        model.setGroupId("org.jboss.pnc.maven_manipulator.versioning.test");
        model.setArtifactId("dospom");
        model.setVersion("1.0");
        model.setPackaging("pom");
        model.setModelVersion("4.0.0");

        Project p = new Project(targetFile, model);
        HashSet<Project> changed = new HashSet<>();
        changed.add(p);
        pomIO.rewritePOMs(changed);

        assertTrue(FileUtils.contentEqualsIgnoreEOL(pom, targetFile, StandardCharsets.UTF_8.toString()));
        assertTrue(FileUtils.contentEquals(targetFile, pom));
    }

    @Test
    public void testListRemovalPOMs()
            throws Exception {
        URL resource = PomIOTest.class.getResource(filename);
        assertNotNull(resource);
        File pom = new File(resource.getFile());
        assertTrue(pom.exists());

        File targetFile = folder.newFile("target.xml");
        FileUtils.copyFile(pom, targetFile);

        List<Project> projects = pomIO.parseProject(targetFile);
        Project p = projects.get(0);
        p.getModel().getRepositories().clear();

        HashSet<Project> changed = new HashSet<>();
        changed.add(p);

        pomIO.rewritePOMs(changed);

        p = pomIO.parseProject(targetFile).get(0);

        assertEquals(0, p.getModel().getRepositories().size());
    }

    @Test
    public void testWriteModel()
            throws Exception {
        // Thanks to http://www.buildmystring.com
        String sb = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd\" xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
                + "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                + "  <modelVersion>4.0.0</modelVersion>\n"
                + "  <groupId>org.jboss.pnc.maven_manipulator.versioning.test</groupId>\n"
                + "  <artifactId>dospom</artifactId>\n" + "  <version>1.0</version>\n"
                + "  <packaging>pom</packaging>\n" + "</project>\n";

        URL resource = PomIOTest.class.getResource(filename);
        assertNotNull(resource);
        File pom = new File(resource.getFile());
        assertTrue(pom.exists());

        File targetFile = folder.newFile("target.xml");

        Model model = new Model();
        model.setGroupId("org.jboss.pnc.maven_manipulator.versioning.test");
        model.setArtifactId("dospom");
        model.setVersion("1.0");
        model.setPackaging("pom");
        model.setModelVersion("4.0.0");

        pomIO.writeModel(model, targetFile);
        assertTrue(targetFile.exists());
        assertEquals(sb, FileUtils.readFileToString(targetFile, StandardCharsets.UTF_8));
    }

    @Test
    public void testAddModifiedBy()
            throws Exception {
        URL resource = PomIOTest.class.getResource(filename);
        assertNotNull(resource);
        File pom = new File(resource.getFile());
        assertTrue(pom.exists());

        File targetFile = folder.newFile("target.xml");
        FileUtils.copyFile(pom, targetFile);

        Project project = pomIO.parseProject(targetFile).get(0);
        project.setExecutionRoot();

        HashSet<Project> changed = new HashSet<>();
        changed.add(project);
        pomIO.rewritePOMs(changed);

        String s = FileUtils.readFileToString(targetFile, StandardCharsets.UTF_8);
        assertEquals(1, StringUtils.countMatches(s, "Modified by POM Manipulation Extension"));

        pomIO.rewritePOMs(changed);
        s = FileUtils.readFileToString(targetFile, StandardCharsets.UTF_8);
        assertEquals(1, StringUtils.countMatches(s, "Modified by POM Manipulation Extension"));
    }
}
