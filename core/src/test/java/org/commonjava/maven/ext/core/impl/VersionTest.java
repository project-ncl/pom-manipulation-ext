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
package org.commonjava.maven.ext.core.impl;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.commonjava.maven.ext.core.state.VersioningState;
import org.junit.Test;

public class VersionTest {
    @Test
    public void testAppendQualifierSuffix() {
        assertThat(Version.appendQualifierSuffix("1.0", "foo"), equalTo("1.0.foo"));
        assertThat(Version.appendQualifierSuffix("1.0.0.Beta1", "foo"), equalTo("1.0.0.Beta1-foo"));
        assertThat(Version.appendQualifierSuffix("1.0_Betafoo", "foo"), equalTo("1.0_Betafoo"));
        assertThat(Version.appendQualifierSuffix("1.0_fooBeta", "foo"), equalTo("1.0_fooBeta-foo"));
        assertThat(Version.appendQualifierSuffix("1.0", "-foo"), equalTo("1.0-foo"));
        assertThat(Version.appendQualifierSuffix("1.0.0.Beta1", "_foo"), equalTo("1.0.0.Beta1_foo"));
        assertThat(Version.appendQualifierSuffix("1.0_Betafoo", "-foo"), equalTo("1.0_Beta-foo"));
        assertThat(Version.appendQualifierSuffix("1.0_Beta.foo", "-foo"), equalTo("1.0_Beta-foo"));
        assertThat(Version.appendQualifierSuffix("jboss-1-GA", "jboss"), equalTo("jboss-1-GA-jboss"));
        assertThat(Version.appendQualifierSuffix("1.2", "jboss-1-SNAPSHOT"), equalTo("1.2.jboss-1-SNAPSHOT"));
        assertThat(Version.appendQualifierSuffix("1.2-SNAPSHOT", "jboss1"), equalTo("1.2.jboss1-SNAPSHOT"));
        assertThat(Version.appendQualifierSuffix("1.2-jboss-1", "jboss-2"), equalTo("1.2-jboss-2"));
        assertThat(Version.appendQualifierSuffix("1.2-jboss-1", ".jboss-2"), equalTo("1.2.jboss-2"));
        assertThat(
                Version.appendQualifierSuffix("1.1-SNAPSHOT", ".test_jdk7-SNAPSHOT"),
                equalTo("1.1.test_jdk7-SNAPSHOT"));
        assertThat(Version.appendQualifierSuffix("1.1.beta-2", "-beta-1"), equalTo("1.1-beta-1"));
        assertThat(Version.appendQualifierSuffix("1.1.beta-2", "-foo-1"), equalTo("1.1.beta-2-foo-1"));
    }

    @Test
    public void testAppendQualifierSuffix_WithProperty() {
        assertThat(Version.appendQualifierSuffix(Version.PROJECT_VERSION, "foo"), equalTo("${project.version}-foo"));
        assertThat(Version.appendQualifierSuffix("1.0.${micro}", ".foo"), equalTo("1.0.${micro}.foo"));
        assertThat(Version.setBuildNumber(Version.PROJECT_VERSION, "10"), equalTo("${project.version}-10"));
        assertThat(Version.setBuildNumber("${project.version}-foo", "10"), equalTo("${project.version}-foo-10"));
    }

    @Test
    public void testAppendQualifierSuffix_MulitpleTimes() {

        String version = "1.2.0";
        version = Version.appendQualifierSuffix(version, "jboss-1");
        version = Version.appendQualifierSuffix(version, "foo");
        assertThat(version, equalTo("1.2.0.jboss-1-foo"));

        version = "1.2";
        version = Version.appendQualifierSuffix(version, "jboss-1");
        version = Version.appendQualifierSuffix(version, "jboss-2");
        version = Version.getOsgiVersion(version);
        assertThat(version, equalTo("1.2.0.jboss-2"));
    }

