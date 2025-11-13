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
package org.jboss.pnc.mavenmanipulator.core.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.Properties;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.commonjava.atlas.maven.ident.ref.ArtifactRef;
import org.jboss.pnc.mavenmanipulator.common.model.Project;
import org.jboss.pnc.mavenmanipulator.common.util.PropertyInterpolator;
import org.jboss.pnc.mavenmanipulator.core.ManipulationSession;
import org.jboss.pnc.mavenmanipulator.core.fixture.TestUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PropertyInterpolatorTest {
    private static final String RESOURCE_BASE = "properties/";

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void testInteropolateProperties() throws Exception {
        final Model model = TestUtils.resolveModelResource(RESOURCE_BASE, "infinispan-bom-8.2.0.Final.pom");

        Project p = new Project(model);

        Properties props = p.getModel().getProperties();
        boolean containsProperty = false;
        for (Object o : props.values()) {
            if (((String) o).contains("${")) {
                containsProperty = true;
                break;
            }
        }
        assertTrue(containsProperty);
        PropertyInterpolator pi = new PropertyInterpolator(props, p);
        assertEquals("5.0.4.Final", pi.interp("${version.hibernate.osgi}"));
    }

    @Test
    public void testInteropolateDependencies() throws Exception {
        final Model model = TestUtils.resolveModelResource(RESOURCE_BASE, "infinispan-bom-8.2.0.Final.pom");

        Project project = new Project(model);
        PropertyInterpolator pi = new PropertyInterpolator(project.getModel().getProperties(), project);

        String nonInterp = "", interp = "";
        // Explicitly calling the non-resolved model dependencies...
        for (Dependency d : project.getModel().getDependencyManagement().getDependencies()) {
            nonInterp += (d.getGroupId().equals("${project.groupId}") ? project.getGroupId() : d.getGroupId()) + ":"
                    + (d.getArtifactId().equals("${project.artifactId}") ? project.getArtifactId() : d.getArtifactId())
                    + System.lineSeparator();

            interp += pi.interp(d.getGroupId().equals("${project.groupId}") ? project.getGroupId() : d.getGroupId())
                    + ":" + pi.interp(
                            d.getArtifactId().equals("${project.artifactId}") ? project.getArtifactId()
                                    : d.getArtifactId())
                    + System.lineSeparator();

        }
        assertTrue(nonInterp.contains("${"));
        assertFalse(interp.contains("${"));
    }

    @Test
    public void testResolveProjectDependencies() throws Exception {
        final Model model = TestUtils.resolveModelResource(RESOURCE_BASE, "infinispan-bom-8.2.0.Final.pom");
        final Project project = new Project(model);

        Map<ArtifactRef, Dependency> deps = project.getResolvedManagedDependencies(new ManipulationSession());

        assertEquals(66, deps.size());
    }
}
