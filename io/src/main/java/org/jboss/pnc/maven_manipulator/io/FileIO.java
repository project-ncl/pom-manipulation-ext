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
package org.jboss.pnc.maven_manipulator.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.jboss.pnc.maven_manipulator.common.ManipulationException;
import org.jboss.pnc.maven_manipulator.common.ManipulationUncheckedException;
import org.jboss.pnc.maven_manipulator.common.util.LineSeparator;

/**
 * Class to resolve Files from alternate locations
 */
@Named
@Singleton
public class FileIO {

    private final File cacheDir;

    @Inject
    public FileIO() {
        try {
            cacheDir = Files.createTempDirectory("pme-cache-").toFile();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> FileUtils.deleteQuietly(cacheDir)));
        } catch (IOException e) {
            throw new ManipulationUncheckedException(e);
        }
    }

    public FileIO(File cacheDir) {
        this.cacheDir = cacheDir;
    }

    /**
     * Read the raw file from a given URL. Useful if we need to read
     * a remote file.
     *
     * @param reference the URL to read.
     * @return the file for the URL
     * @throws IOException if an error occurs.
     */
    public File resolveURL(final String reference) throws IOException {
        URL ref;
        File result;

        if (SystemUtils.IS_OS_WINDOWS) {
            ref = new URL(reference.replaceFirst("^file://([a-zA-Z]:\\\\)", "file:///$1"));
        } else {
            ref = new URL(reference);
        }
        // If its a local file reference. just use the file itself rather than copying it.
        if (!"file".equals(ref.getProtocol())) {
            result = Files.createTempFile(cacheDir.toPath(), UUID.randomUUID().toString(), null).toFile();
            FileUtils.copyURLToFile(ref, result);
        } else {
            result = new File(ref.getPath());
        }

        return result;
    }

    public static LineSeparator determineEOL(File file) throws ManipulationException {
        try (BufferedReader bufferIn = new BufferedReader(
                new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8))) {
            int prev = -1;
            int ch;
            while ((ch = bufferIn.read()) != -1) {
                if (ch == '\n') {
                    if (prev == '\r') {
                        return LineSeparator.CRNL;
                    } else {
                        return LineSeparator.NL;
                    }
                } else if (prev == '\r') {
                    return LineSeparator.CR;
                }
                prev = ch;
            }
            throw new ManipulationException("Could not determine end-of-line marker mode");
        } catch (IOException ioe) {
            throw new ManipulationException("Could not determine end-of-line marker mode", ioe);
        }
    }
}
