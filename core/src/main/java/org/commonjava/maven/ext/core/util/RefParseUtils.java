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
package org.commonjava.maven.ext.core.util;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import org.apache.commons.io.FileUtils;
import org.commonjava.atlas.maven.ident.ref.InvalidRefException;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Convenience utility for parsing and fetching nested remote reference specs for strings containing
 * lists of GA, GAV, GATCV, etc. strings.
 */
public final class RefParseUtils {
    private static final Logger logger = LoggerFactory.getLogger(RefParseUtils.class);

    private RefParseUtils() {
    }

    /**
     * Splits the value on ',', then parses each element using the provided parser. If an entry
     * appears to be an HTTP(S) URL, the resulting resource will be fetched and recursively parsed
     * in the same way. Returns null if the input value is null or empty.
     *
     * @param <T> the type of element the entries of the common-delimited value will be parsed as
     * @param value a comma separated list of references to parse
     * @param parser parsing function to convert individual reference entries to type T
     *
     * @return a list of parsed {@code T} entries.
     */
    public static <T> List<T> parseRefs(final String value, Function<String, T> parser) {
        if (isEmpty(value)) {
            return null;
        } else {
            final String[] entries = value.split(",");
            final List<T> refs = new ArrayList<>();
            for (final String entry : entries) {
                if (isNotEmpty(entry)) {
                    if (entry.startsWith("http://") || entry.startsWith("https://")) {
                        logger.debug("Found remote file in {}", entry);
                        try {
                            File found = File.createTempFile(UUID.randomUUID().toString(), null);
                            FileUtils.copyURLToFile(new URL(entry), found);
                            String potentialRefs = FileUtils.readFileToString(found, Charset.defaultCharset())
                                    .trim()
                                    .replace("\n", ",");
                            List<T> readRefs = parseRefs(potentialRefs, parser);
                            if (readRefs != null) {
                                refs.addAll(readRefs);
                            }
                        } catch (InvalidRefException | IOException e) {
                            throw new ManipulationUncheckedException(e);
                        }
                    } else {
                        refs.add(parser.apply(entry));
                    }
                }
            }
            return refs;
        }
    }
}