    @Test
    public void testFindHighestMatchingBuildNumber() {
        String version = "1.2.0.Final-foo";
        final Set<String> versionSet = new HashSet<>();
        versionSet.add("1.2.0.Final-foo-1");
        versionSet.add("1.2.0.Final-foo-2");
        assertThat(Version.findHighestMatchingBuildNumber(version, versionSet), equalTo(2));

        version = "1.2.0.Final-foo10";
        versionSet.clear();
        versionSet.add("1.2.0.Final-foo-1");
        versionSet.add("1.2.0.Final-foo-2");
        assertThat(Version.findHighestMatchingBuildNumber(version, versionSet), equalTo(2));

        version = Version.appendQualifierSuffix("0.0.4", "redhat-0");
        versionSet.clear();
        versionSet.add("0.0.1");
        versionSet.add("0.0.2");
        versionSet.add("0.0.3");
        versionSet.add("0.0.4");
        versionSet.add("0.0.4.redhat-2");
        assertThat(Version.findHighestMatchingBuildNumber(version, versionSet), equalTo(2));

        version = "1.2-foo-1";
        versionSet.clear();
        versionSet.add("1.2-foo-4");
        assertThat(Version.findHighestMatchingBuildNumber(version, versionSet), equalTo(4));

        version = "1.2";
        versionSet.clear();
        versionSet.add("1.2");
        assertThat(Version.findHighestMatchingBuildNumber(version, versionSet), equalTo(0));
    }

    @Test
    public void testFindHighestMatchingBuildNumber_OSGi() {
        String version = "1.2.0.Final-foo";
        final Set<String> versionSet = new HashSet<>();
        versionSet.add("1.2.0.Final-foo-10");
        versionSet.add("1.2.0.Final-foo-2");
        assertThat(Version.findHighestMatchingBuildNumber(version, versionSet), equalTo(10));
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
        assertThat(Version.findHighestMatchingBuildNumber(version, versionSet), equalTo(2));
        versionSet.clear();
    }

    @Test
    public void testFindHighestMatchingBuildNumber_ZeroFill() {
        String majorOnlyVersion = "7";
        String version = Version.appendQualifierSuffix(majorOnlyVersion, "redhat");
        final Set<String> versionSet = new HashSet<>();
        versionSet.add("7.0.0.redhat-2");
        assertThat(Version.findHighestMatchingBuildNumber(version, versionSet), equalTo(2));

        String majorMinorVersion = "7.1";
        version = Version.appendQualifierSuffix(majorMinorVersion, "redhat");
        versionSet.clear();
        versionSet.add("7.1.0.redhat-4");
        assertThat(Version.findHighestMatchingBuildNumber(version, versionSet), equalTo(4));
    }

    @Test
    public void testGetBuildNumber() {
        assertThat(Version.getBuildNumber("1.0-SNAPSHOT"), equalTo(""));
        assertThat(Version.getBuildNumber("1.0.0.Beta1"), equalTo("1"));
        assertThat(Version.getBuildNumber("1.0.beta.1-2"), equalTo("2"));
        assertThat(Version.getBuildNumber("Beta3"), equalTo("3"));
        assertThat(Version.getBuildNumber("1.x.2.beta4t"), equalTo(""));
        assertThat(Version.getBuildNumber("1.0.0.Beta11-SNAPSHOT"), equalTo("11"));
        assertThat(Version.getBuildNumber("1.0.0.Beta1-SNAPSHOT-10"), equalTo("10"));
    }

    @Test
    public void testGetMMM() {
        assertThat(Version.getMMM("1.0-SNAPSHOT"), equalTo("1.0"));
        assertThat(Version.getMMM("1.0.0.Beta1"), equalTo("1.0.0"));
        assertThat(Version.getMMM("1.0.beta.1"), equalTo("1.0"));
        assertThat(Version.getMMM("Beta1"), equalTo(""));
        assertThat(Version.getMMM("1.x.2.beta1"), equalTo("1"));
        assertThat(Version.getMMM("7.18.1.20190312-prod"), equalTo("7.18.1"));
    }

