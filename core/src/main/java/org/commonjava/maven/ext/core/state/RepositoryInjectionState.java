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

import java.util.List;
import java.util.Properties;

import org.commonjava.atlas.maven.ident.ref.ProjectRef;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectVersionRef;
import org.commonjava.maven.ext.annotation.ConfigValue;
import org.commonjava.maven.ext.core.impl.RepositoryInjectionManipulator;
import org.commonjava.maven.ext.core.util.IdUtils;

/**
 * Captures configuration relating to injection repositories from a remote POM.
 * Used by {@link RepositoryInjectionManipulator}.
 */
public class RepositoryInjectionState
        implements State {
    /**
     * Suffix to enable this modder
     */
    @ConfigValue(docIndex = "misc.html#repository-injection")
    private static final String REPOSITORY_INJECTION_PROPERTY = "repositoryInjection";

    @ConfigValue(docIndex = "misc.html#repository-injection")
    private static final String REPOSITORY_INJECTION_POMS = "repositoryInjectionPoms";

    private ProjectVersionRef repoMgmt;

    private List<ProjectRef> groupArtifact;

    public RepositoryInjectionState(final Properties userProps) {
        initialise(userProps);
    }

    public void initialise(Properties userProps) {
        final String gav = userProps.getProperty(REPOSITORY_INJECTION_PROPERTY);
        groupArtifact = IdUtils.parseGAs(userProps.getProperty(REPOSITORY_INJECTION_POMS));
        repoMgmt = gav == null ? null : SimpleProjectVersionRef.parse(gav);
    }

    /**
     * Enabled ONLY if repositoryInjection is provided in the user properties / CLI -D options.
     *
     * @see #REPOSITORY_INJECTION_PROPERTY
     * @see org.commonjava.maven.ext.core.state.State#isEnabled()
     */
    @Override
    public boolean isEnabled() {
        return repoMgmt != null;
    }

    public List<ProjectRef> getRemoteRepositoryInjectionTargets() {
        return groupArtifact;
    }

    public ProjectVersionRef getRemoteRepositoryInjectionMgmt() {
        return repoMgmt;
    }
}
