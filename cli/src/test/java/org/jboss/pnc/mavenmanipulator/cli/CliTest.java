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
package org.jboss.pnc.mavenmanipulator.cli;

import static org.jboss.pnc.mavenmanipulator.core.fixture.TestUtils.INTEGRATION_TEST;
import static org.jboss.pnc.mavenmanipulator.core.fixture.TestUtils.ROOT_DIRECTORY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.maven.execution.MavenSession;
import org.jboss.pnc.mavenmanipulator.core.ManipulationSession;
import org.jboss.pnc.mavenmanipulator.core.fixture.TestUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.ProvideSystemProperty;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;

public class CliTest {
    @Rule
    public final ProvideSystemProperty ansiOFF = new ProvideSystemProperty("picocli.ansi", "false");

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    private File writeSettings(File f) throws IOException {
        FileUtils.writeStringToFile(
                f,
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                        + "<settings xmlns=\"https://maven.apache.org/SETTINGS/1.0.0\""
                        + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                        + "xsi:schemaLocation=\"https://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd\">"
                        + "</settings>",
                StandardCharsets.UTF_8);
        return f;
    }

    @Test
    public void checkHelpAndExit() {
        exit.expectSystemExitWithStatus(0);
        exit.checkAssertionAfterwards(() -> assertTrue(systemOutRule.getLog().contains("Usage: PME")));
        Cli.main(new String[] { "-h" });
    }

    @Test
    public void checkInvalidParam() {
        new Cli().run(new String[] { "-FOOBAR" });
        assertTrue(systemErrRule.getLog().contains("Unknown option"));
    }

    @Test
    public void checkTargetMatches() throws Exception {
        Cli c = new Cli();
        File pom1 = temp.newFile();
        File settings = writeSettings(temp.newFile());

        TestUtils.executeMethod(
                c,
                "createSession",
                new Class[] { File.class, File.class },
                new Object[] { pom1, settings });

        assertEquals(
                "Session file should match",
                pom1,
                ((ManipulationSession) FieldUtils.readField(c, "session", true)).getPom());
    }

    @Test
    public void checkTargetMatchesWithRun() throws Exception {
        Cli c = new Cli();
        File pom1 = temp.newFile();

        c.run(new String[] { "-f", pom1.toString() });

        assertEquals(
                "Session file should match",
                pom1,
                ((ManipulationSession) FieldUtils.readField(c, "session", true)).getPom());
    }

    @Test
    public void checkTargetDefaultMatches() throws Exception {
        Cli c = new Cli();
        c.run(new String[] {});

        ManipulationSession session = (ManipulationSession) FieldUtils.readField(c, "session", true);
        File defaultTarget = (File) FieldUtils.readField(c, "target", true);

        assertEquals("Session file should match", defaultTarget, session.getPom());
    }

    @Test
    public void checkLocalRepositoryWithDefaults() throws Exception {
        Cli c = new Cli();
        File settings = writeSettings(temp.newFile());
        c.run(new String[] { "-s", settings.toString() });

        ManipulationSession session = (ManipulationSession) FieldUtils.readField(c, "session", true);
        MavenSession ms = (MavenSession) FieldUtils.readField(session, "mavenSession", true);

        assertEquals(
                ms.getRequest().getLocalRepository().getBasedir(),
                ms.getRequest().getLocalRepositoryPath().toString());
        assertEquals(
                "File " + new File(ms.getRequest().getLocalRepository().getBasedir()).getParentFile().toString()
                        + " was not equal to " + System.getProperty("user.home") + File.separatorChar + ".m2",
                new File(ms.getRequest().getLocalRepository().getBasedir()).getParentFile().toString(),
                System.getProperty("user.home") + File.separatorChar + ".m2");

    }

