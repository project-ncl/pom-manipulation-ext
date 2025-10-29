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
package org.commonjava.maven.ext.core.state;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.commonjava.maven.ext.annotation.ConfigValue;
import org.commonjava.maven.ext.core.ManipulationSession;
import org.commonjava.maven.ext.core.impl.DependencyManipulator;
import org.commonjava.maven.ext.io.rest.DefaultTranslator;
import org.commonjava.maven.ext.io.rest.Translator;

/**
 * Captures configuration relating to dependency alignment from the POMs. Used by {@link DependencyManipulator}.
 */
public class RESTState implements State {
    @ConfigValue(docIndex = "dep-manip.html#rest-endpoint")
    public static final String REST_URL = "restURL";

    @ConfigValue(docIndex = "dep-manip.html#rest-endpoint")
    public static final String REST_BREW_PULL_ACTIVE = "restBrewPullActive";

    @ConfigValue(docIndex = "dep-manip.html#rest-endpoint")
    public static final String REST_MODE = "restMode";

    @ConfigValue(docIndex = "dep-manip.html#rest-endpoint")
    public static final String REST_MAX_SIZE = "restMaxSize";

    @ConfigValue(docIndex = "dep-manip.html#rest-endpoint")
    public static final String REST_MIN_SIZE = "restMinSize";

    @ConfigValue(docIndex = "dep-manip.html#rest-endpoint")
    public static final String REST_SUFFIX = "restSuffixAlign";

    @ConfigValue(docIndex = "dep-manip.html#rest-endpoint")
    public static final String REST_HEADERS = "restHeaders";

    @ConfigValue(docIndex = "dep-manip.html#rest-timeouts-and-retries")
    public static final String REST_CONNECTION_TIMEOUT_SEC = "restConnectionTimeout";

    @ConfigValue(docIndex = "dep-manip.html#rest-timeouts-and-retries")
    public static final String REST_SOCKET_TIMEOUT_SEC = "restSocketTimeout";

    @ConfigValue(docIndex = "dep-manip.html#rest-timeouts-and-retries")
    public static final String REST_RETRY_DURATION_SEC = "restRetryDuration";

    private final ManipulationSession session;

    private String restURL;

    private Translator restEndpoint;

    private boolean restSuffixAlign;

    public RESTState(final ManipulationSession session) {
        this.session = session;

        initialise(session.getUserProperties());
    }

    @Override
    public void initialise(Properties userProps) {
        restURL = userProps.getProperty(REST_URL);
        restSuffixAlign = Boolean.parseBoolean(userProps.getProperty(REST_SUFFIX, "true"));

        Boolean brewPullActive = Boolean.parseBoolean(userProps.getProperty(REST_BREW_PULL_ACTIVE));
        String mode = userProps.getProperty(REST_MODE, "");
        int restMaxSize = Integer.parseInt(userProps.getProperty(REST_MAX_SIZE, "-1"));
        int restMinSize = Integer.parseInt(
                userProps.getProperty(
                        REST_MIN_SIZE,
                        String.valueOf(DefaultTranslator.CHUNK_SPLIT_COUNT)));
        Map<String, String> restHeaders = restHeaderParser(userProps.getProperty(REST_HEADERS, ""));
        int restConnectionTimeout = Integer.parseInt(
                userProps.getProperty(
                        REST_CONNECTION_TIMEOUT_SEC,
                        String.valueOf(DefaultTranslator.DEFAULT_CONNECTION_TIMEOUT_SEC)));
        int restSocketTimeout = Integer.parseInt(
                userProps.getProperty(
                        REST_SOCKET_TIMEOUT_SEC,
                        String.valueOf(DefaultTranslator.DEFAULT_SOCKET_TIMEOUT_SEC)));
        int restRetryDuration = Integer.parseInt(
                userProps.getProperty(
                        REST_RETRY_DURATION_SEC,
                        String.valueOf(DefaultTranslator.RETRY_DURATION_SEC)));

        restEndpoint = new DefaultTranslator(
                restURL,
                restMaxSize,
                restMinSize,
                brewPullActive,
                mode,
                restHeaders,
                restConnectionTimeout,
                restSocketTimeout,
                restRetryDuration);
    }

    /**
     * Enabled ONLY if propertyManagement is provided in the user properties / CLI -D options.
     *
     * @see State#isEnabled()
     */
    @Override
    public boolean isEnabled() {
        return restURL != null && !restURL.isEmpty();
    }

    public Translator getVersionTranslator() {
        return restEndpoint;
    }

    public boolean isRestSuffixAlign() {
        return restSuffixAlign;
    }

    // Public API : Used by GME to convert as well.
    public static Map<String, String> restHeaderParser(String value) {
        if (!StringUtils.isEmpty(value)) {
            return Arrays.stream(value.split(","))
                    .map(h -> h.split(":", 2))
                    .filter(h -> h.length > 0 && StringUtils.isNotEmpty(h[0]))
                    .collect(
                            Collectors.toMap(
                                    h -> h[0],
                                    h -> h.length > 1 ? h[1] : "",
                                    (x, y) -> y,
                                    LinkedHashMap::new));
        }
        return Collections.emptyMap();
    }
}
