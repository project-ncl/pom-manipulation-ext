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

import java.util.Properties;

import org.commonjava.maven.ext.annotation.ConfigValue;
import org.commonjava.maven.ext.core.impl.PluginInjectingManipulator;

/**
 * Captures configuration parameters for use with {@link PluginInjectingManipulator}. This state implementation captures
 * two properties:
 *
 * <ul>
 * <li><b>projectSrcSkip</b> - If true, don't try to inject the project-sources-maven-plugin.</li>
 * <li><b>project.src.version</b> - The version of the project-sources-maven-plugin to be injected.</li>
 * <li><b>projectMetaSkip</b> - If true, don't try to inject the buildmetadata-maven-plugin.</li>
 * <li><b>project.meta.version</b> - The version of the buildmetadata-maven-plugin to be injected.</li>
 * </ul>
 */
@Deprecated
public class PluginInjectingState
        implements State {

    /**
     * Set this property to true using <code>-DprojectSrcSkip=true</code> in order to turn off injection of the
     * project-sources plugin.
     */
    @ConfigValue(docIndex = "plugin-manip.html#project-sources--build-metadata-plugin-injection", deprecated = true)
    private static final String PROJECT_SOURCES_SKIP_PROPERTY = "projectSrcSkip";

    /**
     * Set this property to true using <code>-DprojectMetaSkip=true</code> in order to turn off injection of the
     * project-sources plugin.
     */
    @ConfigValue(docIndex = "plugin-manip.html#project-sources--build-metadata-plugin-injection", deprecated = true)
    private static final String BMMP_SKIP_PROPERTY = "projectMetaSkip";

    /** Set this property to control the version of the project-sources plugin to be injected. */
    @ConfigValue(docIndex = "plugin-manip.html#project-sources--build-metadata-plugin-injection", deprecated = true)
    private static final String PROJECT_SOURCES_PLUGIN_VERSION_PROPERTY = "projectSrcVersion";

    /** The default plugin version to use in case no alternative version is specified on the command line. */
    private static final String DEFAULT_PROJECT_SOURCES_PLUGIN_VERSION = "1.0";

    /** Set this property to control the version of the build-metadata plugin to be injected. */
    @ConfigValue(docIndex = "plugin-manip.html#project-sources--build-metadata-plugin-injection", deprecated = true)
    private static final String BMMP_VERSION_PROPERTY = "projectMetaVersion";

    private static final String DEFAULT_BMMP_VERSION = "1.7.0";

    private boolean projectsourcesEnabled;

    private boolean metadataEnabled;

    private String projectSrcPluginVersion;

    private String bmmpVersion;

    static {
        State.activeByDefault.add(PluginInjectingState.class);
    }

    /**
     * Detects the projectSrcSkip and project.src.version user properties. Sets the projectsourcesEnabled flag and the
     * plugin version accordingly.
     *
     * @param userProperties the properties for the manipulator
     */
    public PluginInjectingState(final Properties userProperties) {
        initialise(userProperties);
    }

    public void initialise(Properties userProperties) {
        projectsourcesEnabled = !Boolean
                .parseBoolean(userProperties.getProperty(PROJECT_SOURCES_SKIP_PROPERTY, "true"));
        metadataEnabled = !Boolean.parseBoolean(userProperties.getProperty(BMMP_SKIP_PROPERTY, "true"));

        projectSrcPluginVersion = userProperties
                .getProperty(PROJECT_SOURCES_PLUGIN_VERSION_PROPERTY, DEFAULT_PROJECT_SOURCES_PLUGIN_VERSION);
        bmmpVersion = userProperties.getProperty(BMMP_VERSION_PROPERTY, DEFAULT_BMMP_VERSION);
    }

    /**
     * @see PluginInjectingState#PROJECT_SOURCES_SKIP_PROPERTY
     *
     * @return true if <b>either</b> of {@link #isProjectSourcesPluginEnabled()} or
     *         {@link #isBuildMetadataPluginEnabled()} is enabled.
     */
    @Override
    public boolean isEnabled() {
        return isBuildMetadataPluginEnabled() || isProjectSourcesPluginEnabled();
    }

    /**
     * @see PluginInjectingState#BMMP_SKIP_PROPERTY
     * @return whether the BuildMetadata plugin is projectsourcesEnabled.
     */
    public boolean isProjectSourcesPluginEnabled() {
        return projectsourcesEnabled;
    }

    /**
     * @see PluginInjectingState#BMMP_SKIP_PROPERTY
     * @return whether the BuildMetadata plugin is projectsourcesEnabled.
     */
    public boolean isBuildMetadataPluginEnabled() {
        return metadataEnabled;
    }

    /**
     * @see #PROJECT_SOURCES_PLUGIN_VERSION_PROPERTY
     * @see #DEFAULT_PROJECT_SOURCES_PLUGIN_VERSION
     * @return the ProjectSources plugin version
     */
    public String getProjectSourcesPluginVersion() {
        return projectSrcPluginVersion;
    }

    /**
     * @see #BMMP_VERSION_PROPERTY
     * @see #DEFAULT_BMMP_VERSION
     * @return the BuildMetadata plugin version
     */
    public String getBuildMetadataPluginVersion() {
        return bmmpVersion;
    }

}
