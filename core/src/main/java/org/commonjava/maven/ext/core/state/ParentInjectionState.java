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

import org.apache.maven.model.Parent;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectVersionRef;
import org.commonjava.maven.ext.annotation.ConfigValue;
import org.commonjava.maven.ext.core.impl.ParentInjectionManipulator;

/**
 * Captures configuration relating to injection of parent pom.
 * Used by {@link ParentInjectionManipulator}.
 */
public class ParentInjectionState
        implements State {
    /**
     * Suffix to enable this modder
     */
    @ConfigValue(docIndex = "misc.html#parent-injection")
    private static final String PARENT_INJECTION_PROPERTY = "parentInjection";

    private Parent parent = new Parent();

    public ParentInjectionState(final Properties userProps) {
        initialise(userProps);
    }

    public void initialise(Properties userProps) {
        final String gav = userProps.getProperty(PARENT_INJECTION_PROPERTY);

        if (gav != null) {
            ProjectVersionRef ref = SimpleProjectVersionRef.parse(gav);
            parent = new Parent();
            parent.setGroupId(ref.getGroupId());
            parent.setArtifactId(ref.getArtifactId());
            parent.setVersion(ref.getVersionString());
            parent.setRelativePath("");
        } else {
            parent = null;
        }
    }

    /**
     * Enabled ONLY if parentInjection is provided in the user properties / CLI -D options.
     *
     * @see #PARENT_INJECTION_PROPERTY
     * @see State#isEnabled()
     */
    @Override
    public boolean isEnabled() {
        return parent != null;
    }

    public Parent getParentInjection() {
        return parent;
    }
}
