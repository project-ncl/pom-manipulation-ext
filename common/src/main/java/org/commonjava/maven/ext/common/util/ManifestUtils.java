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
package org.commonjava.maven.ext.common.util;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.security.CodeSource;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ManifestUtils {
    private static final Logger logger = LoggerFactory.getLogger(ManifestUtils.class);

    /**
     * Retrieves the SHA this was built with.
     *
     * @param target the Class within the jar to find and locate.
     * @return the GIT sha of this codebase.
     */
    public static String getManifestInformation(Class<?> target) {
        String result = "";

        if (target == null) {
            throw new ManipulationUncheckedException("No target specified.");
        }

        try {
            final CodeSource cs = target.getProtectionDomain().getCodeSource();
            if (cs == null) {
                logger.debug(
                        "Unable to retrieve manifest for {} as CodeSource was null for the protection domain ({})",
                        target,
                        target.getProtectionDomain());
            } else {
                final URL jarUrl = cs.getLocation();

                if (new File(jarUrl.getPath()).isDirectory()) {
                    logger.debug(
                            "Unable to retrieve manifest for {} as location is a directory not a jar ({})",
                            target,
                            jarUrl.getPath());
                } else {
                    try (JarInputStream jarStream = new JarInputStream(jarUrl.openStream())) {
                        final Manifest manifest = jarStream.getManifest();
                        if (manifest != null) {
                            result = manifest.getMainAttributes().getValue("Implementation-Version");
                            result += " ( SHA: " + manifest.getMainAttributes().getValue("Scm-Revision") + " )";
                        }
                    }
                }
            }
        } catch (final IOException e) {
            throw new ManipulationUncheckedException("Error retrieving information from manifest", e);
        }

        return result;
    }
}
