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

package org.commonjava.maven.ext.core.util;

import org.commonjava.atlas.maven.ident.ref.ProjectRef;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.commonjava.maven.ext.io.rest.handler.StaticResourceHandler;
import org.commonjava.maven.ext.io.rest.rule.MockServer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.TemporaryFolder;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class IdUtilsTest
{
    @Rule
    public final SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final StaticResourceHandler staticFile = new StaticResourceHandler( "src/test/resources/gavs.txt" );

    @Rule
    public MockServer mockServer = new MockServer( staticFile );


    @Test
    public void testParseGAVs()
    {
        List<ProjectVersionRef> result = IdUtils.parseGAVs( mockServer.getUrl() );
        assertNotNull( result );
        assertEquals( 3, result.size() );
    }

    @Test
    public void testParseGAVsWithEmbedded()
    {
        List<ProjectVersionRef> result = IdUtils.parseGAVs( "httpunit:httpunit:1.7," + mockServer.getUrl() );
        assertNotNull( result );
        assertEquals( 4, result.size() );
        assertEquals( "[httpunit:httpunit:1.7, org.foo:bar:1.0, com.company:artifact:2.0, org"
                                      + ".codehaus.plexus:plexus-utils:1.10]", result.toString() );
    }

    @Test
    public void testParseGAs()
    {
        List<ProjectRef> result = IdUtils.parseGAs( mockServer.getUrl() );
        assertNotNull( result );
        assertEquals( 3, result.size() );
    }


    @Test(expected = ManipulationUncheckedException.class )
    public void testParseGAVsWithFailure()
    {
        IdUtils.parseGAVs( mockServer.getUrl() + "/1234");
    }

    @Test(expected = ManipulationUncheckedException.class)
    public void testParseGAsWithFailure()
    {
        IdUtils.parseGAs( "https://github.com/project-ncl/pom-manipulation-ext" );
    }
}
