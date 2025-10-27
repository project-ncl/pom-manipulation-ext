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

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Profile;
import org.commonjava.atlas.maven.ident.ref.ArtifactRef;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.commonjava.maven.ext.common.model.Project;
import org.commonjava.maven.ext.common.util.ProfileUtils;
import org.commonjava.maven.ext.core.ManipulationSession;
import org.commonjava.maven.ext.core.state.SuffixState;
import org.commonjava.maven.ext.core.util.PropertiesUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link Manipulator} implementation that can strip a version suffix. Note that this is quite similar to the
 * {@link ProjectVersioningManipulator} which can add suffixes. However this has been spawned off into a separate tool
 * so that its possible to remove the suffix before handing off to the {@link RESTCollector}.
 *
 * Configuration is stored in a {@link SuffixState} instance, which is in turn stored in the {@link ManipulationSession}.
 */
@Named("suffix-manipulator")
@Singleton
public class SuffixManipulator
    implements Manipulator
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private ManipulationSession session;

    @Override
    public void init( final ManipulationSession session )
    {
        this.session = session;
        session.setState( new SuffixState( session.getUserProperties() ) );
    }

    /**
     * Apply the property changes to the list of {@link Project}'s given.
     */
    @Override
    public Set<Project> applyChanges( final List<Project> projects ) throws ManipulationException
    {
        final SuffixState state = session.getState( SuffixState.class );

        if ( !session.isEnabled() || !state.isEnabled() )
        {
            logger.debug("{}: Nothing to do!", getClass().getSimpleName());
            return Collections.emptySet();
        }

        final Set<Project> changed = new HashSet<>();
        final Pattern suffixStripPattern = Pattern.compile( state.getSuffixStrip() );

        for ( final Project project : projects )
        {
            final Parent parent = project.getModel().getParent();

            if ( parent != null && parent.getVersion() != null )
            {
                Matcher m = suffixStripPattern.matcher( parent.getVersion() );

                if ( m.matches() )
                {
                    final String version = m.group( 1 );
                    logger.info( "Stripping suffix for {} and resetting parent version from {} to {}", project.getKey(), parent.getVersion(), version );
                    parent.setVersion( version );
                    changed.add( project );
                }
            }
            // Not using project.getVersion as that can return the inherited parent version
            if ( project.getModel().getVersion() != null )
            {
                Matcher m = suffixStripPattern.matcher( project.getModel().getVersion() );
                if ( m.matches() )
                {
                    final String version = m.group( 1 );
                    logger.info( "Stripping suffix and resetting project version from {} to {}", project.getModel().getVersion(), version );
                    project.getModel().setVersion( version );
                    changed.add( project );
                }
            }

            processDependencies( suffixStripPattern, project, project.getResolvedDependencies( session ) );
            processDependencies( suffixStripPattern, project, project.getResolvedManagedDependencies( session ) );
            processPlugins( suffixStripPattern, project, project.getResolvedPlugins( session ) );
            processPlugins( suffixStripPattern, project, project.getResolvedManagedPlugins( session ) );

            List<Profile> profiles = ProfileUtils.getProfiles( session, project.getModel() );
            for ( Profile p : profiles )
            {
                processDependencies( suffixStripPattern, project, project.getResolvedProfileDependencies( session ).get( p ) );
                processDependencies( suffixStripPattern, project, project.getResolvedProfileManagedDependencies( session ).get( p ) );
                processPlugins( suffixStripPattern, project, project.getResolvedProfilePlugins( session ).get( p ) );
                processPlugins( suffixStripPattern, project, project.getResolvedProfileManagedPlugins( session ).get( p ) );
            }
        }
        return changed;
    }

    private void processPlugins( Pattern suffixStripPattern, Project project,
                                 Map<ProjectVersionRef, Plugin> plugins ) throws ManipulationException
    {
        try
        {
            if ( plugins != null )
            {
                for ( final Entry<ProjectVersionRef, Plugin> entry : plugins.entrySet() )
                {
                    final ProjectVersionRef a = entry.getKey();
                    final Plugin original = entry.getValue();
                    final Matcher m = suffixStripPattern.matcher( a.getVersionString() );

                    if ( m.matches() )
                    {
                        final String stripped = m.group( 1 );

                        logger.info( "Stripping suffix from plugin {} (version {}) to {}", a, original.getVersion(),
                                stripped );

                        // If it's a property, update the value, otherwise inline the version change.
                        if ( original.getVersion().contains( "$" ) )
                        {
                            handleProperties( project, original.getVersion(), stripped );
                        }
                        else
                        {
                            original.setVersion( stripped );
                        }
                    }
                }
            }
        }
        catch ( ManipulationUncheckedException e )
        {
            throw (ManipulationException) e.getCause();
        }
    }

    private void processDependencies( Pattern suffixStripPattern, Project project, Map<ArtifactRef, Dependency> deps )
                    throws ManipulationException
    {
        try
        {
            if ( deps != null )
            {
                for ( Entry<ArtifactRef, Dependency> entry : deps.entrySet() )
                {
                    final ArtifactRef a = entry.getKey();
                    final Dependency original = entry.getValue();
                    final Matcher m = suffixStripPattern.matcher( a.getVersionString() );

                    if ( m.matches() )
                    {
                        final String stripped = m.group( 1 );

                        logger.info( "Stripping suffix from dependency {} (version {}) to {}", a,
                                original.getVersion(), stripped );

                        // If it's a property, update the value, otherwise inline the version change.
                        if ( original.getVersion().contains( "$" ) )
                        {
                            handleProperties( project, original.getVersion(), stripped );
                        }
                        else
                        {
                            original.setVersion( stripped );
                        }
                    }
                }
            }
        }
        catch ( ManipulationUncheckedException e )
        {
            throw (ManipulationException) e.getCause();
        }
    }

    // Wrap code that throws a ManipulationException to hide the unchecked exception handling.
    private void handleProperties( Project project, String original, String stripped )
    {
        try
        {
            PropertiesUtils.updateProperties( session, project, true,
                    PropertiesUtils.extractPropertyName( original ), stripped );
        }
        catch ( ManipulationException e )
        {
            throw new ManipulationUncheckedException( e );
        }
    }

    @Override
    public int getExecutionIndex()
    {
        return 6;
    }
}