    @Test
    public void checkLocalRepositoryWithDefaultsAndModifiedUserSettings() throws Exception {
        boolean restore = false;
        Path source = Paths.get(
                System.getProperty("user.home") + File.separatorChar + ".m2" + File.separatorChar
                        + "settings.xml");
        Path backup = Paths.get(source.toString() + '.' + UUID.randomUUID());
        Path tmpSettings = Paths.get(getClass().getResource("/settings-test.xml").toURI());

        try {
            if (source.toFile().exists()) {
                System.out.println("Backing up settings.xml to " + backup);
                restore = true;
                Files.move(source, backup, StandardCopyOption.ATOMIC_MOVE);
            }
            Files.copy(tmpSettings, source);

            Cli c = new Cli();
            c.run(new String[] {});

            ManipulationSession session = (ManipulationSession) FieldUtils.readField(c, "session", true);
            MavenSession ms = (MavenSession) FieldUtils.readField(session, "mavenSession", true);

            assertEquals(
                    ms.getRequest().getLocalRepository().getBasedir(),
                    ms.getRequest().getLocalRepositoryPath().toString());
            assertEquals(
                    ms.getLocalRepository().getBasedir(),
                    System.getProperty("user.home") + File.separatorChar + ".m2-mead-test");

        } finally {
            if (restore) {
                Files.move(backup, source, StandardCopyOption.ATOMIC_MOVE);
            } else {
                Files.delete(source);
            }
        }
    }

    @Test
    public void checkProfileActivation() throws Exception {
        File folder = temp.newFolder();
        File target = temp.newFile();
        // Locate the PME project pom file. Use that to verify inheritance tracking.
        Files.copy(
                Paths.get(INTEGRATION_TEST.toString(), "pom.xml"),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING);

        Cli c = new Cli();
        c.run(
                new String[] {
                        "-d",
                        "--settings=" + getClass().getResource("/settings-test.xml").getFile(),
                        "-Dmaven.repo.local=" + folder.toString(),
                        "-Prun-its",
                        "--file",
                        target.getCanonicalPath() });

        assertTrue(systemOutRule.getLog().contains("Explicitly activating [run-its]"));
        assertTrue(
                systemOutRule.getLog()
                        .contains("Will not scan all profiles and returning active profiles of [run-its]"));
    }

    @Test
    public void checkLocalRepositoryWithSettings() throws Exception {
        Cli c = new Cli();
        c.run(new String[] { "-settings=" + getClass().getResource("/settings-test.xml").getFile() });

        ManipulationSession session = (ManipulationSession) FieldUtils.readField(c, "session", true);
        MavenSession ms = (MavenSession) FieldUtils.readField(session, "mavenSession", true);

        assertEquals(
                ms.getRequest().getLocalRepository().getBasedir(),
                ms.getRequest().getLocalRepositoryPath().toString());
    }

    @Test
    public void checkLocalRepositoryWithExplicitMavenRepo() throws Exception {
        File folder = temp.newFolder();
        Cli c = new Cli();
        c.run(new String[] { "-Dmaven.repo.local=" + folder.toString() });

        ManipulationSession session = (ManipulationSession) FieldUtils.readField(c, "session", true);
        MavenSession ms = (MavenSession) FieldUtils.readField(session, "mavenSession", true);

        assertEquals(
                ms.getRequest().getLocalRepository().getBasedir(),
                ms.getRequest().getLocalRepositoryPath().toString());
    }

    @Test
    public void checkLocalRepositoryWithExplicitMavenRepoAndSettings() throws Exception {
        File folder = temp.newFolder();
        Cli c = new Cli();
        c.run(
                new String[] {
                        "--settings=" + getClass().getResource("/settings-test.xml").getFile(),
                        "-Dmaven.repo.local=" + folder.toString() });

        ManipulationSession session = (ManipulationSession) FieldUtils.readField(c, "session", true);
        MavenSession ms = (MavenSession) FieldUtils.readField(session, "mavenSession", true);

        assertEquals(ms.getLocalRepository().getBasedir(), folder.toString());
        assertEquals(
                ms.getRequest().getLocalRepository().getBasedir(),
                ms.getRequest().getLocalRepositoryPath().toString());
    }

