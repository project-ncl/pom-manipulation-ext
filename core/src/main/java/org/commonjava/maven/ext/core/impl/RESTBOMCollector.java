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

import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectVersionRef;
import org.commonjava.maven.ext.common.model.Project;
import org.commonjava.maven.ext.core.ManipulationSession;
import org.commonjava.maven.ext.core.state.DependencyState;
import org.commonjava.maven.ext.core.state.PluginState;
import org.commonjava.maven.ext.core.state.ProfileInjectionState;
import org.commonjava.maven.ext.core.state.RESTState;
import org.commonjava.maven.ext.core.util.PropertiesUtils;
import org.commonjava.maven.ext.io.rest.RestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * This Manipulator runs very early. It makes a REST call to an external service to increment the GAVs to align the project version
 * and dependencies to. It will prepopulate Project GA versions into the VersioningState in case the VersioningManipulator has been
 * activated and the various remove BOM/Plugin/Profiles as well.
 */
@Named("rest-bom-manipulator")
@Singleton
public class RESTBOMCollector
                implements Manipulator
{
    private static final Logger logger = LoggerFactory.getLogger( RESTBOMCollector.class );

    private ManipulationSession session;

    @Override
    public void init( final ManipulationSession session )
    {
        this.session = session;
        session.setState( new RESTState( session ) );
    }

    /**
     * No-op in this case - any changes, if configured, would happen in Versioning or other Manipulators.
     */
    @Override
    public Set<Project> applyChanges( final List<Project> projects ) throws RestException
    {
        populateBOMVersions( );

        return Collections.emptySet();
    }

    @Override
    public int getExecutionIndex()
    {
        // Low value index so it runs very early in order to call the REST API.
        return 4;
    }

    private void populateBOMVersions( ) throws RestException
    {
        final RESTState state = session.getState( RESTState.class );
        final DependencyState ds = session.getState( DependencyState.class );
        final PluginState ps = session.getState( PluginState.class );
        final ProfileInjectionState pis = session.getState( ProfileInjectionState.class );

        if ( !session.isEnabled() || !state.isEnabled() )
        {
            logger.debug( "{}: Nothing to do!", getClass().getSimpleName() );
            return;
        }

        final ArrayList<ProjectVersionRef> restParam = new ArrayList<>();

        // If the various state e.g. dependencyState::getRemoteBOMDepMgmt contains suffix then process it.
        // We only recognise dependencyManagement of the form g:a:version-rebuild not g:a:version-rebuild-<numeric>.
        populateRestParam( restParam, "dependencyManagement", ds.getRemoteBOMDepMgmt() );
        populateRestParam( restParam, "pluginManagement", ps.getRemotePluginMgmt() );
        populateRestParam( restParam, "profileInjectionManagement", pis.getRemoteProfileInjectionMgmt() );

        if ( restParam.size() > 0 )
        {
            // Call the REST to populate the result.
            logger.debug( "Passing {} BOM GAVs following into the REST client api {}", restParam.size(), restParam );
            logger.info( "Calling REST client for BOMs..." );
            Map<ProjectVersionRef, String> restResult = state.getVersionTranslator().lookupVersions( restParam );
            logger.debug( "REST Client returned for BOMs {}", restResult );

            final ListIterator<ProjectVersionRef> emptyIterator = Collections.<ProjectVersionRef>emptyList().listIterator();

            // Process rest result for boms
            updateBOM( ( ds.getRemoteBOMDepMgmt() == null ? emptyIterator : ds.getRemoteBOMDepMgmt().listIterator() ),
                       restResult );
            updateBOM( ( ps.getRemotePluginMgmt() == null ? emptyIterator : ps.getRemotePluginMgmt().listIterator() ),
                       restResult );
            updateBOM( ( pis.getRemoteProfileInjectionMgmt() == null ?
                            emptyIterator :
                            pis.getRemoteProfileInjectionMgmt().listIterator() ), restResult );
        }
        else
        {
            logger.debug( "No BOM GAVS to pass into REST client." );
        }
    }

    private void populateRestParam( final ArrayList<ProjectVersionRef> restParam, final String log, final List<ProjectVersionRef> bomMgmt )
    {
        asStream( bomMgmt ).filter
                        ( b -> !Version.hasBuildNumber( b.getVersionString() ) &&
                                        b.getVersionString().contains( PropertiesUtils.getSuffix( session ) ) )
                           .forEach( bom -> {
                               // Create the dummy PVR to send to DA (which requires a numeric suffix).
                               ProjectVersionRef newBom = new SimpleProjectVersionRef( bom.asProjectRef(), bom.getVersionString() + "-0" );
                               logger.debug( "Adding {} BOM {} into REST call.", log, newBom );
                               restParam.add( newBom );
                           } );

    }

    private void updateBOM ( final ListIterator<ProjectVersionRef> iterator, final Map<ProjectVersionRef, String> restResult)
    {
        while ( iterator.hasNext() )
        {
            ProjectVersionRef pvr = iterator.next();
            // As before, only process the BOMs if they are of the format <rebuild suffix> without a numeric portion.
            if ( !Version.hasBuildNumber( pvr.getVersionString() ) && pvr.getVersionString()
                                                                         .contains( PropertiesUtils.getSuffix( session ) ) )
            {
                // Create the dummy PVR to compare with results to...
                ProjectVersionRef newBom = new SimpleProjectVersionRef( pvr.asProjectRef(), pvr.getVersionString() + "-0" );
                if ( restResult.containsKey( newBom ) )
                {
                    ProjectVersionRef replacementBOM = new SimpleProjectVersionRef( pvr.asProjectRef(), restResult.get( newBom ) );
                    logger.debug( "Replacing BOM value of {} with {}.", pvr, replacementBOM );
                    iterator.remove();
                    iterator.add( replacementBOM );
                }
            }
        }
    }

    private static Stream<ProjectVersionRef> asStream ( final Collection <ProjectVersionRef> collection)
    {
        return ( collection == null ? Stream.empty() : collection.stream() );
    }
}
