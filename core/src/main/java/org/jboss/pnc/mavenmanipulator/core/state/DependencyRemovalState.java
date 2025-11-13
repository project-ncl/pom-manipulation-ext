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

import org.commonjava.atlas.maven.ident.ref.ProjectRef;
import org.jboss.pnc.mavenmanipulator.annotation.ConfigValue;
import org.jboss.pnc.mavenmanipulator.core.util.IdUtils;

/**
 * Captures configuration relating to dependency removal from the POMs.
 */
public class DependencyRemovalState
        implements State {
    /**
     * The name of the property which contains a comma separated list of dependencies to remove.
     *
     * <pre>
     * <code>-DdependencyRemoval=org.foo:bar,....</code>
     * </pre>
     */
    @ConfigValue(docIndex = "dep-manip.html#dependency-removal")
    private static final String DEPENDENCY_REMOVAL_PROPERTY = "dependencyRemoval";

    private List<ProjectRef> dependencyRemoval;

    public DependencyRemovalState(final Properties userProps) {
        initialise(userProps);
    }

    public void initialise(Properties userProps) {
        dependencyRemoval = IdUtils.parseGAs(userProps.getProperty(DEPENDENCY_REMOVAL_PROPERTY));
    }

    /**
     * Enabled ONLY if dependency-removal is provided in the user properties / CLI -D options.
     *
     * @see State#isEnabled()
     */
    @Override
    public boolean isEnabled() {
        return dependencyRemoval != null && !dependencyRemoval.isEmpty();
    }

    /**
     * @return the dependencies we wish to remove.
     */
    public List<ProjectRef> getDependencyRemoval() {
        return dependencyRemoval;
    }
}