    @Test
    public void testGetOsgiMMM() {
        assertThat(Version.getOsgiMMM("1.0", false), equalTo("1.0"));
        assertThat(Version.getOsgiMMM("1.0", true), equalTo("1.0.0"));
        assertThat(Version.getOsgiMMM("13_2-43", false), equalTo("13.2.43"));
        assertThat(Version.getOsgiMMM("1", false), equalTo("1"));
        assertThat(Version.getOsgiMMM("2", true), equalTo("2.0.0"));
        assertThat(Version.getOsgiMMM("beta1", false), equalTo(""));
        assertThat(Version.getOsgiMMM("GA-1-GA-foo", true), equalTo(""));
        assertThat(Version.getOsgiMMM("1.2.3.foo-final-1", true), equalTo("1.2.3"));
        assertThat(Version.getOsgiMMM("1.2.final-1", true), equalTo("1.2.0"));
    }

    @Test
    public void testGetOsgiVersion() {
        assertThat(Version.getOsgiVersion("1.0"), equalTo("1.0"));
        assertThat(Version.getOsgiVersion("1_2_3"), equalTo("1.2.3"));
        assertThat(Version.getOsgiVersion("1-2.3beta4"), equalTo("1.2.3.beta4"));
        assertThat(Version.getOsgiVersion("1.2.3.4.beta"), equalTo("1.2.3.4-beta"));
        assertThat(Version.getOsgiVersion("12.4-beta"), equalTo("12.4.0.beta"));
        assertThat(Version.getOsgiVersion("-beta1"), equalTo("-beta1"));
        assertThat(Version.getOsgiVersion("12.beta1_3-5.hello"), equalTo("12.0.0.beta1_3-5-hello"));
        assertThat(
                Version.getOsgiVersion("1.0.0.Final-t20170516223844555-redhat-1"),
                equalTo("1.0.0.Final-t20170516223844555-redhat-1"));
        assertThat(Version.getOsgiVersion("1-0-3.4-beta2.1"), equalTo("1.0.3.4-beta2-1"));
        assertThat(Version.getOsgiVersion("4.8-2"), equalTo("4.8.2"));
        assertThat(Version.getOsgiVersion("4.5.1-1"), equalTo("4.5.1.1"));
        assertThat(Version.getOsgiVersion("4.5.1-1.redhat-1"), equalTo("4.5.1.1-redhat-1"));
        assertThat(Version.getOsgiVersion("1.1.foo"), equalTo("1.1.0.foo"));

        String o = "1.1-foo";
        assertThat(
                Version.getOsgiVersion(Version.getOsgiMMM(o, true) + "." + Version.getQualifier(o)),
                equalTo("1.1.0.foo"));
        o = "1.2";
        assertThat(
                Version.getOsgiVersion(Version.getOsgiMMM(o, true) + "." + Version.getQualifier(o)),
                equalTo("1.2.0"));
    }

