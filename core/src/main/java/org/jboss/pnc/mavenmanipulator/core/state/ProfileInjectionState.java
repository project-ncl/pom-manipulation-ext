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

import java.util.List;
import java.util.Properties;

import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.jboss.pnc.mavenmanipulator.annotation.ConfigValue;
import org.jboss.pnc.mavenmanipulator.core.impl.ProfileInjectionManipulator;
import org.jboss.pnc.mavenmanipulator.core.util.IdUtils;

/**
 * Captures configuration relating to injection profiles from a remote POM.
 * Used by {@link ProfileInjectionManipulator}.
 */
public class ProfileInjectionState
        implements State {
    /**
     * Suffix to enable this modder
     */
    @ConfigValue(docIndex = "misc.html#profile-injection")
    private static final String PROFILE_INJECTION_PROPERTY = "profileInjection";

    private List<ProjectVersionRef> profileMgmt;

    public ProfileInjectionState(final Properties userProps) {
        initialise(userProps);
    }

    public void initialise(Properties userProps) {
        profileMgmt = IdUtils.parseGAVs(userProps.getProperty(PROFILE_INJECTION_PROPERTY));
    }

    /**
     * Enabled ONLY if repo-reporting-removal is provided in the user properties / CLI -D options.
     *
     * @see #PROFILE_INJECTION_PROPERTY
     * @see State#isEnabled()
     */
    @Override
    public boolean isEnabled() {
        return profileMgmt != null && !profileMgmt.isEmpty();
    }

    public List<ProjectVersionRef> getRemoteProfileInjectionMgmt() {
        return profileMgmt;
    }
}
