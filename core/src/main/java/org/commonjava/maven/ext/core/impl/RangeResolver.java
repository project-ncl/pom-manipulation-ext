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

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.commonjava.atlas.maven.ident.ref.ProjectRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.commonjava.maven.ext.common.model.Project;
import org.commonjava.maven.ext.common.util.PropertyResolver;
import org.commonjava.maven.ext.core.ManipulationSession;
import org.commonjava.maven.ext.core.state.RangeResolverState;
import org.commonjava.maven.ext.core.state.State;
import org.commonjava.maven.ext.io.resolver.GalleyAPIWrapper;
import org.commonjava.maven.galley.maven.GalleyMavenException;
import org.commonjava.maven.galley.maven.model.view.meta.MavenMetadataView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This Manipulator runs first and is active by default. It will resolve any ranges and update
 * them to locked values.
 */
@Named( "range-resolver" )
@Singleton
public class RangeResolver
                implements Manipulator
{
    private static final Logger logger = LoggerFactory.getLogger( RangeResolver.class );

    private ManipulationSession session;

    private final GalleyAPIWrapper readerWrapper;

    @Inject
    public RangeResolver( final GalleyAPIWrapper readerWrapper )
    {
        this.readerWrapper = readerWrapper;
    }

    @Override
    public void init( final ManipulationSession session )
    {
        this.session = session;
        session.setState( new RangeResolverState( session.getUserProperties() ) );
    }

    @Override
    public Set<Project> applyChanges( final List<Project> projects ) throws ManipulationException
    {
        final RangeResolverState state = session.getState( RangeResolverState.class );

        if ( !session.isEnabled() || !session.anyStateEnabled( State.activeByDefault ) || state == null
                || !state.isEnabled() )
        {
            logger.debug( "{}: Nothing to do!", getClass().getSimpleName() );
            return Collections.emptySet();
        }

        final Set<Project> changed = new HashSet<>( projects.size() );

        for ( final Project p : projects )
        {
            try
            {
                if ( p.getModel().getBuild() != null )
                {
                    // PluginManagement
                    if ( p.getModel().getBuild().getPluginManagement() != null )
                    {
                        p.getModel()
                         .getBuild()
                         .getPluginManagement()
                         .getPlugins()
                         .stream()
                         .filter( plugin -> StringUtils.isNotEmpty( plugin.getVersion() ) )
                         .forEach( plugin -> handleVersionWithRange( projects, plugin ) );
                    }
                    // Plugins
                    p.getModel()
                     .getBuild()
                     .getPlugins()
                     .stream()
                     .filter( plugin -> StringUtils.isNotEmpty( plugin.getVersion() ) )
                     .forEach( plugin -> handleVersionWithRange( projects, plugin ) );
                }

                // DependencyManagement
                if ( p.getModel().getDependencyManagement() != null )
                {
                    p.getModel().getDependencyManagement()
                     .getDependencies()
                     .stream()
                     .filter( dependency -> StringUtils.isNotEmpty( dependency.getVersion() ) )
                     .forEach( dependency -> handleVersionWithRange( projects, dependency ) );
                }
                // Dependencies
                p.getModel().getDependencies()
                 .stream()
                 .filter( dependency -> StringUtils.isNotEmpty( dependency.getVersion() ) )
                 .forEach( dependency -> handleVersionWithRange( projects, dependency ) );

                p.getModel().getProfiles().stream().filter( profile -> profile.getDependencyManagement() != null )
                  .forEach( profile -> {
                    // DependencyManagement
                        profile.getDependencyManagement()
                               .getDependencies()
                               .stream()
                               .filter( dependency -> StringUtils.isNotEmpty( dependency.getVersion() ) )
                               .forEach( dependency -> handleVersionWithRange( projects, dependency ) );
                    // Dependencies
                    profile.getDependencies()
                           .stream()
                           .filter( dependency -> StringUtils.isNotEmpty( dependency.getVersion() ) )
                           .forEach( dependency -> handleVersionWithRange( projects, dependency ) );

                    if ( profile.getBuild() != null )
                    {
                        // PluginManagement
                        if ( profile.getBuild().getPluginManagement() != null )
                        {
                            profile.getBuild()
                                   .getPluginManagement()
                                   .getPlugins()
                                   .stream()
                                   .filter( plugin -> StringUtils.isNotEmpty( plugin.getVersion() ) )
                                   .forEach( plugin -> handleVersionWithRange( projects, plugin ) );
                        }
                        // Plugins
                        profile.getBuild()
                               .getPlugins()
                               .stream()
                               .filter( plugin -> StringUtils.isNotEmpty( plugin.getVersion() ) )
                               .forEach( plugin -> handleVersionWithRange( projects, plugin ) );
                    }
                } );

                changed.add( p );
            }
            catch ( RuntimeException e )
            {
                if ( e.getCause() instanceof ManipulationException )
                {
                    throw ( ManipulationException ) e.getCause();
                }
                throw e;
            }
        }
        return changed;
    }

    private void handleVersionWithRange( List<Project> projects, Plugin p )
    {
        final String version = PropertyResolver.resolvePropertiesUnchecked( session, projects, p.getVersion() );

        try
        {
            final VersionRange versionRange = VersionRange.createFromVersionSpec( version );

            // If it's a range then try to use a matching version...
            if ( versionRange.hasRestrictions() )
            {
                final String groupId = PropertyResolver.resolvePropertiesUnchecked( session, projects, p.getGroupId() );
                final String artifactId = PropertyResolver.resolvePropertiesUnchecked( session, projects,
                        p.getArtifactId() );
                final List<ArtifactVersion> versions = getVersions( new SimpleProjectRef( groupId, artifactId ) );
                final ArtifactVersion result = versionRange.matchVersion( versions );

                logger.debug( "Resolved range for plugin {} got versionRange {} and potential replacement of {}", p,
                        versionRange, result );

                if ( result != null )
                {
                    p.setVersion( result.toString() );
                }
                else
                {
                    logger.warn( "Unable to find replacement for range." );
                }
            }
        }
        catch ( InvalidVersionSpecificationException e )
        {
            throw new ManipulationUncheckedException( new ManipulationException( "Invalid range", e ) );
        }
    }

    private void handleVersionWithRange( List<Project> projects, Dependency d )
    {
        final String version = PropertyResolver.resolvePropertiesUnchecked( session, projects, d.getVersion() );

        try
        {
            final VersionRange versionRange = VersionRange.createFromVersionSpec( version );

            // If it's a range then try to use a matching version...
            if ( versionRange.hasRestrictions() )
            {
                final String groupId = PropertyResolver.resolvePropertiesUnchecked( session, projects, d.getGroupId() );
                final String artifactId = PropertyResolver.resolvePropertiesUnchecked( session, projects,
                        d.getArtifactId() );
                final List<ArtifactVersion> versions = getVersions( new SimpleProjectRef( groupId, artifactId ) );
                final ArtifactVersion result = versionRange.matchVersion( versions );

                logger.debug( "Resolved range for dependency {} got versionRange {} and potential replacement of {}", d,
                        versionRange, result );

                if ( result != null )
                {
                    d.setVersion( result.toString() );
                }
                else
                {
                    logger.warn( "Unable to find replacement for range" );
                }
            }
        }
        catch ( InvalidVersionSpecificationException e )
        {
            throw new ManipulationUncheckedException( new ManipulationException( "Invalid range", e ) );
        }
    }

    private List<ArtifactVersion> getVersions( ProjectRef ga )
    {
        final MavenMetadataView mavenMetadataView;
        try
        {
            mavenMetadataView = readerWrapper.readMetadataView( ga );
        }
        catch ( GalleyMavenException e )
        {
            throw new ManipulationUncheckedException(
                    new ManipulationException( "Caught Galley exception processing artifact", e ) );
        }
        return mavenMetadataView.resolveXPathToAggregatedStringList( "/metadata/versioning/versions/version", true, -1 )
                                .stream()
                                .distinct()
                                .map( DefaultArtifactVersion::new )
                                .collect( Collectors.toList() );
    }

    @Override
    public int getExecutionIndex()
    {
        // Low value index so it runs very early in order to lock the versions down prior to attempting REST alignment.
        return 2;
    }
}