    @Test
    public void testGetQualifier() {
        assertThat(Version.getQualifier("1.0-SNAPSHOT"), equalTo("SNAPSHOT"));
        assertThat(Version.getQualifierWithDelim("1.0-SNAPSHOT"), equalTo("-SNAPSHOT"));

        assertThat(Version.getQualifier("1.0.0.Beta1"), equalTo("Beta1"));
        assertThat(Version.getQualifierWithDelim("1.0.0.Beta1"), equalTo(".Beta1"));

        assertThat(Version.getQualifier("1.0.beta.1"), equalTo("beta.1"));
        assertThat(Version.getQualifierWithDelim("1.0.beta.1"), equalTo(".beta.1"));

        assertThat(Version.getQualifier("Beta1"), equalTo("Beta1"));
        assertThat(Version.getQualifierWithDelim("Beta1"), equalTo("Beta1"));

        assertThat(Version.getQualifier("1.x.2.beta1"), equalTo("x.2.beta1"));
        assertThat(Version.getQualifierWithDelim("1.x.2.beta1"), equalTo(".x.2.beta1"));

        assertThat(Version.getQualifier("1.2"), equalTo(""));
        assertThat(Version.getQualifierWithDelim("1.2"), equalTo(""));

        assertThat(Version.getQualifier("1.5-3_beta-SNAPSHOT-1"), equalTo("beta-SNAPSHOT-1"));
        assertThat(Version.getQualifierWithDelim("1.5-3_beta-SNAPSHOT-1"), equalTo("_beta-SNAPSHOT-1"));
        assertThat(Version.getQualifierBase("1.5-3_beta-SNAPSHOT-1"), equalTo("beta-SNAPSHOT"));
        assertThat(Version.getOsgiVersion("1.5-3_beta-SNAPSHOT-1"), equalTo("1.5.3.beta-SNAPSHOT-1"));

        assertThat(Version.getQualifier("_beta-SNAPSHOT-1"), equalTo("beta-SNAPSHOT-1"));
        assertThat(Version.getQualifierWithDelim("_beta-SNAPSHOT-1"), equalTo("_beta-SNAPSHOT-1"));
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
        assertThat(Version.getQualifierBase("1.0-SNAPSHOT"), equalTo(""));
        assertThat(Version.getQualifierBase("1.0.0.Beta1"), equalTo("Beta"));
        assertThat(Version.getQualifierBase("1.0.0.jboss-test-SNAPSHOT"), equalTo("jboss-test"));
        assertThat(Version.getQualifierBase("${project.version}-test-1"), equalTo("${project.version}-test"));

        assertThat(Version.getQualifierBase("Final-Beta10"), equalTo("Final-Beta"));
        assertThat(Version.getQualifierBase("1.0.0.Beta10-rebuild-3"), equalTo("Beta10-rebuild"));
        assertThat(Version.getQualifierBase("1.0.0.Final-Beta-1"), equalTo("Final-Beta"));
        assertThat(Version.getQualifierBase("1.0.0.Final-Beta10"), equalTo("Final-Beta"));
        assertThat(Version.getQualifierBase("4.8-TWENTY"), equalTo("TWENTY"));
        assertThat(Version.getQualifierBase("4.8-2"), equalTo(""));
    }

    @Test
    public void testGetSnapshot() {
        assertThat(Version.getSnapshot("1.0-SNAPSHOT"), equalTo("SNAPSHOT"));
        assertThat(Version.getSnapshotWithDelim("1.0-SNAPSHOT"), equalTo("-SNAPSHOT"));

        assertThat(Version.getSnapshot("1.0.0.SNAPSHOT"), equalTo("SNAPSHOT"));
        assertThat(Version.getSnapshotWithDelim("1.0.0.SNAPSHOT"), equalTo(".SNAPSHOT"));

        assertThat(Version.getSnapshot("1.0.0.Beta1-snapshot"), equalTo("snapshot"));
        assertThat(Version.getSnapshotWithDelim("1.0.0.Beta1-snapshot"), equalTo("-snapshot"));

        assertThat(Version.getSnapshot("1_snaPsHot"), equalTo("snaPsHot"));
        assertThat(Version.getSnapshotWithDelim("1_snaPsHot"), equalTo("_snaPsHot"));

        assertThat(Version.getSnapshot("1.0"), equalTo(""));
        assertThat(Version.getSnapshotWithDelim("1.0"), equalTo(""));

        assertThat(Version.getSnapshot("1.0-foo"), equalTo(""));
        assertThat(Version.getSnapshotWithDelim("1.0-foo"), equalTo(""));
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
        assertThat(Version.removeSnapshot("1.0-SNAPSHOT"), equalTo("1.0"));
        assertThat(Version.removeSnapshot("1.0.0.Beta1_snapshot"), equalTo("1.0.0.Beta1"));
        assertThat(Version.removeSnapshot("1.snaPsHot"), equalTo("1"));
        assertThat(Version.removeSnapshot("SNAPSHOT"), equalTo(""));
        assertThat(Version.removeSnapshot("1.0.snapshot.beta1"), equalTo("1.0.snapshot.beta1"));

        assertThat(Version.removeSnapshot("1.0.0.redhat-1"), equalTo("1.0.0.redhat-1"));

    }

