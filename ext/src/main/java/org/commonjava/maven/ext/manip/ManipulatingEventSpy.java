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
package org.commonjava.maven.ext.manip;

import ch.qos.logback.classic.Level;
import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.eventspy.EventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.ExecutionEvent.Type;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.commonjava.maven.ext.manip.io.ConfigIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Properties;

import static org.apache.commons.lang.StringUtils.isNotEmpty;

/**
 * Implements hooks necessary to apply modificationprojectBs in the Maven bootstrap, before the build starts.
 * @author jdcasey
 */
@Component( role = EventSpy.class, hint = "manipulation" )
public class ManipulatingEventSpy
    extends AbstractEventSpy
{

    private static final String REQUIRE_EXTENSION = "manipulation.required";

    private static final Logger LOGGER = LoggerFactory.getLogger( ManipulatingEventSpy.class );

    @Requirement
    private ManipulationManager manipulationManager;

    @Requirement
    private ManipulationSession session;

    @Requirement
    private ConfigIO configIO;

    @Override
    public void onEvent( final Object event )
        throws Exception
    {
        boolean required = false;

        try
        {
            if ( event instanceof ExecutionEvent )
            {
                final ExecutionEvent ee = (ExecutionEvent) event;

                required = Boolean.parseBoolean( ee.getSession()
                                                   .getRequest()
                                                   .getUserProperties()
                                                   .getProperty( REQUIRE_EXTENSION, "false" ) );

                final ExecutionEvent.Type type = ee.getType();
                if ( type == Type.ProjectDiscoveryStarted )
                {
                    if ( ee.getSession() != null )
                    {
                        if ( ee.getSession().getRequest().getLoggingLevel() == 0 )
                        {
                            final ch.qos.logback.classic.Logger root =
                                            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger( org.slf4j.Logger.ROOT_LOGGER_NAME );
                            root.setLevel( Level.DEBUG );
                        }

                        session.setMavenSession( ee.getSession() );

                        if ( ee.getSession().getRequest().getPom() != null )
                        {
                            Properties config = configIO.parse( ee.getSession().getRequest().getPom().getParentFile() );
                            String value = session.getUserProperties().getProperty( "allowConfigFilePrecedence" );
                            if ( isNotEmpty( value ) && "true".equalsIgnoreCase( value ) )
                            {
                                session.getUserProperties().putAll( config );
                            }
                            else
                            {
                                for ( String key : config.stringPropertyNames() )
                                {
                                    if ( ! session.getUserProperties().containsKey( key ) )
                                    {
                                        session.getUserProperties().setProperty( key, config.getProperty( key ) );
                                    }
                                }
                            }
                        }

                        manipulationManager.init( session );
                    }
                    else
                    {
                        LOGGER.error( "Null session ; unable to continue" );
                        return;
                    }

                    if ( !session.isEnabled() )
                    {
                        LOGGER.info( "Manipulation engine disabled via command-line option" );
                        return;
                    }
                    else if ( ee.getSession().getRequest().getPom() == null )
                    {
                        LOGGER.info( "Manipulation engine disabled. No project found." );
                        return;
                    }
                    else if ( new File( ee.getSession().getRequest().getPom().getParentFile(),
                                        ManipulationManager.MARKER_FILE ).exists() )
                    {
                        LOGGER.info( "Skipping manipulation as previous execution found." );
                        return;
                    }

                    manipulationManager.scanAndApply( session );
                }
            }
        }
        catch ( final ManipulationException e )
        {
            LOGGER.error( "Extension failure", e );
            if ( required )
            {
                throw e;
            }
            else
            {
                session.setError( e );
            }
        }
        // Catch any runtime exceptions and mark them to fail the build as well.
        catch ( final RuntimeException e )
        {
            LOGGER.error( "Extension failure", e );
            if ( required )
            {
                throw e;
            }
            else
            {
                session.setError( new ManipulationException( "Caught runtime exception", e ) );
            }
        }
        finally
        {
            super.onEvent( event );
        }
    }
}
