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
package org.commonjava.maven.ext.core.util;

import static java.util.stream.Collectors.toSet;
import static org.commonjava.maven.ext.core.util.PropertiesUtils.updateProperties;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.json.PME;
import org.commonjava.maven.ext.common.model.Project;
import org.commonjava.maven.ext.common.util.ProjectComparator;
import org.commonjava.maven.ext.common.util.PropertyResolver;
import org.commonjava.maven.ext.common.util.WildcardMap;
import org.commonjava.maven.ext.core.ManipulationSession;
import org.commonjava.maven.ext.core.fixture.TestUtils;
import org.commonjava.maven.ext.core.impl.Version;
import org.commonjava.maven.ext.core.state.CommonState;
import org.commonjava.maven.ext.core.state.DependencyState;
import org.commonjava.maven.ext.core.state.RelocationState;
import org.commonjava.maven.ext.core.state.VersioningState;
import org.commonjava.maven.ext.io.PomIO;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;

public class PropertiesUtilsTest {
    private static final String RESOURCE_BASE = "properties/";

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private final Properties p = new Properties();

    @Before
    public void beforeTest() {
        p.setProperty("strictAlignment", "true");
        p.setProperty("strictViolationFails", "true");
        p.setProperty("versionSuffix", "redhat-1");
        p.setProperty("scanActiveProfiles", "true");
    }

    @Test
    public void testCheckStrictValue() throws Exception {
        ManipulationSession session = createUpdateSession();
        assertFalse(PropertiesUtils.checkStrictValue(session, null, "1.0"));
        assertFalse(PropertiesUtils.checkStrictValue(session, "1.0", null));
        assertFalse(PropertiesUtils.checkStrictValue(session, "1.0.0.Final", "1.0.0.redhat-1"));
        assertTrue(PropertiesUtils.checkStrictValue(session, "1.0.0", "1.0.0.redhat-1"));
        assertTrue(PropertiesUtils.checkStrictValue(session, "1.0.0-SNAPSHOT", "1.0.0.redhat-1"));
        assertFalse(PropertiesUtils.checkStrictValue(session, "1.0.Final-SNAPSHOT", "1.0.0.redhat-1"));
        assertTrue(PropertiesUtils.checkStrictValue(session, "1.0-SNAPSHOT", "1.0.0.redhat-1"));
    }

    @Test
    public void testCheckStrictValueWithMgdSvc1() throws Exception {
        p.remove("versionSuffix");
        p.setProperty("versionIncrementalSuffix", "managedsvc-temporary-redhat");
        p.setProperty("versionSuffixAlternatives", "redhat,managedsvc-redhat");
        ManipulationSession session = createUpdateSession();
        assertTrue(PropertiesUtils.checkStrictValue(session, "0.26.0", "0.26.0.managedsvc-redhat-00014"));
        assertFalse(systemOutRule.getLog().contains("0.26.0 to 0.26.0.managedsvc and"));
    }

    @Test
    public void testCheckStrictValueWithMgdSvc2() throws Exception {
        p.remove("versionSuffix");
        p.setProperty("versionIncrementalSuffix", "managedsvc-temporary-redhat");
        p.setProperty("versionSuffixAlternatives", "redhat");
        ManipulationSession session = createUpdateSession();
        assertFalse(PropertiesUtils.checkStrictValue(session, "0.26.0", "0.26.0.managedsvc-redhat-00014"));
    }

    @Test
    public void testGetSuffix() throws Exception {
        p.remove("versionSuffix");
        ManipulationSession session = createUpdateSession();
        assertNotNull(PropertiesUtils.getSuffix(session));
    }