    @Test
    public void testSetBuildNumber() {
        assertThat(Version.setBuildNumber("1.0.beta1", "2"), equalTo("1.0.beta2"));
        assertThat(Version.setBuildNumber("1.0_2-Beta1-SNAPSHOT", "41"), equalTo("1.0_2-Beta41-SNAPSHOT"));
        assertThat(Version.setBuildNumber("1.0.2.1", "3"), equalTo("1.0.2.3"));
        assertThat(Version.setBuildNumber("1.0.2", "3"), equalTo("1.0.2.3"));
        assertThat(Version.setBuildNumber("1.0", "2"), equalTo("1.0.2"));
        assertThat(Version.setBuildNumber("1.0-alpha", "001"), equalTo("1.0-alpha-001"));
    }

    @Test
    public void testSetSnapshot() {
        assertThat(Version.setSnapshot("1.0-SNAPSHOT", true), equalTo("1.0-SNAPSHOT"));
        assertThat(Version.setSnapshot("1.0-SNAPSHOT", false), equalTo("1.0"));
        assertThat(Version.setSnapshot("1.1", true), equalTo("1.1-SNAPSHOT"));
        assertThat(Version.setSnapshot("1.1", false), equalTo("1.1"));
        assertThat(Version.setSnapshot("1.2.jboss-1", true), equalTo("1.2.jboss-1-SNAPSHOT"));
        assertThat(Version.setSnapshot("1.2.jboss-1", false), equalTo("1.2.jboss-1"));
        assertThat(Version.setSnapshot("1.0.0.Beta1_snapshot", true), equalTo("1.0.0.Beta1_snapshot"));
        assertThat(Version.setSnapshot("1.0.0.Beta1_snapshot", false), equalTo("1.0.0.Beta1"));
        assertThat(Version.setSnapshot("1.snaPsHot", true), equalTo("1.snaPsHot"));
        assertThat(Version.setSnapshot("1.snaPsHot", false), equalTo("1"));
        assertThat(Version.setSnapshot("SNAPSHOT", true), equalTo("SNAPSHOT"));
        assertThat(Version.setSnapshot("SNAPSHOT", false), equalTo(""));
        assertThat(Version.setSnapshot("1.0.snapshot.beta1", true), equalTo("1.0.snapshot.beta1-SNAPSHOT"));
        assertThat(Version.setSnapshot("1.0.snapshot.beta1", false), equalTo("1.0.snapshot.beta1"));
    }

    @Test
    public void testRemoveLeadingDelimiters() {
        assertThat(Version.removeLeadingDelimiter(".1.2"), equalTo("1.2"));
        assertThat(Version.removeLeadingDelimiter("_Beta1"), equalTo("Beta1"));
        assertThat(Version.removeLeadingDelimiter("1.0-SNAPSHOT"), equalTo("1.0-SNAPSHOT"));
        assertThat(Version.removeLeadingDelimiter("1.0_foo-"), equalTo("1.0_foo-"));
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

    @Test
    public void testStateAlternatives() {
        Properties p = new Properties();
        VersioningState state = new VersioningState(p);
        assertEquals(1, state.getSuffixAlternatives().size());
        assertEquals("redhat", state.getSuffixAlternatives().get(0));

        p.clear();
        p.setProperty(VersioningState.VERSION_SUFFIX_SYSPROP, "redhat-10");
        state = new VersioningState(p);
        assertEquals(0, state.getSuffixAlternatives().size());
        assertEquals(1, state.getAllSuffixes().size());

        p.clear();
        p.setProperty(VersioningState.VERSION_SUFFIX_SYSPROP, "temporary-redhat-10");
        p.setProperty(VersioningState.VERSION_SUFFIX_ALT, "redhat-alt");
        state = new VersioningState(p);
        assertEquals(1, state.getSuffixAlternatives().size());
        assertEquals("redhat-alt", state.getSuffixAlternatives().get(0));

        p.clear();
        p.setProperty(VersioningState.VERSION_SUFFIX_SYSPROP, "temporary-redhat-1");
        state = new VersioningState(p);
        assertEquals(1, state.getSuffixAlternatives().size());
        assertEquals(2, state.getAllSuffixes().size());
    }
}
