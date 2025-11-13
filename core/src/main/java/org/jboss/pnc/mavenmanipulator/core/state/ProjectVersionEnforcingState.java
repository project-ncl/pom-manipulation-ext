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

import org.jboss.pnc.mavenmanipulator.annotation.ConfigValue;

/**
 * Captures configuration relating to enforcing removal of use of ${project.version} from pom.
 */
public class ProjectVersionEnforcingState
        implements State {
    /**
     * Property used to set the enforcement mode.
     */
    @ConfigValue(docIndex = "misc.html#projectversion-expression-replacement")
    private static final String ENFORCE_PROJECT_VERSION = "enforceProjectVersion";

    static {
        State.activeByDefault.add(ProjectVersionEnforcingState.class);
    }

    // Default to on.
    private boolean enabled = true;

    public ProjectVersionEnforcingState(final Properties userProps) {
        initialise(userProps);
    }

    public void initialise(Properties userProps) {
        enabled = Boolean.parseBoolean(userProps.getProperty(ENFORCE_PROJECT_VERSION, "true"));
    }

    /**
     * Enabled by default; may be disabled by enforce-project-version.
     *
     * @see #ENFORCE_PROJECT_VERSION
     * @see State#isEnabled()
     * @see EnforcingMode
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
