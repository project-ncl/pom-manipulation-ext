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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.Collections;
import java.util.Properties;

import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.jboss.pnc.mavenmanipulator.common.exception.ManipulationException;
import org.jboss.pnc.mavenmanipulator.common.model.Project;
import org.jboss.pnc.mavenmanipulator.core.ManipulationSession;
import org.jboss.pnc.mavenmanipulator.core.fixture.TestUtils;
import org.jboss.pnc.mavenmanipulator.core.state.DependencyState;
import org.jboss.pnc.mavenmanipulator.core.state.RESTState;
import org.jboss.pnc.mavenmanipulator.core.state.VersioningState;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RESTCollectorTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void collect_throwsManipulationException_whenProjectVersionIsDashSnapshot()
            throws Exception {
        final Properties props = new Properties();
        props.setProperty(RESTState.REST_URL, "http://localhost:8080/rest");

        final ManipulationSession session = TestUtils.createSession(props);
        final RESTCollector collector = new RESTCollector();
        collector.init(session);

        final Model model = new Model();
        model.setGroupId("org.jboss.pnc");
        model.setArtifactId("test-project");
        model.setVersion("-SNAPSHOT");

        final File pomFile = temp.newFile("pom.xml");
        final Project project = new Project(session, pomFile, model);
        project.setInheritanceRoot(true);

        try {
            collector.applyChanges(Collections.singletonList(project));
            fail("Expected ManipulationException");
        } catch (ManipulationException e) {
            assertThat(e.getMessage().contains("has no base component after SNAPSHOT removal"), equalTo(true));
        }
    }

    @Test
    public void collect_throwsManipulationException_whenProjectVersionIsDashSnapshot_withEnforceVersionPrefix()
            throws Exception {
        final Properties props = new Properties();
        props.setProperty(RESTState.REST_URL, "http://localhost:8080/rest");
        props.setProperty(VersioningState.ENFORCE_VERSION_PREFIX, "rhlw");
        props.setProperty(VersioningState.INCREMENT_SERIAL_SUFFIX_PADDING_SYSPROP, "5");

        final ManipulationSession session = TestUtils.createSession(props);
        final RESTCollector collector = new RESTCollector();
        collector.init(session);

        final Model model = new Model();
        model.setGroupId("org.jboss.pnc");
        model.setArtifactId("test-project");
        model.setVersion("-SNAPSHOT");

        final File pomFile = temp.newFile("pom.xml");
        final Project project = new Project(session, pomFile, model);
        project.setInheritanceRoot(true);

        try {
            collector.applyChanges(Collections.singletonList(project));
            fail("Expected ManipulationException");
        } catch (ManipulationException e) {
            assertThat(e.getMessage().contains("has no base component after SNAPSHOT removal"), equalTo(true));
        }
    }

    @Test
    public void collect_throwsManipulationException_whenParentVersionIsDashSnapshot()
            throws Exception {
        final Properties props = new Properties();
        props.setProperty(RESTState.REST_URL, "http://localhost:8080/rest");
        props.setProperty(DependencyState.DEPENDENCY_OVERRIDE_PREFIX, "true");

        final ManipulationSession session = TestUtils.createSession(props);
        final RESTCollector collector = new RESTCollector();
        collector.init(session);

        final Model model = new Model();
        model.setGroupId("org.jboss.pnc");
        model.setArtifactId("test-child");
        model.setVersion("1.0.0");

        final Parent parent = new Parent();
        parent.setGroupId("org.jboss.pnc");
        parent.setArtifactId("test-parent");
        parent.setVersion("-SNAPSHOT");
        model.setParent(parent);

        final File pomFile = temp.newFile("pom.xml");
        final Project project = new Project(session, pomFile, model);
        project.setInheritanceRoot(true);

        try {
            collector.applyChanges(Collections.singletonList(project));
            fail("Expected ManipulationException");
        } catch (ManipulationException e) {
            assertThat(e.getMessage().contains("has no base component after SNAPSHOT removal"), equalTo(true));
        }
    }

    @Test
    public void collect_throwsManipulationException_whenDependencyVersionIsDashSnapshot()
            throws Exception {
        final Properties props = new Properties();
        props.setProperty(RESTState.REST_URL, "http://localhost:8080/rest");
        props.setProperty(DependencyState.DEPENDENCY_OVERRIDE_PREFIX, "true");

        final ManipulationSession session = TestUtils.createSession(props);
        final RESTCollector collector = new RESTCollector();
        collector.init(session);

        final Model model = new Model();
        model.setGroupId("org.jboss.pnc");
        model.setArtifactId("test-project");
        model.setVersion("1.0.0");

        final Dependency dep = new Dependency();
        dep.setGroupId("org.jboss.pnc");
        dep.setArtifactId("dep-project");
        dep.setVersion("-SNAPSHOT");
        model.addDependency(dep);

        final File pomFile = temp.newFile("pom.xml");
        final Project project = new Project(session, pomFile, model);
        project.setInheritanceRoot(true);

        try {
            collector.applyChanges(Collections.singletonList(project));
            fail("Expected ManipulationException");
        } catch (ManipulationException e) {
            assertThat(e.getMessage().contains("has no base component after SNAPSHOT removal"), equalTo(true));
        }
    }

    @Test
    public void collect_throwsManipulationException_whenPluginVersionIsDashSnapshot()
            throws Exception {
        final Properties props = new Properties();
        props.setProperty(RESTState.REST_URL, "http://localhost:8080/rest");
        props.setProperty(DependencyState.DEPENDENCY_OVERRIDE_PREFIX, "true");

        final ManipulationSession session = TestUtils.createSession(props);
        final RESTCollector collector = new RESTCollector();
        collector.init(session);

        final Model model = new Model();
        model.setGroupId("org.jboss.pnc");
        model.setArtifactId("test-project");
        model.setVersion("1.0.0");

        final Plugin plugin = new Plugin();
        plugin.setGroupId("org.jboss.pnc");
        plugin.setArtifactId("plugin-project");
        plugin.setVersion("-SNAPSHOT");

        final Build build = new Build();
        build.addPlugin(plugin);
        model.setBuild(build);

        final File pomFile = temp.newFile("pom.xml");
        final Project project = new Project(session, pomFile, model);
        project.setInheritanceRoot(true);

        try {
            collector.applyChanges(Collections.singletonList(project));
            fail("Expected ManipulationException");
        } catch (ManipulationException e) {
            assertThat(e.getMessage().contains("has no base component after SNAPSHOT removal"), equalTo(true));
        }
    }
}
