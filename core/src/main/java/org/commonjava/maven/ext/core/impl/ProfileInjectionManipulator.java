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

import org.apache.maven.model.Profile;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.model.Project;
import org.commonjava.maven.ext.core.ManipulationSession;
import org.commonjava.maven.ext.core.state.ProfileInjectionState;
import org.commonjava.maven.ext.io.ModelIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * {@link Manipulator} implementation that can resolve a remote pom file and inject the remote pom's
 * profile(s) into the current project's top level pom file. Configuration is stored in a
 * {@link ProfileInjectionState} instance, which is in turn stored in the {@link ManipulationSession}.
 */
@Named("profile-injection")
@Singleton
public class ProfileInjectionManipulator
    implements Manipulator
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private final ModelIO modelBuilder;

    private ManipulationSession session;

    @Inject
    public ProfileInjectionManipulator(ModelIO modelBuilder)
    {
        this.modelBuilder = modelBuilder;
    }

    /**
     * Initialize the {@link ProfileInjectionState} state holder in the {@link ManipulationSession}. This state holder detects
     * version-change configuration from the Maven user properties (-D properties from the CLI) and makes it available for
     * later.
     */
    @Override
    public void init( final ManipulationSession session )
    {
        this.session = session;
        session.setState( new ProfileInjectionState( session.getUserProperties() ) );
    }

    /**
     * Apply the profile injection changes to the top level pom.
     */
    @Override
    public Set<Project> applyChanges( final List<Project> projects )
        throws ManipulationException
    {
        final ProfileInjectionState state = session.getState( ProfileInjectionState.class );
        if ( !session.isEnabled() || !state.isEnabled() )
        {
            logger.debug( "{}: Nothing to do!", getClass().getSimpleName() );
            return Collections.emptySet();
        }

        final Set<Project> changed = new HashSet<>();

        for ( ProjectVersionRef p : state.getRemoteProfileInjectionMgmt() )
        {
            final List<Profile> remoteProfiles = modelBuilder.resolveRawModel( p ).getProfiles();

            projects.stream().filter( Project::isInheritanceRoot ).forEach( project -> {
                logger.info( "Applying changes to: {}:{}", project.getGroupId(), project.getArtifactId() );
                project.updateProfiles(remoteProfiles);
                changed.add(project);
            });
        }

        return changed;
    }

    @Override
    public int getExecutionIndex()
    {
        return 5;
    }
}
