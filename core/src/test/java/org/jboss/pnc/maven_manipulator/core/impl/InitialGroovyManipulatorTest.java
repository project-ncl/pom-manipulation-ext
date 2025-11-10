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
package org.jboss.pnc.maven_manipulator.core.impl;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.jboss.pnc.maven_manipulator.common.model.Project;
import org.jboss.pnc.maven_manipulator.core.fixture.TestUtils;
import org.jboss.pnc.maven_manipulator.io.PomIO;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * @author Michal Szynkiewicz, michal.l.szynkiewicz@gmail.com
 *         <br>
 *         Date: 22/08/2019
 */
public class InitialGroovyManipulatorTest {
    @Rule
    public TemporaryFolder tf = new TemporaryFolder();

    @Test
    public void shouldRemoveProjectInLegacyGroovyScript() throws Exception {
        final File groovy = TestUtils.resolveFileResource("groovy-project-removal", "legacy-manipulation.groovy");
        common(groovy);
    }

    @Test
    public void shouldRemoveProjectInGroovyScript() throws Exception {
        final File groovy = TestUtils.resolveFileResource("groovy-project-removal", "manipulation.groovy");
        common(groovy);
    }

    protected void common(File groovy) throws Exception {
        final File base = TestUtils.resolveFileResource("groovy-project-removal", "");
        final File root = tf.newFolder();
        FileUtils.copyDirectory(base, root);
        final File projectroot = new File(root, "pom.xml");

        final DefaultContainerConfiguration config = new DefaultContainerConfiguration();
        config.setClassPathScanning(PlexusConstants.SCANNING_ON);
        config.setComponentVisibility(PlexusConstants.GLOBAL_VISIBILITY);
        config.setName("PME-CLI");
        PlexusContainer container = new DefaultPlexusContainer(config);

        PomIO pomIO = container.lookup(PomIO.class);

        List<Project> projects = pomIO.parseProject(projectroot);

        assertThat(projects.size(), equalTo(3));

        Properties userProperties = new Properties();
        userProperties.setProperty("versionIncrementalSuffix", "rebuild");
        userProperties.setProperty("groovyScripts", groovy.toURI().toString());

        TestUtils.SMContainer smc = TestUtils.createSessionAndManager(userProperties, projectroot);
        smc.getManager().scanAndApply(smc.getSession());

        // re-read the projects:
        projects = pomIO.parseProject(projectroot);
        assertThat(projects.size(), equalTo(3));

        assertEquals(1, projectForArtifactId(projects, "groovy-project-removal").getModel().getProfiles().size());
        assertThat(projectForArtifactId(projects, "groovy-project-removal").getVersion(), containsString("rebuild"));
        assertThat(
                projectForArtifactId(projects, "groovy-project-removal-moduleA").getVersion(),
                containsString("rebuild"));
        // moduleB was removed from projects by the groovy script and therefore should not be reversioned:
        assertThat(projectForArtifactId(projects, "groovy-project-removal-moduleB").getVersion(), equalTo("1.0.0"));
    }

    private Project projectForArtifactId(List<Project> projects, String artifactId) {
        Optional<Project> maybeProject = projects.stream().filter(p -> p.getArtifactId().equals(artifactId)).findAny();
        if (!maybeProject.isPresent()) {
            fail("Unable to find project " + artifactId + " in the groovy-project-removal project");
        }
        return maybeProject.get();
    }
}
