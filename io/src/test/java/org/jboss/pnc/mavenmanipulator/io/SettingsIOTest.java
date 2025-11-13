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

package org.jboss.pnc.mavenmanipulator.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.apache.commons.io.FileUtils;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.io.DefaultSettingsReader;
import org.jboss.pnc.mavenmanipulator.common.ManipulationException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SettingsIOTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void verifySettingsEncoding() throws IOException {
        URL resource = SettingsIOTest.class.getResource("settings.xml");
        assertNotNull(resource);
        File originalSettingsFile = new File(resource.getFile());
        assertTrue(originalSettingsFile.exists());

        Settings settings = new DefaultSettingsReader().read(originalSettingsFile, Collections.emptyMap());
        assertNull(settings.getModelEncoding());

        resource = SettingsIOTest.class.getResource("settings-wth-encoding.xml");
        assertNotNull(resource);
        originalSettingsFile = new File(resource.getFile());
        assertTrue(originalSettingsFile.exists());
        settings = new DefaultSettingsReader().read(originalSettingsFile, Collections.emptyMap());
        assertEquals("UTF-8", settings.getModelEncoding());
    }

    @Test
    public void verifyRoundTrip() throws IOException, ManipulationException {
        URL resource = SettingsIOTest.class.getResource("settings.xml");
        assertNotNull(resource);
        File originalSettingsFile = new File(resource.getFile());
        assertTrue(originalSettingsFile.exists());

        Settings settings = new DefaultSettingsReader().read(originalSettingsFile, Collections.emptyMap());
        assertNull(settings.getModelEncoding());

        File targetFile = folder.newFile("settings.xml");
        FileUtils.copyFile(originalSettingsFile, targetFile);
        new SettingsIO(null).write(settings, targetFile);
        assertTrue(
                FileUtils.contentEqualsIgnoreEOL(originalSettingsFile, targetFile, StandardCharsets.UTF_8.toString()));
        assertTrue(FileUtils.contentEquals(targetFile, originalSettingsFile));
    }

    @Test(expected = ManipulationException.class)
    public void verifyErroneousSettings() throws IOException, ManipulationException {
        File targetFile = folder.newFile("settings.xml");
        FileOutputStream saveFile = new FileOutputStream(targetFile);
        try (DataOutputStream save = new DataOutputStream(saveFile)) {
            save.writeByte(0);
        }
        new SettingsIO(null).write(null, targetFile);
    }

    @Test
    public void verifyWriteSettngs() throws IOException, ManipulationException {
        URL resource = SettingsIOTest.class.getResource("settings.xml");
        assertNotNull(resource);
        File originalSettingsFile = new File(resource.getFile());
        assertTrue(originalSettingsFile.exists());

        Settings settings = new DefaultSettingsReader().read(originalSettingsFile, Collections.emptyMap());
        assertNull(settings.getModelEncoding());

        File targetFile = folder.newFile("settings.xml");

        new SettingsIO(null).write(settings, targetFile);
        assertFalse(FileUtils.contentEquals(targetFile, originalSettingsFile));
    }
}