    @Test
    public void testStrictWithTimeStamp() throws Exception {
        String suffix = "t-20170216-223844-555-rebuild";
        p.setProperty("versionSuffix", suffix + "-1");
        ManipulationSession session = createUpdateSession();

        assertEquals(PropertiesUtils.getSuffix(session), suffix);
        assertTrue(
                PropertiesUtils.checkStrictValue(
                        session,
                        "1.0.0.Final",
                        "1.0.0.Final-t-20170216-223844-555-rebuild-1"));
        assertTrue(PropertiesUtils.checkStrictValue(session, "1.0", "1.0.0.t-20170216-223844-555-rebuild-1"));
        assertTrue(PropertiesUtils.checkStrictValue(session, "1.0-SNAPSHOT", "1.0.0.t-20170216-223844-555-rebuild-1"));

        suffix = "t20170216223844555-rebuild";
        p.setProperty("versionSuffix", suffix + "-2");
        session = createUpdateSession();

        assertEquals(PropertiesUtils.getSuffix(session), suffix);
        assertTrue(
                PropertiesUtils.checkStrictValue(session, "1.0.0.Final", "1.0.0.Final-t20170216223844555-rebuild-2"));
        assertTrue(PropertiesUtils.checkStrictValue(session, "1.0", "1.0.0.t20170216223844555-rebuild-2"));
        assertTrue(PropertiesUtils.checkStrictValue(session, "1.0-SNAPSHOT", "1.0.0.t20170216223844555-rebuild-2"));
    }

    @Test
    public void testCacheProperty() throws Exception {
        ManipulationSession session = createUpdateSession();
        Map<Project, Map<String, PropertyMapper>> propertyMap = new HashMap<>();
        Project project = getProject();
        Plugin dummy = new Plugin();
        dummy.setGroupId("org.dummy");
        dummy.setArtifactId("dummyArtifactId");

        assertFalse(
                PropertiesUtils.cacheProperty(
                        session,
                        project,
                        propertyMap,
                        "${foobar}${foobar2}",
                        null,
                        dummy,
                        false));
        assertFalse(
                PropertiesUtils.cacheProperty(session, project, propertyMap, "suffix.${foobar}", null, dummy, false));
        assertFalse(PropertiesUtils.cacheProperty(session, project, propertyMap, null, "2.0", dummy, false));
        assertFalse(PropertiesUtils.cacheProperty(session, project, propertyMap, "1.0", "2.0", dummy, false));
        assertTrue(
                PropertiesUtils.cacheProperty(
                        session,
                        project,
                        propertyMap,
                        "${version.org.jboss}",
                        "2.0",
                        dummy,
                        false));
        assertFalse(
                PropertiesUtils.cacheProperty(
                        session,
                        project,
                        propertyMap,
                        Version.PROJECT_VERSION,
                        "2.0",
                        dummy,
                        false));

        // DependencyManipulator does dependency.getVersion(). This could return e.g. ${version.scala} which can
        // refer to <version.scala>${version.scala.major}.7</version.scala>. If we are attempting to change version.scala
        // to e.g. 2.11.7.redhat-1 then in this case we need to ignore the version.scala.major property and append the .redhat-1.

        // If the property is ${...}.foobar then we only want to append suffix to foobar to change the version
        // However we don't need to change the value of the property. If the property is foobar.${....} then
        // we want to append suffix to the property ... but we need to handle that part of the property is hardcoded.

        assertFalse(
                PropertiesUtils.cacheProperty(
                        session,
                        project,
                        propertyMap,
                        "${version.scala}.7",
                        "2.0",
                        null,
                        false));
        assertFalse(
                PropertiesUtils.cacheProperty(
                        session,
                        project,
                        propertyMap,
                        "${version.foo}.${version.scala}.7",
                        "2.0",
                        null,
                        false));

        try {
            PropertiesUtils.cacheProperty(
                    session,
                    project,
                    propertyMap,
                    "${version.scala}.7.${version.scala2}",
                    "2.0",
                    null,
                    false);
        } catch (ManipulationException e) {
            // Pass.
        }
    }

