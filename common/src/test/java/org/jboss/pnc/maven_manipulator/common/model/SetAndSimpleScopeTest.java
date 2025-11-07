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
package org.jboss.pnc.maven_manipulator.common.model;

import static org.junit.Assert.assertEquals;

import java.util.HashSet;
import java.util.Set;

import org.commonjava.atlas.maven.ident.ref.ArtifactRef;
import org.junit.Test;

public class SetAndSimpleScopeTest {
    @Test
    public void testVerifyArtifactRef() {
        SimpleScopedArtifactRef commonslang = SimpleScopedArtifactRef.parse("commons-lang:commons-lang:jar:1.0");
        SimpleScopedArtifactRef commonslangsources = SimpleScopedArtifactRef
                .parse("commons-lang:commons-lang:jar:1.0:sources");

        Set<ArtifactRef> s = new HashSet<>();
        s.add(commonslang);
        s.add(commonslangsources);

        assertEquals(2, s.size());
    }

    @Test
    public void testVerifyScopedArtifactRef() {
        SimpleScopedArtifactRef commonslang = SimpleScopedArtifactRef.parse("commons-lang:commons-lang:jar:1.0");
        SimpleScopedArtifactRef commonslangsources = SimpleScopedArtifactRef
                .parse("commons-lang:commons-lang:jar:1.0:sources");

        SimpleScopedArtifactRef commonsscoped = new SimpleScopedArtifactRef(
                commonslang.asProjectVersionRef(),
                commonslang.getTypeAndClassifier(),
                "compile");
        SimpleScopedArtifactRef commonsscopedtest = new SimpleScopedArtifactRef(
                commonslangsources.asProjectVersionRef(),
                commonslangsources.getTypeAndClassifier(),
                "test");

        Set<ArtifactRef> s = new HashSet<>();
        s.add(commonsscoped);
        s.add(commonsscopedtest);

        assertEquals(2, s.size());
    }
}