    @Test
    public void checkUnknownProperty() throws Exception {
        File folder = temp.newFolder();
        File target = temp.newFile();
        // Locate the PME project pom file. Use that to verify inheritance tracking.
        Files.copy(
                Paths.get(INTEGRATION_TEST.toString(), "pom.xml"),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING);

        Cli c = new Cli();
        c.run(
                new String[] {
                        "-d",
                        "--settings=" + getClass().getResource("/settings-test.xml").getFile(),
                        "-Dmaven.repo.local=" + folder.toString(),
                        "-DUNKNOWN_PROPERTY=DUMMY",
                        "-Prun-its",
                        "--file",
                        target.getCanonicalPath() });

        assertTrue(systemOutRule.getLog().contains("Unknown configuration value UNKNOWN_PROPERTY"));
    }

    @Test
    public void checkPropertyWithoutValue() throws Exception {
        File folder = temp.newFolder();
        File target = temp.newFile();
        // Locate the PME project pom file. Use that to verify inheritance tracking.
        Files.copy(
                Paths.get(INTEGRATION_TEST.toString(), "pom.xml"),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING);

        Cli c = new Cli();
        c.run(
                new String[] {
                        "-d",
                        "--settings=" + getClass().getResource("/settings-test.xml").getFile(),
                        "-Dmaven.repo.local=" + folder.toString(),
                        "-DBOOLEAN_PROPERTY",
                        "-Prun-its",
                        "--file",
                        target.getCanonicalPath() });

        assertTrue(systemOutRule.getLog().contains("BOOLEAN_PROPERTY=true"));
    }

    @Test
    public void checkVersionAndExit() {
        new Cli().run(new String[] { "--version" });
        assertTrue(systemOutRule.getLog().contains("PME CLI"));
    }

