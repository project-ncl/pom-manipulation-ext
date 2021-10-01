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

import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.model.Project;
import org.commonjava.maven.ext.core.ManipulationSession;
import org.commonjava.maven.ext.core.groovy.InvocationStage;
import org.commonjava.maven.ext.core.state.GroovyState;
import org.commonjava.maven.ext.io.FileIO;
import org.commonjava.maven.ext.io.ModelIO;
import org.commonjava.maven.ext.io.PomIO;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * The preparse groovy manipulator runs before any projects are loaded. The configuration is stored in a
 * {@link GroovyState} instance, which is in turn stored in the {@link ManipulationSession}.
 */
@Named( "groovy-injection-preparse" )
@Singleton
public class PreparseGroovyManipulator
    extends BaseGroovyManipulator
{
    @Inject
    public PreparseGroovyManipulator( ModelIO modelIO, FileIO fileIO, PomIO pomIO )
    {
        super( modelIO, fileIO, pomIO );
    }

    /**
     * Apply the groovy script changes.
     *
     * @param session the manipulation session
     * @return an empty set since no projects are modified
     * @throws ManipulationException if an error occurs
     */
    public Set<Project> applyChanges( final ManipulationSession session )
        throws ManipulationException
    {
        this.session = session;
        final GroovyState state = new GroovyState( session.getUserProperties() );
        session.setState( state );

        if ( !session.isEnabled() || !state.isEnabled() )
        {
            logger.debug("{}: Nothing to do!", getClass().getSimpleName() );
            return Collections.emptySet();
        }

        final List<File> groovyScripts = parseGroovyScripts( state.getGroovyScripts() );

        for ( final File groovyScript : groovyScripts )
        {
            applyGroovyScript( Collections.emptyList(), null, groovyScript );
        }

        return Collections.emptySet();
    }


    public int getExecutionIndex()
    {
        return InvocationStage.PREPARSE.getStageValue();
    }
}
