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
package org.jboss.pnc.maven_manipulator.core.state;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.jboss.pnc.maven_manipulator.annotation.ConfigValue;
import org.jboss.pnc.maven_manipulator.core.impl.ProfileInjectionManipulator;

/**
 * Captures configuration relating to injection profiles from a remote POM.
 * Used by {@link ProfileInjectionManipulator}.
 */
public class ProfileRemovalState
        implements State {
    /**
     * Suffix to enable this modder
     */
    @ConfigValue(docIndex = "misc.html#profile-removal")
    private static final String PROFILE_REMOVAL_PROPERTY = "profileRemoval";

    private List<String> profiles;

    public ProfileRemovalState(final Properties userProps) {
        initialise(userProps);
    }

    public void initialise(Properties userProps) {
        final String p = userProps.getProperty(PROFILE_REMOVAL_PROPERTY);

        profiles = isNotEmpty(p) ? Arrays.asList(p.split(",")) : null;
    }

    /**
     * Enabled ONLY if repo-reporting-removal is provided in the user properties / CLI -D options.
     *
     * @see #PROFILE_REMOVAL_PROPERTY
     * @see State#isEnabled()
     */
    @Override
    public boolean isEnabled() {
        return profiles != null && profiles.size() > 0;
    }

    public List<String> getProfileRemoval() {
        return profiles;
    }
}
