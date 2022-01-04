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
package org.commonjava.maven.ext.core;

import org.apache.commons.io.FileUtils;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.core.fixture.PlexusTestRunner;
import org.commonjava.maven.ext.core.fixture.TestUtils;
import org.commonjava.maven.ext.core.impl.Manipulator;
import org.commonjava.maven.ext.core.state.CommonState;
import org.commonjava.maven.ext.core.state.DependencyState;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith( PlexusTestRunner.class )
@Named
public class ManipulationManagerTest
{
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Rule
    public final SystemOutRule systemRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @SuppressWarnings( "unused" )
    @Inject
    private Map<String, Manipulator> manipulators;

    @Test
    public void testListManipulators()
    {
        assertNotNull( manipulators );

        for ( final Map.Entry<String, Manipulator> entry : manipulators.entrySet() )
        {
            assertTrue (entry.getValue().getExecutionIndex() > 0 && entry.getValue().getExecutionIndex() < 100);
        }
    }

    @Test
    public void testSessionStartupMessage()
    {
        new ManipulationSession();
        assertTrue( systemRule.getLog().contains( "Running Maven Manipulation Extension (PME)" ) );
    }

    @Test
    public void testDepOverrideAndStrictPropertyValidation()
                    throws IOException, ManipulationException
    {
        final File projectroot = folder.newFile();
        final File resource = TestUtils.resolveFileResource( "", "pom-variables.xml" );
        FileUtils.copyFile( resource, projectroot );
        Properties p = new Properties();
        p.put( DependencyState.DEPENDENCY_OVERRIDE_PREFIX + ".com.fasterxml.jackson.dataformat:*@*", "" );
        p.put( CommonState.DEPENDENCY_PROPERTY_VALIDATION, "true" );

        CommonState commonState = TestUtils.createSessionAndManager( p, projectroot ).getSession().getState( CommonState.class );

        assertTrue( systemRule.getLog().contains( "Disabling strictPropertyValidation as dependencyOverrides are enabled" ) );
        assertEquals( 0, (int) commonState.getStrictDependencyPluginPropertyValidation() );
    }
}
