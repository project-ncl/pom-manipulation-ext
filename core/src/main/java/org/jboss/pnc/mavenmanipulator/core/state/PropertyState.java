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
import org.jboss.pnc.mavenmanipulator.core.impl.PropertyManipulator;
import org.jboss.pnc.mavenmanipulator.core.util.IdUtils;

/**
 * Captures configuration relating to property injection from the POMs. Used by {@link PropertyManipulator}.
 *
 */
public class PropertyState
        implements State {

    /**
     * Suffix to enable this modder. The name of the property which contains the GAV of the remote pom from
     * which to retrieve property mapping information.
     *
     * <pre>
     * <code>-DpropertyManagement:org.foo:bar-property-mgmt:1.0</code>
     * </pre>
     */
    @ConfigValue(docIndex = "misc.html#property-override")
    private static final String PROPERTY_MANAGEMENT_POM_PROPERTY = "propertyManagement";

    private List<ProjectVersionRef> propertyMgmt;

    public PropertyState(final Properties userProps) {
        initialise(userProps);
    }

    public void initialise(Properties userProps) {
        propertyMgmt = IdUtils.parseGAVs(userProps.getProperty(PROPERTY_MANAGEMENT_POM_PROPERTY));
    }

    /**
     * Enabled ONLY if propertyManagement is provided in the user properties / CLI -D options.
     *
     * @see State#isEnabled()
     */
    @Override
    public boolean isEnabled() {
        return propertyMgmt != null && !propertyMgmt.isEmpty();
    }

    public List<ProjectVersionRef> getRemotePropertyMgmt() {
        return propertyMgmt;
    }
}
