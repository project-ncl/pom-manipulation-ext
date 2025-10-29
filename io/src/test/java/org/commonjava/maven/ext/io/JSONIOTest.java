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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.util.LineSeparator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonEncoding;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPathException;

public class JSONIOTest {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private JSONIO jsonIO;
    private File npmFile;
    private File pluginFile;

    @Rule
    public TemporaryFolder tf = new TemporaryFolder();

    @Before
    public void setup() {
        jsonIO = new JSONIO();
        URL resource = this.getClass().getResource("npm-shrinkwrap.json");
        npmFile = new File(resource.getFile());

        URL resource2 = this.getClass().getResource("amg-plugin-registry.json");
        pluginFile = new File(resource2.getFile());
    }

    @Test
    public void readFile()
            throws ManipulationException, IOException {
        DocumentContext o = jsonIO.parseJSON(npmFile);
        logger.debug("Read {}", o.jsonString());
        logger.debug("File {}", FileUtils.readFileToString(npmFile, jsonIO.getCharset()));
        // They won't be equal as jsonString is not pretty printed.
        assertNotEquals(o.jsonString(), FileUtils.readFileToString(npmFile, jsonIO.getCharset()));
        assertNotNull(o);
    }

    @Test
    public void writeFileShrinkwrap() throws ManipulationException, IOException {
        DocumentContext doc = jsonIO.parseJSON(npmFile);

        File target = tf.newFile();

        jsonIO.writeJSON(target, doc);

        assertTrue(FileUtils.contentEqualsIgnoreEOL(npmFile, target, jsonIO.getCharset().name()));
    }

    @Test
    public void testEOL() throws ManipulationException, IOException {
        DocumentContext docUnix = jsonIO.parseJSON(npmFile);
        assertEquals(StandardCharsets.UTF_8, jsonIO.getCharset());
        File targetUnix = tf.newFile();
        jsonIO.writeJSON(targetUnix, docUnix);
        assertEquals("\n", jsonIO.getEOL());
        assertEquals(LineSeparator.NL, FileIO.determineEOL(targetUnix));
        assertTrue(FileUtils.contentEqualsIgnoreEOL(npmFile, targetUnix, jsonIO.getCharset().name()));

        URL resourceDos = this.getClass().getResource("npm-shrinkwrap-dos.json");
        File npmFileDos = new File(resourceDos.getFile());
        DocumentContext docDos = jsonIO.parseJSON(npmFileDos);
        assertEquals(StandardCharsets.UTF_8, jsonIO.getCharset());
        File targetDos = tf.newFile();
        jsonIO.writeJSON(targetDos, docDos);
        assertEquals("\r\n", jsonIO.getEOL());
        assertEquals(LineSeparator.CRNL, FileIO.determineEOL(targetDos));
        assertTrue(FileUtils.contentEqualsIgnoreEOL(npmFileDos, targetDos, jsonIO.getCharset().name()));

        URL resourceMac = this.getClass().getResource("npm-shrinkwrap-mac.json");
        File npmFileMac = new File(resourceMac.getFile());
        DocumentContext docMac = jsonIO.parseJSON(npmFileMac);
        assertEquals(StandardCharsets.UTF_8, jsonIO.getCharset());
        File targetMac = tf.newFile();
        jsonIO.writeJSON(targetMac, docMac);
        assertEquals("\r", jsonIO.getEOL());
        assertEquals(LineSeparator.CR, FileIO.determineEOL(targetMac));
        assertTrue(FileUtils.contentEqualsIgnoreEOL(npmFileMac, targetMac, jsonIO.getCharset().name()));
    }

    @Test
    public void testCharsetUTF16BE() throws ManipulationException, IOException {
        URL resource = this.getClass().getResource("npm-shrinkwrap-utf16be.json");
        File npmFile = new File(resource.getFile());
        DocumentContext doc = jsonIO.parseJSON(npmFile);
        assertEquals(StandardCharsets.UTF_16BE, jsonIO.getCharset());
        File target = tf.newFile();
        jsonIO.writeJSON(target, doc);
        assertEquals(JsonEncoding.UTF16_BE, JSONIO.detectEncoding(target));
        assertTrue(FileUtils.contentEqualsIgnoreEOL(npmFile, target, jsonIO.getCharset().name()));
    }

