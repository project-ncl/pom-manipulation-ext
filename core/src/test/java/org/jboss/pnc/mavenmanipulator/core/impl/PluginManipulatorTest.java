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
package org.jboss.pnc.mavenmanipulator.core.impl;

import static org.jboss.pnc.mavenmanipulator.core.fixture.TestUtils.createSession;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.commonjava.atlas.maven.ident.ref.ArtifactRef;
import org.commonjava.atlas.maven.ident.ref.SimpleArtifactRef;
import org.jboss.pnc.mavenmanipulator.common.exception.ManipulationException;
import org.jboss.pnc.mavenmanipulator.common.model.Project;
import org.jboss.pnc.mavenmanipulator.core.ManipulationSession;
import org.jboss.pnc.mavenmanipulator.core.state.PluginState;
import org.junit.Test;

/**
 * Tests for {@link PluginManipulator}.
 */
public class PluginManipulatorTest {

    /**
     * Reproduces the NullPointerException reported when a {@code <plugin>} element is declared in
     * {@code <pluginManagement>} with no {@code <groupId>} and no {@code <version>}. Maven defaults
     * the group to {@code org.apache.maven.plugins} in this case, but the raw {@link Plugin} object
     * still carries {@code null} for both fields. When a REST override exists for that plugin,
     * {@link PluginManipulator#applyOverrides} used to call {@code plugin.getVersion().equals(...)}
     * unconditionally, causing an NPE.
     *
     * @see <a href="https://github.com/release-engineering/pom-manipulation-ext/issues">PME issue</a>
     */
    @Test
    public void pluginWithoutGroupIdAndVersionDoesNotThrowNPE() throws ManipulationException {
        // Arrange: session using REST as plugin source so the REST override is active.
        // Disable strict alignment to match the production configuration in which the NPE was
        // observed (strict alignment is not in the pncDefaultAlignmentParameters for LIGHTWELL builds).
        Properties props = new Properties();
        props.setProperty("pluginSource", "REST");
        props.setProperty("strictAlignment", "false");
        ManipulationSession session = createSession(props);

        PluginManipulator manipulator = new PluginManipulator(null);
        // init() registers a fresh PluginState in the session; REST overrides must be set after.
        manipulator.init(session);

        // Simulate what RESTCollector does: inject a REST override for maven-jar-plugin.
        Map<ArtifactRef, String> restOverrides = new HashMap<>();
        restOverrides.put(
                SimpleArtifactRef.parse("org.apache.maven.plugins:maven-jar-plugin:2.6.0.rhlw-00002"),
                "2.6.0.rhlw-00002");
        session.getState(PluginState.class).setRemoteRESTOverrides(restOverrides);

        // Build a model that declares maven-jar-plugin with no groupId and no version,
        // exactly as seen in the httpcomponents-parent POM that triggered the failure.
        Model model = new Model();
        model.setGroupId("org.apache.httpcomponents");
        model.setArtifactId("httpcomponents-parent");
        model.setVersion("14");

        Plugin plugin = new Plugin();
        // No groupId set — Maven defaults this to org.apache.maven.plugins
        plugin.setArtifactId("maven-jar-plugin");
        // No version set — this is what triggers the NPE in the unpatched code

        PluginManagement pm = new PluginManagement();
        pm.addPlugin(plugin);

        Build build = new Build();
        build.setPluginManagement(pm);
        model.setBuild(build);

        Project project = new Project(model);
        project.setInheritanceRoot(true);

        // Act: must not throw NullPointerException.
        List<Project> projects = Collections.singletonList(project);
        Set<Project> changed = manipulator.applyChanges(projects);

        // Assert: no NPE was thrown and the project was processed.
        // Prior to the fix, plugin.getVersion() threw a NullPointerException because
        // the raw Plugin object has version=null when only <artifactId> is declared.
        assertEquals(1, changed.size());
        // The version should have been set to the REST override value.
        Plugin aligned = model.getBuild().getPluginManagement().getPlugins().get(0);
        assertEquals("2.6.0.rhlw-00002", aligned.getVersion());
    }

    /**
     * Verifies that with {@code strictAlignment=true} (the production default) a plugin declared
     * without {@code <groupId>}/{@code <version>} does not throw a NullPointerException either.
     * In this mode the strict-version check receives {@code null} as the old value, which causes
     * {@link org.jboss.pnc.mavenmanipulator.core.util.PropertiesUtils#checkStrictValue} to return
     * {@code false}. Because {@code failOnStrictViolation} defaults to {@code false} the override
     * is simply skipped (the plugin version remains {@code null}), but no exception is thrown.
     */
    @Test
    public void pluginWithoutVersionDoesNotThrowNPEWithStrictAlignment() throws ManipulationException {
        // Arrange: strictAlignment=true is the default, so we only need pluginSource=REST.
        Properties props = new Properties();
        props.setProperty("pluginSource", "REST");
        // strictAlignment defaults to true — explicitly set it here for clarity.
        props.setProperty("strictAlignment", "true");
        ManipulationSession session = createSession(props);

        PluginManipulator manipulator = new PluginManipulator(null);
        manipulator.init(session);

        Map<ArtifactRef, String> restOverrides = new HashMap<>();
        restOverrides.put(
                SimpleArtifactRef.parse("org.apache.maven.plugins:maven-jar-plugin:2.6.0.rhlw-00002"),
                "2.6.0.rhlw-00002");
        session.getState(PluginState.class).setRemoteRESTOverrides(restOverrides);

        Model model = new Model();
        model.setGroupId("org.apache.httpcomponents");
        model.setArtifactId("httpcomponents-parent");
        model.setVersion("14");

        Plugin plugin = new Plugin();
        plugin.setArtifactId("maven-jar-plugin");
        // No groupId and no version — same trigger as the first test.

        PluginManagement pm = new PluginManagement();
        pm.addPlugin(plugin);

        Build build = new Build();
        build.setPluginManagement(pm);
        model.setBuild(build);

        Project project = new Project(model);
        project.setInheritanceRoot(true);

        // Act: must not throw NullPointerException.
        List<Project> projects = Collections.singletonList(project);
        manipulator.applyChanges(projects);

        // Assert: the strict-version check sees oldValue=null and skips the override,
        // so the plugin version stays null — but no NPE was thrown.
        Plugin aligned = model.getBuild().getPluginManagement().getPlugins().get(0);
        assertNull(aligned.getVersion());
    }
}