    @Test
    public void testUpdateNestedProperties1() throws Exception {
        Project pP = getProject();
        ManipulationSession session = createUpdateSession();

        assertSame(
                updateProperties(session, pP, false, "version.hibernate.core", "5.0.4.Final-redhat-1"),
                PropertiesUtils.PropertyUpdate.FOUND);

        assertSame(
                updateProperties(session, pP, false, "version.scala", "2.11.7.redhat-1"),
                PropertiesUtils.PropertyUpdate.FOUND);
        try {
            updateProperties(session, pP, false, "version.scala", "3.11.7-redhat-1");
        } catch (ManipulationException e) {
            // Pass.
        }
    }

    @Test
    public void testUpdateNestedProperties2() throws Exception {
        Project pP = getProject();

        ManipulationSession session = createUpdateSession();

        assertSame(
                updateProperties(session, pP, false, "version.hibernate.osgi", "5.0.4.Final-redhat-1"),
                PropertiesUtils.PropertyUpdate.FOUND);

        assertNotSame(
                updateProperties(session, pP, false, "version.scala", "2.11.7"),
                PropertiesUtils.PropertyUpdate.FOUND);
    }

    @Test
    public void testUpdateNestedProperties3() throws Exception {
        // io.hawt:project:1.4.9
        final Model modelParent = TestUtils.resolveModelResource(RESOURCE_BASE, "project-1.4.9.pom");
        Project pP = new Project(modelParent);

        ManipulationSession session = createUpdateSession();

        assertSame(
                updateProperties(session, pP, false, "perfectus-build", "610379.redhat-1"),
                PropertiesUtils.PropertyUpdate.FOUND);

        try {
            assertSame(
                    updateProperties(session, pP, false, "perfectus-build", "610.NOTTHEVALUE.redhat-1"),
                    PropertiesUtils.PropertyUpdate.FOUND);
        } catch (ManipulationException e) {
            // Pass.
        }
        try {
            assertSame(
                    updateProperties(session, pP, true, "perfectus-build", "610.NOTTHEVALUE.redhat-1"),
                    PropertiesUtils.PropertyUpdate.FOUND);
        } catch (ManipulationException e) {
            // Pass.
        }
    }

    @Test
    public void testUpdateNestedProperties4() throws Exception {
        Project pP = getProject();
        ManipulationSession session = createUpdateSession();

        assertSame(
                updateProperties(
                        session,
                        pP,
                        false,
                        "camel.version",
                        "3.18.2.redhat-00003"),
                PropertiesUtils.PropertyUpdate.FOUND);
        assertEquals(
                "${camel.major.minor}.2.redhat-00003",
                pP.getModel().getProperties().getProperty("camel.version"));

        assertSame(
                updateProperties(
                        session,
                        pP,
                        true,
                        "camel.version",
                        "3.18.2.redhat-00004"),
                PropertiesUtils.PropertyUpdate.FOUND);
        assertEquals(
                "${camel.major.minor}.2.redhat-00004",
                pP.getModel().getProperties().getProperty("camel.version"));

        assertSame(
                updateProperties(
                        session,
                        pP,
                        false,
                        "camel.version.2",
                        "3.18.2.redhat-00003"),
                PropertiesUtils.PropertyUpdate.FOUND);
        assertEquals(
                "3.18.2.redhat-00003",
                pP.getModel().getProperties().getProperty("camel.version.2"));
        assertTrue(
                systemOutRule.getLog()
                        .contains("Original value (${camel.major.minor}${rh}) ends with variable so inlining update"));

        assertSame(
                updateProperties(
                        session,
                        pP,
                        false,
                        "camel.version.3",
                        "3.18.2.redhat-00003"),
                PropertiesUtils.PropertyUpdate.FOUND);
        assertEquals(
                "3.${camel.minor}.2.redhat-00003",
                pP.getModel().getProperties().getProperty("camel.version.3"));
    }

