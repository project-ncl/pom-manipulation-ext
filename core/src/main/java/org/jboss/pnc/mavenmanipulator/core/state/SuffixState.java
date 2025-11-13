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
package org.jboss.pnc.mavenmanipulator.core.state;

import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.jboss.pnc.mavenmanipulator.annotation.ConfigValue;
import org.jboss.pnc.mavenmanipulator.core.impl.SuffixManipulator;

/**
 * Captures configuration relating to suffix stripping from the POM version. Used by {@link SuffixManipulator}.
 *
 */
public class SuffixState
        implements State {
    private static final String DEFAULT_SUFFIX_STRIP = "(.*)(.jbossorg-\\d+)$";

    /**
     * Suffix to enable and configure this modder.
     *
     * This will activate this modder with the preconfigured default of <code>(.*)(.jbossorg-\d+)$</code>
     *
     * <pre>
     * <code>-DversionSuffixStrip</code>
     * </pre>
     *
     * This will activate this modder with the specified suffix
     *
     * <pre>
     * <code>-DversionSuffixStrip=jbossorg-\d+</code>
     * </pre>
     */
    @ConfigValue(docIndex = "project-version-manip.html#suffix-stripping")
    static final String SUFFIX_STRIP_PROPERTY = "versionSuffixStrip";

    private String suffixStrip;

    public SuffixState(final Properties userProps) {
        initialise(userProps);
    }

    public void initialise(Properties userProps) {
        suffixStrip = userProps.getProperty(SUFFIX_STRIP_PROPERTY);

        if (StringUtils.isEmpty(suffixStrip) && userProps.containsKey(SUFFIX_STRIP_PROPERTY)) {
            suffixStrip = DEFAULT_SUFFIX_STRIP;
        }
        if ("NONE".equals(suffixStrip)) {
            suffixStrip = "";
        }
    }

    /**
     * Enabled ONLY if propertyManagement is provided in the user properties / CLI -D options.
     *
     * @see State#isEnabled()
     */
    @Override
    public boolean isEnabled() {
        return suffixStrip != null && !suffixStrip.isEmpty();
    }

    public String getSuffixStrip() {
        return suffixStrip;
    }
}
