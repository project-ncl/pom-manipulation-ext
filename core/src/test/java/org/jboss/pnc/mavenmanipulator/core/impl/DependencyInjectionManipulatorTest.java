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
package org.jboss.pnc.mavenmanipulator.core.impl;

import static org.jboss.pnc.mavenmanipulator.core.fixture.TestUtils.createSession;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.commonjava.atlas.maven.ident.ref.InvalidRefException;
import org.jboss.pnc.mavenmanipulator.common.ManipulationException;
import org.jboss.pnc.mavenmanipulator.common.model.Project;
import org.jboss.pnc.mavenmanipulator.core.ManipulationSession;
import org.junit.Test;

public class DependencyInjectionManipulatorTest extends DependencyInjectionManipulator {

    private DependencyInjectionManipulator manipulator;
    private Properties sessionProperties;
    private ManipulationSession session;

    void initialize(String dependencyInjection) throws ManipulationException {
        sessionProperties = new Properties();
        sessionProperties.put("dependencyInjection", dependencyInjection);

        session = createSession(sessionProperties);

        manipulator = new DependencyInjectionManipulator();
        manipulator.init(session);
    }

    static Model model(boolean initDepMgmt) {
        Model model = new Model();
        model.setGroupId(UUID.randomUUID().toString());
        model.setArtifactId(UUID.randomUUID().toString());
        model.setVersion("1.0");
        if (initDepMgmt) {
            model.setDependencyManagement(new DependencyManagement());
        }
        return model;
    }

    static Project newProject(boolean initDepMgmt) throws ManipulationException {
        Project project = new Project(model(initDepMgmt));
        project.setInheritanceRoot(true);
        return project;
    }

    @Test
    public void testDependencyInjectedWithoutDepMgmt() throws ManipulationException {
        testDependencyInjected3Part(false);
    }

    @Test
    public void testDependencyInjectedWithDepMgmt() throws ManipulationException {
        testDependencyInjected3Part(true);
    }

    private void testDependencyInjected3Part(boolean initDepMgmt) throws ManipulationException {
        Dependency managed = inject("grp1:art1:ver1", initDepMgmt);
        assertEquals("grp1", managed.getGroupId());
        assertEquals("art1", managed.getArtifactId());
        assertEquals("ver1", managed.getVersion());
    }

    @Test
    public void testDependencyInjected4Part() throws ManipulationException {
        Dependency managed = inject("grp1:art1:typ1:ver1");
        assertEquals("grp1", managed.getGroupId());
        assertEquals("art1", managed.getArtifactId());
        assertEquals("typ1", managed.getType());
        assertEquals("ver1", managed.getVersion());
    }

    @Test
    public void testDependencyInjected5Part() throws ManipulationException {
        Dependency managed = inject("grp1:art1:typ1:cls1:ver1");
        assertEquals("grp1", managed.getGroupId());
        assertEquals("art1", managed.getArtifactId());
        assertEquals("typ1", managed.getType());
        assertEquals("cls1", managed.getClassifier());
        assertEquals("ver1", managed.getVersion());
    }

    @Test
    public void testDependencyInjected6Part() throws ManipulationException {
        Dependency managed = inject("grp1:art1:typ1:cls1:ver1:scp1");
        assertEquals("grp1", managed.getGroupId());
        assertEquals("art1", managed.getArtifactId());
        assertEquals("typ1", managed.getType());
        assertEquals("cls1", managed.getClassifier());
        assertEquals("ver1", managed.getVersion());
        assertEquals("scp1", managed.getScope());
    }

    @Test
    public void testDependencyInjectedEmptyNull() throws ManipulationException {
        Dependency managed = inject("grp1:art1:::ver1:scp1");
        assertEquals("grp1", managed.getGroupId());
        assertEquals("art1", managed.getArtifactId());
        assertNull(managed.getType());
        assertNull(managed.getClassifier());
        assertEquals("ver1", managed.getVersion());
        assertEquals("scp1", managed.getScope());
    }

    @Test
    public void testDependencyInjectedInvalid() {
        assertThrows(InvalidRefException.class, () -> inject("grp1:art1")); // too short
        assertThrows(InvalidRefException.class, () -> inject(":art1:ver1")); // missing groupId
        assertThrows(InvalidRefException.class, () -> inject("grp1::ver1")); // missing artifactId
        assertThrows(InvalidRefException.class, () -> inject("grp1:art1::::foo1")); // missing version
        assertThrows(InvalidRefException.class, () -> inject("grp1:art1:typ1:cls1:ver1:scp1:foo1")); // too long
    }

    private Dependency inject(String dependencyInjection) throws ManipulationException {
        return inject(dependencyInjection, false);
    }

    private Dependency inject(String dependencyInjection, boolean initDepMgmt) throws ManipulationException {
        initialize(dependencyInjection);

        List<Project> projects = Arrays.asList(newProject(initDepMgmt));
        Set<Project> updatedProjects = manipulator.applyChanges(projects);
        assertEquals(1, updatedProjects.size());

        Model model = updatedProjects.iterator().next().getModel();
        DependencyManagement mgmt = model.getDependencyManagement();
        assertEquals(1, mgmt.getDependencies().size());

        return mgmt.getDependencies().get(0);
    }
}