    @Test
    public void testCharsetUTF32BE() throws ManipulationException, IOException {
        URL resource = this.getClass().getResource("npm-shrinkwrap-utf32be.json");
        File npmFile = new File(resource.getFile());
        DocumentContext doc = jsonIO.parseJSON(npmFile);
        assertEquals(Charset.forName("UTF-32BE"), jsonIO.getCharset());
        File target = tf.newFile();
        jsonIO.writeJSON(target, doc);
        assertEquals(JsonEncoding.UTF32_BE, JSONIO.detectEncoding(target));
        assertTrue(FileUtils.contentEqualsIgnoreEOL(npmFile, target, jsonIO.getCharset().name()));
    }

    @Test
    public void modifyFile() throws ManipulationException, IOException {
        String deletePath = "$..resolved";
        DocumentContext doc = jsonIO.parseJSON(npmFile);
        doc.delete(deletePath);

        File target = tf.newFile();

        jsonIO.writeJSON(target, doc);

        assertFalse(FileUtils.contentEquals(npmFile, target));
    }

    @Test
    public void modifyPartialFile() throws ManipulationException, IOException {
        String deletePath = "$.dependencies.agent-base..resolved";
        DocumentContext doc = jsonIO.parseJSON(npmFile);
        doc.delete(deletePath);

        File target = tf.newFile();

        jsonIO.writeJSON(target, doc);

        assertFalse(doc.jsonString().contains("https://registry.npmjs.org/agent-base/-/agent-base-2.0.1.tgz"));
        assertTrue(doc.jsonString().contains("resolved"));
    }

    @Test
    public void updateVersions() throws ManipulationException, IOException {
        String modifyPath = "$..version";
        DocumentContext doc = jsonIO.parseJSON(pluginFile);
        doc.set(modifyPath, "1.3.0.rebuild-1");

        logger.debug("Modified {}", doc.jsonString());

        File target = tf.newFile();

        jsonIO.writeJSON(target, doc);

        assertFalse(doc.jsonString().contains("1.2.2-SNAPSHOT"));
        assertTrue(doc.jsonString().contains("1.3.0.rebuild-1"));
        assertFalse(FileUtils.contentEquals(pluginFile, target));
    }

    @Test
    public void updateURL() throws ManipulationException, IOException {
        String modifyPath = "$.repository.url";
        DocumentContext doc = jsonIO.parseJSON(pluginFile);
        doc.set(modifyPath, "https://maven.repository.redhat.com/ga/");

        logger.debug("Modified {}", doc.jsonString());

        File target = tf.newFile();

        jsonIO.writeJSON(target, doc);

        assertTrue(doc.jsonString().contains("https://maven.repository.redhat.com/ga/"));
        assertTrue(doc.jsonString().contains("1.2.2-SNAPSHOT"));
        assertFalse(FileUtils.contentEquals(pluginFile, target));
    }

    @Test(expected = ManipulationException.class)
    public void updateWithInvalidPath() throws ManipulationException {
        String modifyPath = "$.I.really.do.not.exist.repository.url";
        try {
            DocumentContext doc = jsonIO.parseJSON(pluginFile);
            doc.set(modifyPath, "https://maven.repository.redhat.com/ga/");
        } catch (JsonPathException e) {
            throw new ManipulationException("Caught JsonPath", e);
        }
    }

    @Test
    public void countMatches() throws ManipulationException {
        String pluginsPath = "$..plugins";
        String reposURLPath = "$.repository.url";
        try {
            DocumentContext doc = jsonIO.parseJSON(pluginFile);

            List o = doc.read(pluginsPath);
            assertEquals(1, o.size());

            o = doc.read(reposURLPath);
            assertEquals(1, o.size());
        } catch (JsonPathException e) {
            throw new ManipulationException("Caught JsonPath", e);
        }
    }
}
