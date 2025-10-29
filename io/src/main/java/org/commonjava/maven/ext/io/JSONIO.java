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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import javax.inject.Named;
import javax.inject.Singleton;

import org.commonjava.maven.ext.common.ManipulationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.core.io.ContentReference;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.json.ByteSourceJsonBootstrapper;
import com.fasterxml.jackson.core.util.BufferRecycler;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

@Named
@Singleton
public class JSONIO {
    private static final Logger logger = LoggerFactory.getLogger(JSONIO.class);

    private File jsonFile;

    private JsonEncoding encoding;

    private Charset charset;

    private String eol;

    public static JsonEncoding detectEncoding(File jsonFile) throws ManipulationException {
        try (FileInputStream in = new FileInputStream(jsonFile)) {
            byte[] inputBuffer = new byte[4];
            in.read(inputBuffer);
            IOContext ctxt = new IOContext(new BufferRecycler(), ContentReference.unknown(), false);
            ByteSourceJsonBootstrapper strapper = new ByteSourceJsonBootstrapper(
                    ctxt,
                    inputBuffer,
                    0,
                    4);
            JsonEncoding encoding = strapper.detectEncoding();
            logger.debug("Detected JSON encoding {} for file {}", encoding, jsonFile);
            return encoding;
        } catch (IOException e) {
            logger.error("Unable to detect charset for file: {}", jsonFile, e);
            throw new ManipulationException("Unable to detect charset for file {}", jsonFile, e);
        }
    }

    public JsonEncoding getEncoding() throws ManipulationException {
        return this.encoding;
    }

    public Charset getCharset() throws ManipulationException {
        return this.charset;
    }

    public String detectEOL(File jsonFile) throws ManipulationException {
        if (charset == null) {
            detectEncoding(jsonFile);
        }

        // TODO: We should detect the eol, but right now we return the system default line separator
        if (!StandardCharsets.UTF_8.equals(charset)) {
            return new String(System.lineSeparator().getBytes(Charset.forName(charset.name())), charset);
        }

        String lf = null;
        try {
            lf = FileIO.determineEOL(jsonFile).value();
            if (logger.isDebugEnabled()) {
                logger.debug(
                        "Detected JSON linefeed {} for file {}",
                        lf.replace("\r", "\\r").replace("\n", "\\n"),
                        jsonFile);
            }
        } catch (ManipulationException e) {
            logger.error("Unable to detect eol for file: {}", jsonFile, e);
            throw new ManipulationException("Unable to detect eol for file {}", jsonFile, e);
        }
        return lf;
    }

    public String getEOL() throws ManipulationException {
        return eol;
    }

    public DocumentContext parseJSON(File jsonFile) throws ManipulationException {
        if (jsonFile == null || !jsonFile.exists()) {
            throw new ManipulationException("JSON File not found");
        }

        this.encoding = detectEncoding(jsonFile);
        this.charset = Charset.forName(encoding.getJavaName());
        this.jsonFile = jsonFile;
        this.eol = detectEOL(jsonFile);

        DocumentContext doc;

        try (FileInputStream in = new FileInputStream(jsonFile)) {
            Configuration conf = Configuration.builder().options(Option.ALWAYS_RETURN_LIST).build();
            doc = JsonPath.using(conf).parse(in, charset.name());
        } catch (IOException e) {
            logger.error("Unable to parse JSON File", e);
            throw new ManipulationException("Unable to parse JSON File", e);
        }
        return doc;
    }

    public void writeJSON(File target, DocumentContext contents) throws ManipulationException {
        try {
            PrettyPrinter dpp = new MyPrettyPrinter(getCharset());
            ObjectMapper mapper = new ObjectMapper();
            String jsonString = contents.jsonString();
            String pretty = mapper.writer(dpp).writeValueAsString(mapper.readValue(jsonString, Object.class));
            Charset cs = getCharset();
            FileOutputStream fileOutputStream = new FileOutputStream(target);
            try (OutputStreamWriter p = new OutputStreamWriter(fileOutputStream, cs)) {
                p.write(pretty);
                p.append(getEOL());
            }
        } catch (IOException e) {
            logger.error("Unable to write JSON string:  ", e);
            throw new ManipulationException("Unable to write JSON string", e);
        }
    }

    /**
     * Override the default Jackson pretty printer so the object field separators look like
     * "version": "0.1.0",
     * not
     * "version" : "0.1.0",
     */
    private class MyPrettyPrinter extends DefaultPrettyPrinter {
        private Charset charset = StandardCharsets.UTF_8;

        public MyPrettyPrinter(Charset charset) throws ManipulationException {
            super(DEFAULT_ROOT_VALUE_SEPARATOR);
            this.charset = charset;
            _objectIndenter = new DefaultIndenter("  ", getEOL());
        }

        public MyPrettyPrinter(MyPrettyPrinter base) {
            super(base, base._rootSeparator);
        }

        @Override
        public DefaultPrettyPrinter createInstance() {
            return new MyPrettyPrinter(this);
        }

        @Override
        public void writeObjectFieldValueSeparator(JsonGenerator jg)
                throws IOException, JsonGenerationException {
            String s;
            if (_spacesInObjectEntries) {
                s = ": ";
            } else {
                s = ":";
            }
            jg.writeRaw(new String(s.getBytes(), charset));
        }
    }
}
