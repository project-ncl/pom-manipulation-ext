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
package org.commonjava.maven.ext.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.io.resolver.GalleyInfrastructure;
import org.commonjava.maven.ext.io.rest.handler.StaticResourceHandler;
import org.commonjava.maven.ext.io.rest.rule.MockServer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FileIOTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final StaticResourceHandler staticFile = new StaticResourceHandler("pom.xml");

    @Rule
    public MockServer mockServer = new MockServer(staticFile);

    private FileIO fileIO;

    @Before
    public void before()
            throws Exception {
        File res = folder.newFolder();
        fileIO = new FileIO(res);
    }

    @Test
    public void testReadURL() throws Exception {
        String urlPom = FileUtils.readFileToString(fileIO.resolveURL(mockServer.getUrl()), StandardCharsets.UTF_8);
        String filePom = FileUtils.readFileToString(
                new File(
                        new File(FileIOTest.class.getResource("/").getPath())
                                .getParentFile()
                                .getParentFile(),
                        "pom.xml"),
                StandardCharsets.UTF_8);

        assertEquals(urlPom, filePom);
    }

    @Test
    public void cacheDir() throws IOException, ManipulationException {
        GalleyInfrastructure gi = new GalleyInfrastructure(null, null).init(null, null, folder.newFolder());

        File f = gi.getCacheDir();

        assertTrue(f.exists());
        assertEquals(f.getParentFile(), folder.getRoot());
    }

    @Test
    public void testReadFile() throws Exception {
        File root = folder.getRoot();
        File result = fileIO.resolveURL("file://" + root.getAbsolutePath());
        assertEquals(root, result);
    }
}