    @Test
    public void testResolveProperties() throws Exception {
        final Model modelChild = TestUtils.resolveModelResource(RESOURCE_BASE, "inherited-properties.pom");
        final Model modelParent = TestUtils.resolveModelResource(RESOURCE_BASE, "infinispan-bom-8.2.0.Final.pom");
        ManipulationSession session = createUpdateSession();

        Project pP = new Project(modelParent);
        Project pC = new Project(modelChild);
        List<Project> al = new ArrayList<>();
        al.add(pC);
        al.add(pP);

        String result = PropertyResolver
                .resolveProperties(session, al, "${version.scala.major}.${version.scala.minor}");
        assertEquals("2.11.7", result);

        result = PropertyResolver.resolveProperties(
                session,
                al,
                "TestSTART.and.${version.scala.major}.now.${version.scala.minor}");
        assertEquals("TestSTART.and.2.11.now.7", result);

        result = PropertyResolver.resolveProperties(session, al, Version.PROJECT_VERSION);
        assertEquals("1", result);

        result = PropertyResolver.resolveProperties(session, al, "${version.hibernate.osgi}");
        assertEquals("5.0.4.Final", result);

        result = PropertyResolver.resolveProperties(session, al, "version.gnu.getopt");
        assertEquals("version.gnu.getopt", result);
    }

    @Test
    public void testUpdateProjectVersionProperty() throws Exception {
        Project pP = getProject();

        ManipulationSession session = createUpdateSession();

        assertNotSame(
                updateProperties(session, pP, false, "project.version", "5.0.4.Final-redhat-1"),
                PropertiesUtils.PropertyUpdate.FOUND);
    }

    @Test
    public void testCompareProjects() throws Exception {
        final File projectroot = TestUtils.resolveFileResource(RESOURCE_BASE, "infinispan-bom-8.2.0.Final.pom");
        ManipulationSession session = createUpdateSession();

        PomIO pomIO = new PomIO();
        List<Project> projects = pomIO.parseProject(projectroot);

        List<Project> newprojects = pomIO.parseProject(projectroot);

        WildcardMap<ProjectVersionRef> map = (session.getState(RelocationState.class) == null ? new WildcardMap<>()
                : session.getState(RelocationState.class).getDependencyRelocations());
        String result = ProjectComparator.compareProjects(session, new PME(), map, projects, newprojects);

        assertTrue(
                result.contains("------------------- project org.infinispan:infinispan-bom" + System.lineSeparator()));
    }

    private Project getProject() throws Exception {
        final Model modelParent = TestUtils.resolveModelResource(RESOURCE_BASE, "infinispan-bom-8.2.0.Final.pom");
        return new Project(modelParent);
    }

    @SuppressWarnings("deprecation")
    private ManipulationSession createUpdateSession() throws Exception {
        ManipulationSession session = new ManipulationSession();

        session.setState(new DependencyState(p));
        session.setState(new VersioningState(p));
        session.setState(new CommonState(p));

        final MavenExecutionRequest req = new DefaultMavenExecutionRequest().setUserProperties(p)
                .setRemoteRepositories(Collections.emptyList());

        final PlexusContainer container = new DefaultPlexusContainer();
        final MavenSession mavenSession = new MavenSession(container, null, req, new DefaultMavenExecutionResult());

        session.setMavenSession(mavenSession);

        return session;
    }

    @Test
    public void testResolvePluginsProject() throws Exception {
        final Model modelChild = TestUtils.resolveModelResource(RESOURCE_BASE, "inherited-properties.pom");
        ManipulationSession session = createUpdateSession();
        Project pC = new Project(modelChild);

        assertEquals(0, pC.getResolvedPlugins(session).size());
        assertEquals(0, pC.getResolvedManagedPlugins(session).size());
    }

