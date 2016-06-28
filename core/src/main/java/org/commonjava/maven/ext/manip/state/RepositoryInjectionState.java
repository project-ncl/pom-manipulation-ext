/**
 * Copyright (C) 2012 Red Hat, Inc. (jcasey@redhat.com)
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
package org.commonjava.maven.ext.manip.state;

import org.commonjava.maven.atlas.ident.ref.InvalidRefException;
import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef;
import org.commonjava.maven.ext.manip.impl.RepositoryInjectionManipulator;
import org.commonjava.maven.ext.manip.util.IdUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Properties;

/**
 * Captures configuration relating to injection repositories from a remote POM.
 * Used by {@link RepositoryInjectionManipulator}.
 */
public class RepositoryInjectionState
    implements State
{
    private static final Logger LOGGER = LoggerFactory.getLogger( RepositoryInjectionState.class );

    /**
     * Suffix to enable this modder
     */
    private static final String REPOSITORY_INJECTION_PROPERTY = "repositoryInjection";

    private static final String REPOSITORY_INJECTION_POMS = "repositoryInjectionPoms";

    private final ProjectVersionRef repoMgmt;

    private final List<ProjectRef> groupArtifact;

    public RepositoryInjectionState( final Properties userProps )
    {
        final String gav = userProps.getProperty( REPOSITORY_INJECTION_PROPERTY );
        groupArtifact = IdUtils.parseGAs( userProps.getProperty( REPOSITORY_INJECTION_POMS ) );

        ProjectVersionRef ref = null;
        if ( gav != null )
        {
            try
            {
                ref = SimpleProjectVersionRef.parse( gav );
            }
            catch ( final InvalidRefException e )
            {
                LOGGER.error( "Skipping repository injection! Got invalid repositoryInjection GAV: {}", gav );
                throw e;
            }
        }

        repoMgmt = ref;
    }

    /**
     * Enabled ONLY if repositoryInjection is provided in the user properties / CLI -D options.
     *
     * @see #REPOSITORY_INJECTION_PROPERTY
     * @see org.commonjava.maven.ext.manip.state.State#isEnabled()
     */
    @Override
    public boolean isEnabled()
    {
        return repoMgmt != null;
    }

    public List<ProjectRef> getRemoteRepositoryInjectionTargets()
    {
        return groupArtifact;
    }

    public ProjectVersionRef getRemoteRepositoryInjectionMgmt()
    {
        return repoMgmt;
    }
}

