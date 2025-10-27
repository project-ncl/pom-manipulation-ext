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
package org.commonjava.maven.ext.core.impl;

import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.commonjava.atlas.maven.ident.ref.ProjectRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.model.Project;
import org.commonjava.maven.ext.core.ManipulationSession;
import org.commonjava.maven.ext.core.state.RepositoryInjectionState;
import org.commonjava.maven.ext.io.ModelIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.commonjava.maven.ext.core.util.IdUtils.ga;

/**
 * {@link Manipulator} implementation that can resolve a remote pom file and inject the remote pom's
 * repository(s) into the current project's top level pom file. Configuration is stored in a
 * {@link RepositoryInjectionState} instance, which is in turn stored in the {@link ManipulationSession}.
 */
@Named("repository-injection")
@Singleton
public class RepositoryInjectionManipulator
        implements Manipulator
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private ModelIO modelBuilder;

    private ManipulationSession session;

    @Inject
    public RepositoryInjectionManipulator(ModelIO modelBuilder)
    {
        this.modelBuilder = modelBuilder;
    }

    /**
     * Initialize the {@link RepositoryInjectionState} state holder in the {@link ManipulationSession}. This state holder detects
     * version-change configuration from the Maven user properties (-D properties from the CLI) and makes it available for
     * later.
     */
    @Override
    public void init( final ManipulationSession session )
    {
        this.session = session;
        session.setState( new RepositoryInjectionState( session.getUserProperties() ) );
    }

    /**
     * Apply the repository injection changes to the the top level pom.
     */
    @Override
    public Set<Project> applyChanges( final List<Project> projects )
            throws ManipulationException
    {
        final RepositoryInjectionState state = session.getState( RepositoryInjectionState.class );
        if ( !session.isEnabled() || !state.isEnabled() )
        {
            logger.debug("{}: Nothing to do!", getClass().getSimpleName() );
            return Collections.emptySet();
        }

        final Set<Project> changed = new HashSet<>();

        final Model remoteModel = modelBuilder.resolveRawModel(state.getRemoteRepositoryInjectionMgmt());
        final List<Repository> remoteRepositories = remoteModel.getRepositories();
        final List<Repository> remotePluginRepositories = remoteModel.getPluginRepositories();

        for ( final Project project : projects )
        {
            logger.info( "Applying changes to: {}:{}", project.getGroupId(), project.getArtifactId() );

            final Model model = project.getModel();

            if ( checkProject( state, project ) )
            {
                // inject repositories
                final List<Repository> repositories = model.getRepositories();
                if ( !remoteRepositories.isEmpty() )
                {
                    final Iterator<Repository> i1 = remoteRepositories.iterator();
                    while ( i1.hasNext() )
                    {
                        addRepository( repositories, i1.next() );
                    }
                    changed.add( project );
                }

                // inject plugin repositories
                final List<Repository> pluginRepositories = model.getPluginRepositories();

                if ( !remotePluginRepositories.isEmpty() )
                {
                    final Iterator<Repository> i2 = remotePluginRepositories.iterator();
                    while ( i2.hasNext() )
                    {
                        addRepository( pluginRepositories, i2.next() );
                    }
                    changed.add( project );
                }
            }
        }

        return changed;
    }

    /**
     * Add the repository to the list of repositories. If an existing repository has the same
     * id it is removed first.
     *
     * @param repositories the current list of repositories
     * @param repository the repository to add
     */
    private void addRepository( final List<Repository> repositories, final Repository repository )
    {
        final Iterator<Repository> i = repositories.iterator();
        while ( i.hasNext() )
        {
            final Repository r = i.next();

            if ( repository.getId().equals( r.getId() ) )
            {
                logger.debug("Removing local repository {}", r);
                i.remove();
                break;
            }
        }

        logger.debug( "Adding repository {}", repository );
        repositories.add(repository);
    }

    private boolean checkProject ( RepositoryInjectionState state, Project project )
    {
        boolean result = false;

        List<ProjectRef> gaToApply = state.getRemoteRepositoryInjectionTargets();
        if ( gaToApply != null )
        {
            // If ProjectRef component contains wildcard artifact treat it differently
            for ( ProjectRef p : gaToApply)
            {
                if ( p.getArtifactId().contains( "*" ) && p.getGroupId().equals( project.getGroupId( ) ) )
                {
                    result = true;
                }
                else if ( p.equals( project.getKey().asProjectRef() ) )
                {
                    result = true;
                }
            }
            logger.debug ("Checking project {} against possible GAs {} and found match {}", project.getKey().asProjectRef(), gaToApply, result);
        }
        else if ( project.isInheritanceRoot() )
        {
            result = true;
        }
        return result;
    }


    @Override
    public int getExecutionIndex()
    {
        return 65;
    }
}
