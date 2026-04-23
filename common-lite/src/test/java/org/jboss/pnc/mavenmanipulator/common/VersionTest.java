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
package org.jboss.pnc.mavenmanipulator.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class VersionTest {
    @Test
    public void testAppendQualifierSuffix() {
        assertEquals("1.0.foo", Version.appendQualifierSuffix("1.0", "foo"));
        assertEquals("1.0.0.Beta1-foo", Version.appendQualifierSuffix("1.0.0.Beta1", "foo"));
        assertEquals("1.0_Betafoo", Version.appendQualifierSuffix("1.0_Betafoo", "foo"));
        assertEquals("1.0_fooBeta-foo", Version.appendQualifierSuffix("1.0_fooBeta", "foo"));
        assertEquals("1.0-foo", Version.appendQualifierSuffix("1.0", "-foo"));
        assertEquals("1.0.0.Beta1_foo", Version.appendQualifierSuffix("1.0.0.Beta1", "_foo"));
        assertEquals("1.0_Beta-foo", Version.appendQualifierSuffix("1.0_Betafoo", "-foo"));
        assertEquals("1.0_Beta-foo", Version.appendQualifierSuffix("1.0_Beta.foo", "-foo"));
        assertEquals("jboss-1-GA-jboss", Version.appendQualifierSuffix("jboss-1-GA", "jboss"));
        assertEquals("1.2.jboss-1-SNAPSHOT", Version.appendQualifierSuffix("1.2", "jboss-1-SNAPSHOT"));
        assertEquals("1.2.jboss1-SNAPSHOT", Version.appendQualifierSuffix("1.2-SNAPSHOT", "jboss1"));
        assertEquals("1.2-jboss-2", Version.appendQualifierSuffix("1.2-jboss-1", "jboss-2"));
        assertEquals("1.2.jboss-2", Version.appendQualifierSuffix("1.2-jboss-1", ".jboss-2"));
        assertEquals("1.1.test_jdk7-SNAPSHOT", Version.appendQualifierSuffix("1.1-SNAPSHOT", ".test_jdk7-SNAPSHOT"));
        assertEquals("1.1-beta-1", Version.appendQualifierSuffix("1.1.beta-2", "-beta-1"));
        assertEquals("1.1.beta-2-foo-1", Version.appendQualifierSuffix("1.1.beta-2", "-foo-1"));
    }

    @Test
    public void testAppendQualifierSuffix_WithProperty() {
        assertEquals(
                "${project.version}-foo",
                Version.appendQualifierSuffix(
                        Version.PROJECT_VERSION,
                        "foo"));
        assertEquals("1.0.${micro}.foo", Version.appendQualifierSuffix("1.0.${micro}", ".foo"));
        assertEquals(
                "${project.version}-10",
                Version.setBuildNumber(
                        Version.PROJECT_VERSION,
                        "10"));
        assertEquals("${project.version}-foo-10", Version.setBuildNumber("${project.version}-foo", "10"));
    }

    @Test
    public void testAppendQualifierSuffix_MulitpleTimes() {

        String version = "1.2.0";
        version = Version.appendQualifierSuffix(version, "jboss-1");
        version = Version.appendQualifierSuffix(version, "foo");
        assertEquals("1.2.0.jboss-1-foo", version);

        version = "1.2";
        version = Version.appendQualifierSuffix(version, "jboss-1");
        version = Version.appendQualifierSuffix(version, "jboss-2");
        version = Version.getOsgiVersion(version);
        assertEquals("1.2.0.jboss-2", version);
    }

    @Test
    public void testFindHighestMatchingBuildNumber() {
        String version = "1.2.0.Final-foo";
        final Set<String> versionSet = new HashSet<>();
        versionSet.add("1.2.0.Final-foo-1");
        versionSet.add("1.2.0.Final-foo-2");
        assertEquals(2, Version.findHighestMatchingBuildNumber(version, versionSet));

        version = "1.2.0.Final-foo10";
        versionSet.clear();
        versionSet.add("1.2.0.Final-foo-1");
        versionSet.add("1.2.0.Final-foo-2");
        assertEquals(2, Version.findHighestMatchingBuildNumber(version, versionSet));

        version = Version.appendQualifierSuffix("0.0.4", "redhat-0");
        versionSet.clear();
        versionSet.add("0.0.1");
        versionSet.add("0.0.2");
        versionSet.add("0.0.3");
        versionSet.add("0.0.4");
        versionSet.add("0.0.4.redhat-2");
        assertEquals(2, Version.findHighestMatchingBuildNumber(version, versionSet));

        version = "1.2-foo-1";
        versionSet.clear();
        versionSet.add("1.2-foo-4");
        assertEquals(4, Version.findHighestMatchingBuildNumber(version, versionSet));

        version = "1.2";
        versionSet.clear();
        versionSet.add("1.2");
        assertEquals(0, Version.findHighestMatchingBuildNumber(version, versionSet));
    }

    @Test
    public void testFindHighestMatchingBuildNumber_OSGi() {
        String version = "1.2.0.Final-foo";
        final Set<String> versionSet = new HashSet<>();
        versionSet.add("1.2.0.Final-foo-10");
        versionSet.add("1.2.0.Final-foo-2");
        assertEquals(10, Version.findHighestMatchingBuildNumber(version, versionSet));
        versionSet.clear();
    }

    @Test
    public void testFindHighestMatchingBuildNumber_Fuse() {
        String version = "2.10.0-000164.fuse-000001-redhat";
        final Set<String> versionSet = new HashSet<>();
        versionSet.add("2.10.0.redhat-000022");
        versionSet.add("2.10.0.000164-fuse-000001-redhat-2");
        versionSet.add("2.10.0.fuse-000022-redhat-2");
        versionSet.add("2.10.0.fuse-000022-redhat-1");
        assertEquals(2, Version.findHighestMatchingBuildNumber(version, versionSet));
        versionSet.clear();
    }

    @Test
    public void testFindHighestMatchingBuildNumber_ZeroFill() {
        String majorOnlyVersion = "7";
        String version = Version.appendQualifierSuffix(majorOnlyVersion, "redhat");
        final Set<String> versionSet = new HashSet<>();
        versionSet.add("7.0.0.redhat-2");
        assertEquals(2, Version.findHighestMatchingBuildNumber(version, versionSet));

        String majorMinorVersion = "7.1";
        version = Version.appendQualifierSuffix(majorMinorVersion, "redhat");
        versionSet.clear();
        versionSet.add("7.1.0.redhat-4");
        assertEquals(4, Version.findHighestMatchingBuildNumber(version, versionSet));
    }

    @Test
    public void testGetBuildNumber() {
        assertEquals("", Version.getBuildNumber("1.0-SNAPSHOT"));
        assertEquals("1", Version.getBuildNumber("1.0.0.Beta1"));
        assertEquals("2", Version.getBuildNumber("1.0.beta.1-2"));
        assertEquals("3", Version.getBuildNumber("Beta3"));
        assertEquals("", Version.getBuildNumber("1.x.2.beta4t"));
        assertEquals("11", Version.getBuildNumber("1.0.0.Beta11-SNAPSHOT"));
        assertEquals("10", Version.getBuildNumber("1.0.0.Beta1-SNAPSHOT-10"));
    }

    @Test
    public void testGetMMM() {
        assertEquals("1.0", Version.getMMM("1.0-SNAPSHOT"));
        assertEquals("1.0.0", Version.getMMM("1.0.0.Beta1"));
        assertEquals("1.0", Version.getMMM("1.0.beta.1"));
        assertEquals("", Version.getMMM("Beta1"));
        assertEquals("1", Version.getMMM("1.x.2.beta1"));
        assertEquals("7.18.1", Version.getMMM("7.18.1.20190312-prod"));
    }

    @Test
    public void testGetOsgiMMM() {
        assertEquals("1.0", Version.getOsgiMMM("1.0", false));
        assertEquals("1.0.0", Version.getOsgiMMM("1.0", true));
        assertEquals("13.2.43", Version.getOsgiMMM("13_2-43", false));
        assertEquals("1", Version.getOsgiMMM("1", false));
        assertEquals("2.0.0", Version.getOsgiMMM("2", true));
        assertEquals("", Version.getOsgiMMM("beta1", false));
        assertEquals("", Version.getOsgiMMM("GA-1-GA-foo", true));
        assertEquals("1.2.3", Version.getOsgiMMM("1.2.3.foo-final-1", true));
        assertEquals("1.2.0", Version.getOsgiMMM("1.2.final-1", true));
    }

    @Test
    public void testGetOsgiVersion() {
        assertEquals("1.0", Version.getOsgiVersion("1.0"));
        assertEquals("1.2.3", Version.getOsgiVersion("1_2_3"));
        assertEquals("1.2.3.beta4", Version.getOsgiVersion("1-2.3beta4"));
        assertEquals("1.2.3.4-beta", Version.getOsgiVersion("1.2.3.4.beta"));
        assertEquals("12.4.0.beta", Version.getOsgiVersion("12.4-beta"));
        assertEquals("-beta1", Version.getOsgiVersion("-beta1"));
        assertEquals("12.0.0.beta1_3-5-hello", Version.getOsgiVersion("12.beta1_3-5.hello"));
        assertEquals(
                "1.0.0.Final-t20170516223844555-redhat-1",
                Version.getOsgiVersion("1.0.0.Final-t20170516223844555-redhat-1"));
        assertEquals("1.0.3.4-beta2-1", Version.getOsgiVersion("1-0-3.4-beta2.1"));
        assertEquals("4.8.2", Version.getOsgiVersion("4.8-2"));
        assertEquals("4.5.1.1", Version.getOsgiVersion("4.5.1-1"));
        assertEquals("4.5.1.1-redhat-1", Version.getOsgiVersion("4.5.1-1.redhat-1"));
        assertEquals("1.1.0.foo", Version.getOsgiVersion("1.1.foo"));

        String o = "1.1-foo";
        assertEquals(
                "1.1.0.foo",
                Version.getOsgiVersion(
                        Version.getOsgiMMM(o, true) + "." + Version.getQualifier(o)));
        o = "1.2";
        assertEquals(
                "1.2.0",
                Version.getOsgiVersion(
                        Version.getOsgiMMM(o, true) + "." + Version.getQualifier(o)));
    }

    @Test
    public void testGetQualifier() {
        assertEquals("SNAPSHOT", Version.getQualifier("1.0-SNAPSHOT"));
        assertEquals("-SNAPSHOT", Version.getQualifierWithDelim("1.0-SNAPSHOT"));

        assertEquals("Beta1", Version.getQualifier("1.0.0.Beta1"));
        assertEquals(".Beta1", Version.getQualifierWithDelim("1.0.0.Beta1"));

        assertEquals("beta.1", Version.getQualifier("1.0.beta.1"));
        assertEquals(".beta.1", Version.getQualifierWithDelim("1.0.beta.1"));

        assertEquals("Beta1", Version.getQualifier("Beta1"));
        assertEquals("Beta1", Version.getQualifierWithDelim("Beta1"));

        assertEquals("x.2.beta1", Version.getQualifier("1.x.2.beta1"));
        assertEquals(".x.2.beta1", Version.getQualifierWithDelim("1.x.2.beta1"));

        assertEquals("", Version.getQualifier("1.2"));
        assertEquals("", Version.getQualifierWithDelim("1.2"));

        assertEquals("beta-SNAPSHOT-1", Version.getQualifier("1.5-3_beta-SNAPSHOT-1"));
        assertEquals("_beta-SNAPSHOT-1", Version.getQualifierWithDelim("1.5-3_beta-SNAPSHOT-1"));
        assertEquals("beta-SNAPSHOT", Version.getQualifierBase("1.5-3_beta-SNAPSHOT-1"));
        assertEquals("1.5.3.beta-SNAPSHOT-1", Version.getOsgiVersion("1.5-3_beta-SNAPSHOT-1"));

        assertEquals("beta-SNAPSHOT-1", Version.getQualifier("_beta-SNAPSHOT-1"));
        assertEquals("_beta-SNAPSHOT-1", Version.getQualifierWithDelim("_beta-SNAPSHOT-1"));
    }

    @Test
    public void testHasQualifier() {
        assertTrue(Version.hasQualifier("1.0-SNAPSHOT"));
        assertFalse(Version.hasQualifier("1.0.0"));
        assertTrue(Version.hasQualifier("1.0.0.Final"));
        assertTrue(Version.hasQualifier("1.0.Final-rebuild"));
        assertTrue(Version.hasQualifier("1.0.Final-rebuild-1"));
        assertTrue(Version.hasQualifier("7.18.1.20190312-prod"));
    }

    @Test
    public void testHasBuildNum() {
        assertFalse(Version.hasBuildNumber("7.18.1.20190312-prod"));
        assertFalse(Version.hasBuildNumber("1.0-SNAPSHOT"));
        assertFalse(Version.hasBuildNumber("1.0.0"));
        assertFalse(Version.hasBuildNumber("1.0.0.Final"));
        assertFalse(Version.hasBuildNumber("1.0.Final-rebuild"));
        assertTrue(Version.hasBuildNumber("1.0.Final-rebuild-1"));
    }

    @Test
    public void testGetQualifierBase() {
        assertEquals("", Version.getQualifierBase("1.0-SNAPSHOT"));
        assertEquals("Beta", Version.getQualifierBase("1.0.0.Beta1"));
        assertEquals("jboss-test", Version.getQualifierBase("1.0.0.jboss-test-SNAPSHOT"));
        assertEquals("${project.version}-test", Version.getQualifierBase("${project.version}-test-1"));

        assertEquals("Final-Beta", Version.getQualifierBase("Final-Beta10"));
        assertEquals("Beta10-rebuild", Version.getQualifierBase("1.0.0.Beta10-rebuild-3"));
        assertEquals("Final-Beta", Version.getQualifierBase("1.0.0.Final-Beta-1"));
        assertEquals("Final-Beta", Version.getQualifierBase("1.0.0.Final-Beta10"));
        assertEquals("TWENTY", Version.getQualifierBase("4.8-TWENTY"));
        assertEquals("", Version.getQualifierBase("4.8-2"));
    }

    @Test
    public void testGetSnapshot() {
        assertEquals("SNAPSHOT", Version.getSnapshot("1.0-SNAPSHOT"));
        assertEquals("-SNAPSHOT", Version.getSnapshotWithDelim("1.0-SNAPSHOT"));

        assertEquals("SNAPSHOT", Version.getSnapshot("1.0.0.SNAPSHOT"));
        assertEquals(".SNAPSHOT", Version.getSnapshotWithDelim("1.0.0.SNAPSHOT"));

        assertEquals("snapshot", Version.getSnapshot("1.0.0.Beta1-snapshot"));
        assertEquals("-snapshot", Version.getSnapshotWithDelim("1.0.0.Beta1-snapshot"));

        assertEquals("snaPsHot", Version.getSnapshot("1_snaPsHot"));
        assertEquals("_snaPsHot", Version.getSnapshotWithDelim("1_snaPsHot"));

        assertEquals("", Version.getSnapshot("1.0"));
        assertEquals("", Version.getSnapshotWithDelim("1.0"));

        assertEquals("", Version.getSnapshot("1.0-foo"));
        assertEquals("", Version.getSnapshotWithDelim("1.0-foo"));
    }

    @Test
    public void testIsSnapshot() {
        assertTrue(Version.isSnapshot("1.0-SNAPSHOT"));
        assertTrue(Version.isSnapshot("1.0-snapshot"));
        assertTrue(Version.isSnapshot("1.0.SnapsHot"));
        assertTrue(Version.isSnapshot("1.0.0snapshot"));
        assertTrue(Version.isSnapshot("snapshot"));

        assertFalse(Version.isSnapshot("1"));
        assertFalse(Version.isSnapshot("1.0-snapsho"));
        assertFalse(Version.isSnapshot("1.0.beta1-"));
    }

    @Test
    public void testIsEmpty() {
        assertTrue(Version.isEmpty(null));
        assertTrue(Version.isEmpty(""));
        assertTrue(Version.isEmpty("  " + System.lineSeparator()));

        assertFalse(Version.isEmpty("a"));
        assertFalse(Version.isEmpty(" a " + System.lineSeparator()));
    }

    @Test
    public void testRemoveSnapshot() {
        assertEquals("1.0", Version.removeSnapshot("1.0-SNAPSHOT"));
        assertEquals("1.0.0.Beta1", Version.removeSnapshot("1.0.0.Beta1_snapshot"));
        assertEquals("1", Version.removeSnapshot("1.snaPsHot"));
        assertEquals("", Version.removeSnapshot("SNAPSHOT"));
        assertEquals("1.0.snapshot.beta1", Version.removeSnapshot("1.0.snapshot.beta1"));

        assertEquals("1.0.0.redhat-1", Version.removeSnapshot("1.0.0.redhat-1"));

    }

    @Test
    public void testSetBuildNumber() {
        assertEquals("1.0.beta2", Version.setBuildNumber("1.0.beta1", "2"));
        assertEquals("1.0_2-Beta41-SNAPSHOT", Version.setBuildNumber("1.0_2-Beta1-SNAPSHOT", "41"));
        assertEquals("1.0.2.3", Version.setBuildNumber("1.0.2.1", "3"));
        assertEquals("1.0.2.3", Version.setBuildNumber("1.0.2", "3"));
        assertEquals("1.0.2", Version.setBuildNumber("1.0", "2"));
        assertEquals("1.0-alpha-001", Version.setBuildNumber("1.0-alpha", "001"));
    }

    @Test
    public void testSetSnapshot() {
        assertEquals("1.0-SNAPSHOT", Version.setSnapshot("1.0-SNAPSHOT", true));
        assertEquals("1.0", Version.setSnapshot("1.0-SNAPSHOT", false));
        assertEquals("1.1-SNAPSHOT", Version.setSnapshot("1.1", true));
        assertEquals("1.1", Version.setSnapshot("1.1", false));
        assertEquals("1.2.jboss-1-SNAPSHOT", Version.setSnapshot("1.2.jboss-1", true));
        assertEquals("1.2.jboss-1", Version.setSnapshot("1.2.jboss-1", false));
        assertEquals("1.0.0.Beta1_snapshot", Version.setSnapshot("1.0.0.Beta1_snapshot", true));
        assertEquals("1.0.0.Beta1", Version.setSnapshot("1.0.0.Beta1_snapshot", false));
        assertEquals("1.snaPsHot", Version.setSnapshot("1.snaPsHot", true));
        assertEquals("1", Version.setSnapshot("1.snaPsHot", false));
        assertEquals("SNAPSHOT", Version.setSnapshot("SNAPSHOT", true));
        assertEquals("", Version.setSnapshot("SNAPSHOT", false));
        assertEquals("1.0.snapshot.beta1-SNAPSHOT", Version.setSnapshot("1.0.snapshot.beta1", true));
        assertEquals("1.0.snapshot.beta1", Version.setSnapshot("1.0.snapshot.beta1", false));
    }

    @Test
    public void testRemoveLeadingDelimiters() {
        assertEquals("1.2", Version.removeLeadingDelimiter(".1.2"));
        assertEquals("Beta1", Version.removeLeadingDelimiter("_Beta1"));
        assertEquals("1.0-SNAPSHOT", Version.removeLeadingDelimiter("1.0-SNAPSHOT"));
        assertEquals("1.0_foo-", Version.removeLeadingDelimiter("1.0_foo-"));
    }

    @Test
    public void testValidOsgi() {
        assertTrue(Version.isValidOSGi("1"));
        assertTrue(Version.isValidOSGi("1.2"));
        assertTrue(Version.isValidOSGi("1.2.3"));
        assertTrue(Version.isValidOSGi("1.2.3.beta1"));
        assertTrue(Version.isValidOSGi("1.2.3.beta_1"));
        assertTrue(Version.isValidOSGi("1.2.3.beta-1"));
        assertTrue(Version.isValidOSGi("1.0.0.beta"));
        assertTrue(Version.isValidOSGi("0.0.1"));

        assertFalse(Version.isValidOSGi("1.2.3.beta|1"));
        assertFalse(Version.isValidOSGi("1.2.3.beta^1"));
        assertFalse(Version.isValidOSGi("1.2.3.beta.1"));
        assertFalse(Version.isValidOSGi("1.2.beta1"));
        assertFalse(Version.isValidOSGi("1beta"));
        assertFalse(Version.isValidOSGi("beta1"));
    }

    @Test
    public void testTimestampedVersion() {
        String v = "1.0.0.t20170216-223844-555-redhat-1";
        assertEquals("1.0.0", Version.getMMM(v));
        assertEquals("1.0.0", Version.getOsgiMMM(v, false));
        assertEquals(Integer.parseInt("1"), Version.getIntegerBuildNumber(v));
        assertEquals("t20170216-223844-555-redhat-1", Version.getQualifier(v));
        assertEquals(".t20170216-223844-555-redhat-1", Version.getQualifierWithDelim(v));
        assertEquals("t20170216-223844-555-redhat", Version.getQualifierBase(v));

        v = "1.0.t-20170216-223844-555-rebuild-5";
        assertEquals("1.0", Version.getMMM(v));
        assertEquals("1.0.0", Version.getOsgiMMM(v, true));
        assertEquals(Integer.parseInt("5"), Version.getIntegerBuildNumber(v));
        assertEquals("t-20170216-223844-555-rebuild-5", Version.getQualifier(v));
        assertEquals(".t-20170216-223844-555-rebuild-5", Version.getQualifierWithDelim(v));
        assertEquals("t-20170216-223844-555-rebuild", Version.getQualifierBase(v));
    }
}
