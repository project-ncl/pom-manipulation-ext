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
package org.commonjava.maven.ext.core.io;

import static junit.framework.TestCase.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;
import org.commonjava.maven.ext.common.model.SimpleScopedArtifactRef;
import org.commonjava.maven.ext.core.ManipulationSession;
import org.commonjava.maven.ext.core.fixture.TestUtils;
import org.commonjava.maven.ext.io.ModelIO;
import org.commonjava.maven.ext.io.resolver.GalleyAPIWrapper;
import org.commonjava.maven.ext.io.resolver.GalleyInfrastructure;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ResolverTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void resolveRealArtifactTest()
            throws Exception {
        File cache = temp.newFolder("cache-dir");
        ManipulationSession session = TestUtils.createSession(null);
        final GalleyInfrastructure galleyInfra = new GalleyInfrastructure(session, null).init(null, null, cache);
        final GalleyAPIWrapper wrapper = new GalleyAPIWrapper(galleyInfra);
        final ModelIO model = new ModelIO(wrapper);

        File c = model.resolveRawFile(SimpleScopedArtifactRef.parse("academy.alex:custommatcher:1.0"));

        assertTrue(c.exists());

        String academy = FileUtils.readFileToString(c, StandardCharsets.UTF_8);

        assertTrue(academy.contains("This is Custom Matcher to validate Credit Card"));
    }
}
