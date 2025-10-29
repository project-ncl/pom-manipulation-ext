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

/**
 * Captures configuration parameters for use with {@link org.commonjava.maven.ext.core.impl.BOMBuilderManipulator}.
 */
public class BOMInjectingState
        implements State {
    /** Set this property to true using <code>-DbomBuilder=true</code> to activate BOM Builder Plugin */
    @ConfigValue(docIndex = "dep-manip.html#bom-generation")
    public static final String BOM_BUILDER = "bomBuilder";

    private boolean builderEnabled;

    /**
     * Detects whether this state is enabled..
     *
     * @param userProperties the properties for the manipulator
     */
    public BOMInjectingState(final Properties userProperties) {
        initialise(userProperties);
    }

    public void initialise(Properties userProperties) {
        builderEnabled = Boolean.parseBoolean(userProperties.getProperty(BOM_BUILDER, "false"));
    }

    /**
     * @see BOMInjectingState#BOM_BUILDER
     *
     * @return true if state is enabled.
     */
    @Override
    public boolean isEnabled() {
        return builderEnabled;
    }

}
