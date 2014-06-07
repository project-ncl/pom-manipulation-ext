/*******************************************************************************
 * Copyright (c) 2014 Red Hat, Inc..
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.commonjava.maven.ext.manip.impl;

import static org.commonjava.maven.ext.manip.util.IdUtils.ga;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.model.Model;
import org.apache.maven.model.Profile;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.commonjava.maven.ext.manip.ManipulationException;
import org.commonjava.maven.ext.manip.io.ModelIO;
import org.commonjava.maven.ext.manip.model.Project;
import org.commonjava.maven.ext.manip.state.ManipulationSession;
import org.commonjava.maven.ext.manip.state.ProfileInjectionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link Manipulator} implementation that can resolve a remote pom file and inject the remote pom's
 * profile(s) into the current project's top level pom file. Configuration is stored in a
 * {@link ProfileInjectionState} instance, which is in turn stored in the {@link ManipulationSession}.
 */
@Component( role = Manipulator.class, hint = "profile-injection" )
public class ProfileInjectionManipulator
    implements Manipulator
{

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    @Requirement
    protected ModelIO modelBuilder;

    protected ProfileInjectionManipulator()
    {
    }

    public ProfileInjectionManipulator( final ModelIO modelIO )
    {
        modelBuilder = modelIO;
    }

    /**
     * No prescanning required for Profile injection.
     */
    @Override
    public void scan( final List<Project> projects, final ManipulationSession session )
        throws ManipulationException
    {
    }

    /**
     * Initialize the {@link ProfileInjectionState} state holder in the {@link ManipulationSession}. This state holder detects
     * version-change configuration from the Maven user properties (-D properties from the CLI) and makes it available for
     * later invocations of {@link ProfileInjectionManipulator#scan(List, ManipulationSession)} and the apply* methods.
     */
    @Override
    public void init( final ManipulationSession session )
    {
        final Properties userProps = session.getUserProperties();
        session.setState( new ProfileInjectionState( userProps ) );
    }

    /**
     * Apply the profile injecton changes to the top level pom.
     */
    @Override
    public Set<Project> applyChanges( final List<Project> projects, final ManipulationSession session )
        throws ManipulationException
    {
        final ProfileInjectionState state = session.getState( ProfileInjectionState.class );
        if ( !session.isEnabled() || !state.isEnabled() )
        {
            logger.debug( getClass().getSimpleName() + ": Nothing to do!" );
            return Collections.emptySet();
        }

        final Map<String, Model> manipulatedModels = session.getManipulatedModels();
        final Set<Project> changed = new HashSet<Project>();

        final Model remoteModel = modelBuilder.resolveRawModel( state.getRemoteProfileInjectionMgmt() );
        final List<Profile> remoteProfiles = remoteModel.getProfiles();

        for ( final Project project : projects )
        {
            if ( project.isTopPOM() )
            {
                final String ga = ga( project );
                logger.info( getClass().getSimpleName() + " applying changes to: " + ga );
                final Model model = manipulatedModels.get( ga );

                final List<Profile> profiles = model.getProfiles();

                final Iterator<Profile> i = remoteProfiles.iterator();
                while ( i.hasNext() )
                {
                    addProfile( profiles, i.next() );
                }
                changed.add( project );
            }
        }

        return changed;
    }

    /**
     * Add the profile to the list of profiles. If an existing profile has the same
     * id it is removed first.
     *
     * @param profiles
     * @param profile
     */
    private void addProfile( final List<Profile> profiles, final Profile profile )
    {
        final Iterator<Profile> i = profiles.iterator();
        while ( i.hasNext() )
        {
            final Profile p = i.next();

            if ( profile.getId()
                        .equals( p.getId() ) )
            {
                logger.debug( "Removing local profile {} ", p );
                i.remove();
                break;
            }
        }

        logger.debug( "Adding profile {}", profile );
        profiles.add( profile );
    }
}
