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
package org.commonjava.maven.ext.core.groovy;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.model.Project;
import org.commonjava.maven.ext.core.fixture.TestUtils;
import org.commonjava.maven.ext.core.state.VersioningState;
import org.commonjava.maven.ext.io.PomIO;
import org.commonjava.maven.ext.io.rest.handler.AddSuffixJettyHandler;
import org.commonjava.maven.ext.io.rest.rule.MockServer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

public class GroovyFunctionsTest {
    private static final String RESOURCE_BASE = "properties/";

    private final AddSuffixJettyHandler handler = new AddSuffixJettyHandler();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public final TestRule restoreSystemProperties = new RestoreSystemProperties();

    @Rule
    public MockServer mockServer = new MockServer(handler);

    @Test(expected = ManipulationException.class)
    public void testOverride() throws Exception {
        // Locate the PME project pom file. Use that to verify inheritance tracking.
        final File projectroot = new File(
                TestUtils.resolveFileResource(RESOURCE_BASE, "")
                        .getParentFile()
                        .getParentFile()
                        .getParentFile()
                        .getParentFile(),
                "pom.xml");
        PomIO pomIO = new PomIO();
        List<Project> projects = pomIO.parseProject(projectroot);

        BaseScriptImplTest impl = new BaseScriptImplTest();
        Properties p = new Properties();
        p.setProperty("restURL", mockServer.getUrl());

        impl.setValues(
                pomIO,
                null,
                null,
                TestUtils.createSession(p, projectroot),
                projects,
                projects.get(0),
                InvocationStage.FIRST);

        impl.overrideProjectVersion(SimpleProjectVersionRef.parse("org.foo:bar:1.0"));
    }

    @Test
    public void testOverrideWithTemp() throws Exception {
        final File base = TestUtils.resolveFileResource("groovy-project-removal", "");
        final File root = temporaryFolder.newFolder();
        FileUtils.copyDirectory(base, root);
        final DefaultContainerConfiguration config = new DefaultContainerConfiguration();

        config.setClassPathScanning(PlexusConstants.SCANNING_ON);
        config.setComponentVisibility(PlexusConstants.GLOBAL_VISIBILITY);
        config.setName("PME-CLI");

        final PlexusContainer container = new DefaultPlexusContainer(config);
        final PomIO pomIO = container.lookup(PomIO.class);
        final File projectroot = new File(root, "pom.xml");
        final List<Project> projects = pomIO.parseProject(projectroot);

        assertEquals(3, projects.size());

        BaseScriptImplTest impl = new BaseScriptImplTest();
        Properties p = new Properties();
        p.setProperty("versionIncrementalSuffix", "temporary-redhat");
        p.setProperty("restRepositoryGroup", "GroovyWithTemporary");
        p.setProperty("restURL", mockServer.getUrl());

        impl.setValues(
                pomIO,
                null,
                null,
                TestUtils.createSession(p, projectroot),
                projects,
                projects.get(0),
                InvocationStage.FIRST);

        impl.overrideProjectVersion(SimpleProjectVersionRef.parse("org.goots:sample:1.0.0"));
    }

    @Test
    public void testTempOverrideWithNonTemp() throws Exception {
        final File base = TestUtils.resolveFileResource("profile.pom", "");
        final File root = temporaryFolder.newFolder();
        final File target = new File(root, "profile.xml");
        FileUtils.copyFile(base, target);
        final DefaultContainerConfiguration config = new DefaultContainerConfiguration();

        config.setClassPathScanning(PlexusConstants.SCANNING_ON);
        config.setComponentVisibility(PlexusConstants.GLOBAL_VISIBILITY);
        config.setName("PME-CLI");

        final PlexusContainer container = new DefaultPlexusContainer(config);
        final PomIO pomIO = container.lookup(PomIO.class);
        final List<Project> projects = pomIO.parseProject(target);

        assertEquals(1, projects.size());

        BaseScriptImplTest impl = new BaseScriptImplTest();
        Properties p = new Properties();
        p.setProperty("versionIncrementalSuffix", "temporary-redhat");
        p.setProperty("restMode", "GroovyWithTemporary");
        p.setProperty("restURL", mockServer.getUrl());

        impl.setValues(
                pomIO,
                null,
                null,
                TestUtils.createSession(p, target),
                projects,
                projects.get(0),
                InvocationStage.FIRST);

        impl.overrideProjectVersion(SimpleProjectVersionRef.parse("org.goots:testTempOverrideWithNonTemp:1.0.0"));

        assertEquals(
                "redhat-5",
                impl.getUserProperties().getProperty(VersioningState.VERSION_SUFFIX_SYSPROP));
    }

    private static class BaseScriptImplTest extends BaseScript {
        @Override
        public Object run() {
            return null;
        }
    }
}