    @Test
    public void checkDependencies() {
        final File root = Paths.get(ROOT_DIRECTORY.toString(), "pom.xml").toFile();

        new Cli().run(new String[] { "--printProjectDeps", "--file", root.getAbsolutePath() });

        // Strip out PME itself otherwise it causes issues on releasing a new version.
        String cliOutput = systemOutRule.getLogWithNormalizedLineSeparator()
                .replaceAll("org.jboss.pnc.maven-manipulator:pom-manipulation-.*\\n", "");
        System.out.println(cliOutput);

        assertTrue(cliOutput.contains("Found 84"));
        assertTrue(
                cliOutput.matches(
                        "(?s).*"
                                + "ch.qos.logback:logback-classic:1.[.\\d+]+\\s+                                   jar                                     compile             \n"
                                + "ch.qos.logback:logback-core:1.[.\\d+]+\\s+                                      jar                                     compile             \n"
                                + "com.diffplug.spotless:spotless-maven-plugin:[.\\d+]+\\s+                        maven-plugin                                                \n"
                                + "com.fasterxml.jackson.core:jackson-annotations:2.[.\\w+|\\a|-]+\\s+             jar                                     compile             \n"
                                + "com.fasterxml.jackson.core:jackson-core:2.[.\\w+|-]+\\s+                        jar                                     compile             \n"
                                + "com.fasterxml.jackson.core:jackson-databind:2.[.\\w+|-]+\\s+                    jar                                     compile             \n"
                                + "com.github.olivergondza:maven-jdk-tools-wrapper:0.1                             jar                                     compile             \n"
                                + "com.github.stefanbirkner:system-rules:1.[.\\d+]+\\s+                            jar                                     test                \n"
                                + "com.google.guava:guava:33.[.\\d+]+-jre\\s+                                      jar                                     compile             \n"
                                + "com.google.inject:guice:5.1.0                                                   jar                                     compile             \n"
                                + "com.jayway.jsonpath:json-path:2.[.\\d+]+\\s+                                    jar                                     compile             \n"
                                + "com.konghq:unirest-java:3.[.\\d+]+\\s+                                          jar                                     compile             \n"
                                + "com.konghq:unirest-objectmapper-jackson:3.[.\\d+]+\\s+                          jar                                     compile             \n"
                                + "com.redhat.rcm:redhat-releng-tools:12\\s+                                       pom                                     compile             \n"
                                + "com.redhat.resilience.otel:opentelemetry-ext-cli-java:1.[.\\d+]+\\s+            jar                                     compile             \n"
                                + "com.soebes.maven.plugins:iterator-maven-plugin:0.[.\\d+]+\\s+                   maven-plugin                                                \n"
                                + "com.squareup:javapoet:1.[.\\d+]+\\s+                                            jar                                     compile             \n"
                                + "commons-codec:commons-codec:1.[.\\d+]+\\s+                                      jar                                     compile             \n"
                                + "commons-io:commons-io:[.\\d+]+\\s+                                              jar                                     compile             \n"
                                + "info.picocli:picocli:4.[.\\d+]+\\s+                                             jar                                     compile             \n"
                                + "javax.inject:javax.inject:1                                                     jar                                     compile             \n"
                                + "junit:junit:4[.\\d+]+\\s+                                                       jar                                     test                \n"
                                + "org.apache.commons:commons-lang3:3.[.\\d+]+\\s+                                 jar                                     compile             \n"
                                + "org.apache.httpcomponents:httpclient:4.[.\\d+]+\\s+                             jar                                     compile             \n"
                                + "org.apache.ivy:ivy:[.\\d+]+\\s+                                                 jar                                     compile             \n"
                                + "org.apache.maven:apache-maven:3.[.\\d+]+\\s+                                    zip                 bin                 test                \n"
                                + "org.apache.maven:maven-artifact:3.[.\\d+]+\\s+                                  jar                                     provided            \n"
                                + "org.apache.maven:maven-compat:3.[.\\d+]+\\s+                                    jar                                     provided            \n"
                                + "org.apache.maven:maven-core:3.[.\\d+]+\\s+                                      jar                                     provided            \n"
                                + "org.apache.maven:maven-model:3.[.\\d+]+\\s+                                     jar                                     provided            \n"
                                + "org.apache.maven:maven-model-builder:3.[.\\d+]+\\s+                             jar                                     provided            \n"
                                + "org.apache.maven:maven-settings:3.[.\\d+]+\\s+                                  jar                                     provided            \n"
                                + "org.apache.maven:maven-settings-builder:3.[.\\d+]+\\s+                          jar                                     provided            \n"
                                + "org.apache.maven.plugins:maven-assembly-plugin:2.2-beta-5                       maven-plugin                                                \n"
                                + "org.apache.maven.plugins:maven-dependency-plugin:3.[.\\d+]+\\s+                 maven-plugin                                                \n"
                                + "org.apache.maven.plugins:maven-invoker-plugin:3.[.\\d+]+\\s+                    maven-plugin                                                \n"
                                + "org.apache.maven.plugins:maven-jar-plugin:2.4                                   maven-plugin                                                \n"
                                + "org.apache.maven.plugins:maven-project-info-reports-plugin:3.[.\\d+]+\\s+       maven-plugin                                                \n"
                                + "org.apache.maven.plugins:maven-release-plugin:2.3.2                             maven-plugin                                                \n"
                                + "org.apache.maven.plugins:maven-resources-plugin:2.6                             maven-plugin                                                \n"
                                + "org.apache.maven.plugins:maven-shade-plugin:3.[.\\d+]+\\s+                      maven-plugin                                                \n"
                                + "org.apache.maven.plugins:maven-surefire-plugin:2.12.4                           maven-plugin                                                \n"
                                + "org.apache.maven.release:maven-release-api:3.[.\\d+]+\\s+                       jar                                     compile             \n"
                                + "org.apache.maven.release:maven-release-manager:3.[.\\d+]+\\s+                   jar                                     compile             \n"
                                + "org.bsc.maven:maven-processor-plugin:3.3.3                                      maven-plugin                                                \n"
                                + "org.codehaus.groovy:groovy:[.\\d+]+\\s+                                         jar                                     compile             \n"
                                + "org.codehaus.groovy:groovy-json:[.\\d+]+\\s+                                    jar                                     compile             \n"
                                + "org.codehaus.groovy:groovy-xml:[.\\d+]+\\s+                                     jar                                     compile             \n"
                                + "org.codehaus.mojo:animal-sniffer-maven-plugin:1.[.\\d+]+\\s+                    maven-plugin                                                \n"
                                + "org.codehaus.plexus:plexus-interpolation:[.\\d+]+\\s+                           jar                                     provided            \n"
                                + "org.codehaus.plexus:plexus-utils:[.\\d+]+\\s+                                   jar                                     compile             \n"
                                + "org.commonjava.atlas:atlas-identities:1.1.8                                     jar                                     compile             \n"
                                + "org.commonjava.maven.galley:galley-api:1.21                                     jar                                     compile             \n"
                                + "org.commonjava.maven.galley:galley-core:1.21                                    jar                                     compile             \n"
                                + "org.commonjava.maven.galley:galley-maven:1.21                                   jar                                     compile             \n"
                                + "org.commonjava.maven.galley:galley-transport-filearc:1.21                       jar                                     compile             \n"
                                + "org.commonjava.maven.galley:galley-transport-httpclient:1.21                    jar                                     compile             \n"
                                + "org.eclipse.aether:aether-api:1.1.0                                             jar                                     provided            \n"
                                + "org.eclipse.jetty:jetty-server:9.[.\\d+]+[v\\d+]*\\s+                           jar                                     compile             \n"
                                + "org.eclipse.sisu:org.eclipse.sisu.plexus:0.9.0.M4                               jar                                     compile             \n"
                                + "org.goots.hiderdoclet:doclet:1.1                                                jar                                     compile             \n"
                                + "org.hamcrest:hamcrest-all:1.3                                                   jar                                     test                \n"
                                + "org.jacoco:jacoco-maven-plugin:0.8[.\\d+]+\\s+                                  maven-plugin                                                \n"
                                + "org.jboss.byteman:byteman-bmunit:4[.\\d+]+\\s+                                  jar                                     test                \n"
                                + "org.jboss.da:reports-model:2.[.\\d+]+\\s+                                       jar                                     compile             \n"
                                + "org.jdom:jdom2:2.[.\\d+]+\\s+                                                   jar                                     compile             \n"
                                + "org.projectlombok:lombok:1.[.\\d+]+\\s+                                         jar                                     provided            \n"
                                + "org.projectlombok:lombok-maven-plugin:1.[.\\d+]+\\s+                            maven-plugin                                                \n"
                                + "org.slf4j:slf4j-api:2.[.\\d+]+\\s+                                              jar                                     compile             \n"
                                + "org.xmlunit:xmlunit-core:2.[.\\d+]+\\s+                                         jar                                     test                \n"
                                + "org.xmlunit:xmlunit-matchers:2.[.\\d+]+\\s+                                     jar                                     test                \n"
                                + "org.yaml:snakeyaml:2.[.\\d+]+\\s+                                               jar                                     compile.*"));

    }

