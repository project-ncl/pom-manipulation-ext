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
import org.jboss.pnc.mavenmanipulator.core.impl.RepoAndReportingRemovalManipulator;

/**
 * Captures configuration relating to removing reporting/repositories from the POMs. Used by
 * {@link RepoAndReportingRemovalManipulator}.
 *
 */
public class RepoReportingState
        implements State {
    @ConfigValue(docIndex = "misc.html#repository-and-reporting-removal")
    private static final String RR_SUFFIX_SYSPROP = "repoReportingRemoval";

    @ConfigValue(docIndex = "misc.html#repository-and-reporting-removal")
    private static final String RR_SUFFIX_SYSPROP_LOCAL = "repoRemovalIgnorelocalhost";

    /**
     * Default value is off.<br/>
     * It can be overridden to:
     * <br/>
     * '' (empty) which means disabled
     * <br/>
     * 'settings.xml' which implicitly means the current build directory
     * <br/>
     * 'filename' which should be a valid path to write to
     */
    @ConfigValue(docIndex = "misc.html#repository-and-reporting-removal")
    private static final String RR_SETTINGS_SFX_SYSPROP = "repoRemovalBackup";

    private boolean removal;

    private String settings;

    private boolean ignoreLocal;

    public RepoReportingState(final Properties userProps) {
        initialise(userProps);
    }

    public void initialise(Properties userProps) {
        removal = Boolean.parseBoolean(userProps.getProperty(RR_SUFFIX_SYSPROP));
        ignoreLocal = Boolean.parseBoolean(userProps.getProperty(RR_SUFFIX_SYSPROP_LOCAL));
        settings = userProps.getProperty(RR_SETTINGS_SFX_SYSPROP);
    }

    /**
     * Enabled ONLY if repo-reporting-removal is provided in the user properties / CLI -D options.
     *
     * @see #RR_SUFFIX_SYSPROP
     * @see State#isEnabled()
     */
    @Override
    public boolean isEnabled() {
        return removal;
    }

    public String getRemovalBackupSettings() {
        return settings;
    }

    public boolean ignoreLocal() {
        return ignoreLocal;
    }
}
