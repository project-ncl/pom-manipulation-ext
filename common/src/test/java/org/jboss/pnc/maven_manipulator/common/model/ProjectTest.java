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
package org.jboss.pnc.maven_manipulator.common.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.apache.maven.model.Model;
import org.jboss.pnc.maven_manipulator.common.ManipulationException;
import org.junit.Test;

public class ProjectTest {
    @Test(expected = ManipulationException.class)
    public void verifyProjectValidation() throws ManipulationException {
        final Model m = new Model();
        m.setGroupId("org.foo");
        m.setArtifactId("bar");
        new Project(m);
    }

    @Test(expected = ManipulationException.class)
    public void createProjectWithNullModel() throws ManipulationException {
        new Project(null, null);
    }

    @Test
    public void verifyProjectComparison() throws ManipulationException {
        Model m1 = new Model();
        m1.setGroupId("org.foo");
        m1.setArtifactId("bar");
        m1.setVersion("1.0");
        Project one = new Project(m1);

        Model m2 = new Model();
        m2.setGroupId("org.foo");
        m2.setArtifactId("bar");
        m2.setVersion("1.0");
        Project two = new Project(m2);

        assertEquals(one, two);

        m2 = new Model();
        m2.setGroupId("org.foo");
        m2.setArtifactId("bar");
        m2.setVersion("1.0.0");
        two = new Project(m2);

        assertEquals(one, two);

        Model m3 = new Model();
        m3.setGroupId("org.foo");
        m3.setArtifactId("bar");
        m3.setVersion("1.0.rebuild-1");
        Project three = new Project(m3);

        assertNotEquals(one, three);
    }
}