    @Test
    public void checkManipulatorOrder() {
        new Cli().run(new String[] { "--printManipulatorOrder" });
        assertTrue(systemOutRule.getLogWithNormalizedLineSeparator().contains("Manipulator order is:"));
        assertTrue(
                systemOutRule.getLogWithNormalizedLineSeparator()
                        .contains(
                                ""
                                        + "         1          InitialGroovyManipulator                \n"
                                        + "         2          RangeResolver                           \n"
                                        + "         4          RESTBOMCollector                        \n"
                                        + "         5          ProfileInjectionManipulator             \n"
                                        + "         6          SuffixManipulator                       \n"
                                        + "         7          RelocationManipulator                   \n"
                                        + "         8          DependencyInjectionManipulator          \n"
                                        + "         10         RESTCollector                           \n"
                                        + "         20         ProjectVersioningManipulator            \n"
                                        + "         25         ParentInjectionManipulator              \n"
                                        + "         30         PropertyManipulator                     \n"
                                        + "         35         PluginManipulator                       \n"
                                        + "         40         DependencyManipulator                   \n"
                                        + "         50         RepoAndReportingRemovalManipulator      \n"
                                        + "         51         DependencyRemovalManipulator            \n"
                                        + "         52         PluginRemovalManipulator                \n"
                                        + "         53         CentralAndNexusMavenPluginRemovalManipulator\n"
                                        + "         55         ProfileRemovalManipulator               \n"
                                        + "         60         PluginInjectingManipulator              \n"
                                        + "         65         RepositoryInjectionManipulator          \n"
                                        + "         70         ProjectVersionEnforcingManipulator      \n"
                                        + "         75         DistributionEnforcingManipulator        \n"
                                        + "         80         BOMBuilderManipulator                   \n"
                                        + "         90         JSONManipulator                         \n"
                                        + "         91         XMLManipulator                          \n"
                                        + "         99         FinalGroovyManipulator                  \n"));
    }

