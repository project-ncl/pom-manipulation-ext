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

import static org.jboss.pnc.maven_manipulator.core.fixture.TestUtils.INTEGRATION_TEST;
import static org.jboss.pnc.maven_manipulator.core.fixture.TestUtils.ROOT_DIRECTORY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Dependency;
import org.commonjava.atlas.maven.ident.ref.ArtifactRef;
import org.jboss.pnc.maven_manipulator.common.model.Project;
import org.jboss.pnc.maven_manipulator.common.model.SimpleScopedArtifactRef;
import org.jboss.pnc.maven_manipulator.core.ManipulationSession;
import org.jboss.pnc.maven_manipulator.core.fixture.TestUtils;
import org.jboss.pnc.maven_manipulator.io.PomIO;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ProjectInheritanceTest {
    private static final String RESOURCE_BASE = "properties/";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testVerifyInheritance() throws Exception {
        // Locate the PME project pom file. Use that to verify inheritance tracking.
        final File projectroot = Paths.get(ROOT_DIRECTORY.toString(), "pom.xml").toFile();

        PomIO pomIO = new PomIO();
        List<Project> projects = pomIO.parseProject(projectroot);
        for (Project p : projects) {
            if (!p.getPom().equals(projectroot)) {
                assertEquals(p.getProjectParent().getPom(), projectroot);
            }
        }
    }

    @Test
    public void testVerifyInheritanceMultiple() throws Exception {
        // Locate the PME project pom file. Use that to verify inheritance tracking.
        final File projectroot = Paths.get(INTEGRATION_TEST.toString(), "src", "it", "project-inheritance", "pom.xml")
                .toFile();

        PomIO pomIO = new PomIO();

        List<Project> projects = pomIO.parseProject(projectroot);

        for (int i = 0; i <= 3; i++) {
            if (i == 0) {
                // First project should be root
                assertNull(projects.get(i).getProjectParent());
                assertEquals(projects.get(i).getPom(), projectroot);
                assertEquals(1, projects.get(i).getInheritedList().size());
            } else if (i == 1) {
                List<Project> inherited = projects.get(i).getInheritedList();
                assertEquals(2, inherited.size());
                assertEquals(inherited.get(1).getProjectParent().getPom(), projectroot);
            } else if (i == 2) {
                List<Project> inherited = projects.get(i).getInheritedList();
                assertEquals(3, inherited.size());
                assertEquals(inherited.get(0).getPom(), projectroot);
            }
        }
    }

    @Test
    public void testVerifyExecutionRoot() throws Exception {
        // Locate the PME project pom file. Use that to verify inheritance tracking.
        final File projectroot = Paths
                .get(INTEGRATION_TEST.toString(), "src", "it", "project-inheritance", "common", "pom.xml")
                .toFile();

        PomIO pomIO = new PomIO();

        List<Project> projects = pomIO.parseProject(projectroot);

        for (Project p : projects) {
            if (p.getKey().toString().equals("io.apiman:apiman-common:1.2.7-SNAPSHOT")) {
                assertTrue(p.isExecutionRoot());
                assertTrue(p.isInheritanceRoot());
            } else {
                assertFalse(p.isExecutionRoot());
                assertFalse(p.isInheritanceRoot());
            }
        }

    }

    @Test
    public void testVerifyRelativeExecutionRoot() throws Exception {
        final File projectRoot = new File(System.getProperty("user.dir"), "pom.xml");

        Path root = Paths.get(projectRoot.getParent());
        Path absolute = Paths.get(projectRoot.toString());
        Path relative = root.relativize(absolute);

        PomIO pomIO = new PomIO();

        List<Project> projects = pomIO.parseProject(relative.toFile());

        assertEquals(1, projects.size());
        assertTrue(projects.get(0).isExecutionRoot());
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void testVerifyCanonicalExecutionRoot() throws Exception {
        final File projectroot = TestUtils.resolveFileResource(RESOURCE_BASE, "infinispan-bom-8.2.0.Final.pom");
        final File newFolder = temporaryFolder.newFolder();
        final File folder1 = new File(newFolder, "One");
        final File folder2 = new File(newFolder, "Two");
        folder1.mkdir();
        folder2.mkdir();
        final File targetPom = new File(folder1, "target.pom");
        final File dummyPom = new File(folder2, "target.pom");

        FileUtils.copyFile(projectroot, targetPom);
        Files.createSymbolicLink(dummyPom.toPath(), targetPom.toPath());

        PomIO pomIO = new PomIO();
        List<Project> projects = pomIO.parseProject(dummyPom);

        assertEquals(1, projects.size());
        assertTrue(projects.get(0).isExecutionRoot());
    }

    @Test
    public void testVerifyInheritanceReversedMultiple() throws Exception {
        // Locate the PME project pom file. Use that to verify inheritance tracking.
        final File projectroot = Paths.get(INTEGRATION_TEST.toString(), "src", "it", "project-inheritance", "pom.xml")
                .toFile();

        PomIO pomIO = new PomIO();

        List<Project> projects = pomIO.parseProject(projectroot);

        for (int i = 0; i <= 3; i++) {
            if (i == 0) {
                // First project should be root
                assertNull(projects.get(i).getProjectParent());
                assertEquals(projects.get(i).getPom(), projectroot);
                assertEquals(1, projects.get(i).getReverseInheritedList().size());
            } else if (i == 1) {
                List<Project> inherited = projects.get(i).getReverseInheritedList();
                assertEquals(2, inherited.size());
                assertEquals(inherited.get(0).getProjectParent().getPom(), projectroot);
            } else if (i == 2) {
                List<Project> inherited = projects.get(i).getReverseInheritedList();
                assertEquals(3, inherited.size());
                assertEquals(inherited.get(2).getPom(), projectroot);
            }
        }
    }

    @Test
    public void testVerifyProjectVersion() throws Exception {
        final ManipulationSession session = new ManipulationSession();

        final File projectroot = Paths.get(INTEGRATION_TEST.toString(), "src", "it", "project-inheritance", "pom.xml")
                .toFile();

        PomIO pomIO = new PomIO();
        List<Project> projects = pomIO.parseProject(projectroot);
        for (Project p : projects) {
            if (p.getPom().equals(projectroot)) {
                Map<ArtifactRef, Dependency> deps = p.getResolvedManagedDependencies(session);
                for (ArtifactRef a : deps.keySet()) {
                    assertFalse(a.getVersionString().contains("project.version"));
                }
                deps = p.getResolvedDependencies(session);
                assertEquals(1, deps.size());
                for (ArtifactRef a : deps.keySet()) {
                    assertFalse(a.getVersionString().contains("project.version"));
                }
                assertFalse(deps.containsKey(SimpleScopedArtifactRef.parse("org.mockito:mockito-all:jar:*")));
                deps = p.getAllResolvedDependencies(session);
                assertEquals(3, deps.size());
                for (ArtifactRef a : deps.keySet()) {
                    assertFalse(a.getVersionString().contains("project.version"));
                    if (a.getGroupId().equals("org.mockito")) {
                        assertTrue(a.getVersionString().contains("*"));
                    }
                }
                assertTrue(deps.containsKey(SimpleScopedArtifactRef.parse("io.swagger:swagger-annotations:jar:*")));
                assertTrue(deps.containsKey(SimpleScopedArtifactRef.parse("org.mockito:mockito-all:jar:*", "test")));
            }
        }
    }
}