    @Test
    public void testBuildOldValueSetWithTemporary() {
        Properties user = new Properties();
        user.setProperty(VersioningState.INCREMENT_SERIAL_SUFFIX_SYSPROP, "temporary-redhat");
        final VersioningState vs = new VersioningState(user);

        Set<String> found = PropertiesUtils.buildOldValueSet(vs, "1.0.0.Final-redhat-10");

        assertEquals(Stream.of("1.0.0.Final-temporary-redhat-0", "1.0.0.Final-redhat-10").collect(toSet()), found);
    }

    @Test
    public void testBuildOldValueSetWithTemporaryAndMultipleAlternatives() {
        Properties user = new Properties();
        user.setProperty(VersioningState.INCREMENT_SERIAL_SUFFIX_SYSPROP, "temporary-redhat");
        user.setProperty(VersioningState.VERSION_SUFFIX_ALT, "foobar,redhat");
        final VersioningState vs = new VersioningState(user);

        Set<String> found = PropertiesUtils.buildOldValueSet(vs, "1.0.0.Final-foobar-10");
        assertEquals(Stream.of("1.0.0.Final-foobar-10", "1.0.0.Final-temporary-redhat-0").collect(toSet()), found);
    }

    @Test
    public void testBuildOldValueSetWithNoAlternatives() {
        Properties user = new Properties();
        user.setProperty(VersioningState.INCREMENT_SERIAL_SUFFIX_SYSPROP, "redhat");
        final VersioningState vs = new VersioningState(user);

        Set<String> found = PropertiesUtils.buildOldValueSet(vs, "1.0.0.Final-temporary-redhat-10");
        System.out.println("### Found " + found);
        assertEquals(Stream.of("1.0.0.Final-temporary-redhat-10").collect(toSet()), found);
    }

    @Test
    public void testBuildOldValueSetWithNoSuffix() {
        Properties user = new Properties();
        final VersioningState vs = new VersioningState(user);

        Set<String> found = PropertiesUtils.buildOldValueSet(vs, "1.0.0.Final-redhat-10");
        assertEquals(Stream.of("1.0.0.Final-redhat-10").collect(toSet()), found);
    }

    @Test
    public void testBuildOldValueSetWithNoStartSuffix() {
        Properties user = new Properties();
        user.setProperty(VersioningState.INCREMENT_SERIAL_SUFFIX_SYSPROP, "redhat");
        final VersioningState vs = new VersioningState(user);

        Set<String> found = PropertiesUtils.buildOldValueSet(vs, "1.0");
        assertEquals(Stream.of("1.0").collect(toSet()), found);
    }

    @Test
    public void testNoSuffix() throws Exception {
        p.clear();
        ManipulationSession session = createUpdateSession();
        VersioningState vs = session.getState(VersioningState.class);
        assertEquals(0, vs.getAllSuffixes().size());
    }

    @Test
    public void testAllSuffixWithRH() throws Exception {
        ManipulationSession session = createUpdateSession();
        VersioningState vs = session.getState(VersioningState.class);
        assertEquals(1, vs.getAllSuffixes().size());
        assertEquals("redhat", vs.getAllSuffixes().get(0));
    }

    @Test
    public void testAllSuffixWithNonRH() throws Exception {
        p.setProperty("versionSuffix", "a-random-value");
        ManipulationSession session = createUpdateSession();
        VersioningState vs = session.getState(VersioningState.class);
        assertEquals(Arrays.asList("a-random", "redhat"), vs.getAllSuffixes());
    }

    @Test
    public void testOriginalTypeChecking() throws Exception {
        ManipulationSession session = createUpdateSession();
        Map<Project, Map<String, PropertyMapper>> propertyMap = new HashMap<>();
        Project project = getProject();

        try {
            assertFalse(
                    PropertiesUtils.cacheProperty(
                            session,
                            project,
                            propertyMap,
                            "${foobar}",
                            null,
                            null,
                            false));
            fail("Should have thrown an exception");
        } catch (ManipulationException e) {
            assertTrue(e.getMessage().contains("Unknown type for null"));
        }
    }
}