    @Test
    public void checkLogLevels() throws Exception {
        File folder = temp.newFolder();
        File target = new File(folder, "pom.xml");

        FileUtils.copyDirectory(
                ROOT_DIRECTORY.toFile(),
                folder,
                FileFilterUtils.or(DirectoryFileFilter.DIRECTORY, FileFilterUtils.suffixFileFilter("xml")));

        Cli c = new Cli();
        c.run(
                new String[] {
                        "--settings=" + getClass().getResource("/settings-test.xml").getFile(),
                        "-Dmaven.repo.local=" + folder.toString(),
                        "-Prun-its",
                        "--file",
                        target.getCanonicalPath() });
        assertTrue(systemOutRule.getLog().contains("Running manipulator"));

        systemOutRule.clearLog();

        c = new Cli();
        c.run(
                new String[] {
                        "-q",
                        "--settings=" + getClass().getResource("/settings-test.xml").getFile(),
                        "-Dmaven.repo.local=" + folder.toString(),
                        "-Prun-its",
                        "--file",
                        target.getCanonicalPath() });

        assertFalse(systemOutRule.getLog().contains("Running manipulator"));

        systemOutRule.clearLog();

        c = new Cli();
        c.run(
                new String[] {
                        "-t",
                        "--settings=" + getClass().getResource("/settings-test.xml").getFile(),
                        "-DdependencyRelocations.commons-io:commons0io@newGroupId:newArtifactId=1.0",
                        "-DversionSuffix=rebuild-1",
                        "-Dmaven.repo.local=" + folder.toString(),
                        "-Prun-its",
                        "--file",
                        target.getCanonicalPath() });

        assertTrue(systemOutRule.getLog().contains("Wildcard map "));
    }

    @Test
    public void invokeCLI() throws Exception {
        Cli c = new Cli();

        URL resource = CliTest.class.getResource("/test.pom");
        assertNotNull(resource);
        File pom = new File(resource.getFile());
        assertTrue(pom.exists());

        File pom1 = temp.newFile("pom.xml");
        FileUtils.copyFile(pom, pom1);

        int result = c.run(new String[] { "-f", pom1.toString() });
        assertTrue(systemOutRule.getLog().contains("Maven-Manipulation-Extension: Finished"));
        assertEquals(0, result);
    }

    @Test
    public void invokeCLIFails() {
        Cli c = new Cli();

        int result = c.run(new String[] { "-d", "-f", temp.getRoot().toString() + "/pom.xml" });
        assertTrue(systemOutRule.getLog().contains("PreparseGroovyManipulator: Nothing to do!"));
        assertTrue(systemOutRule.getLog().contains("Manipulation engine disabled"));
        assertEquals(10, result);
    }
}
