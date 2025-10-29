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

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.junit.Test;

public class VersionRangeTest {
    @Test
    public void testRange1() throws Exception {
        String range = "1.0.0.Final";
        VersionRange s = VersionRange.createFromVersionSpec(range);

        assertFalse(s.hasRestrictions());

        range = "[1.0.0.Final,)";
        s = VersionRange.createFromVersionSpec(range);
        assertTrue(s.hasRestrictions());
    }

    @Test
    public void testRange2() throws Exception {
        List<ArtifactVersion> testList = new ArrayList<>();
        ArtifactVersion av = new DefaultArtifactVersion("1.9");
        testList.add(av);
        av = new DefaultArtifactVersion("1.0.1");
        testList.add(av);
        av = new DefaultArtifactVersion("1.10");
        testList.add(av);

        String range = "[1.0,1.10)";
        VersionRange s = VersionRange.createFromVersionSpec(range);

        assertTrue(s.hasRestrictions());
        assertEquals("1.9", s.matchVersion(testList).toString());
    }

    @Test
    public void testRange3() throws Exception {
        List<ArtifactVersion> testList = new ArrayList<>();
        ArtifactVersion av = new DefaultArtifactVersion("1.9");
        testList.add(av);
        av = new DefaultArtifactVersion("1.0.1");
        testList.add(av);
        av = new DefaultArtifactVersion("1.10.0.Final");
        testList.add(av);

        String range = "[1.0,1.10)";
        VersionRange s = VersionRange.createFromVersionSpec(range);

        assertTrue(s.hasRestrictions());
        assertEquals("1.9", s.matchVersion(testList).toString());
    }

    @Test
    public void testRange4() throws Exception {
        List<ArtifactVersion> testList = new ArrayList<>();
        ArtifactVersion av = new DefaultArtifactVersion("1.9");
        testList.add(av);
        av = new DefaultArtifactVersion("1.0.1");
        testList.add(av);
        av = new DefaultArtifactVersion("1.10.0.Final");
        testList.add(av);

        String range = "[1.0,)";
        VersionRange s = VersionRange.createFromVersionSpec(range);

        assertTrue(s.hasRestrictions());
        assertEquals("1.10.0.Final", s.matchVersion(testList).toString());
    }

    @Test
    public void testRange5() throws Exception {
        String range = "(,1.0],[1.2,)";
        VersionRange s = VersionRange.createFromVersionSpec(range);

        assertTrue(s.hasRestrictions());
    }
}
