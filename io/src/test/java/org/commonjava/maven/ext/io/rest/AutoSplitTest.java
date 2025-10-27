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
package org.commonjava.maven.ext.io.rest;

import kong.unirest.Unirest;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.io.rest.handler.SpyFailJettyHandler;
import org.commonjava.maven.ext.io.rest.rule.MockServer;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.commonjava.maven.ext.io.rest.Translator.DEFAULT_CONNECTION_TIMEOUT_SEC;
import static org.commonjava.maven.ext.io.rest.Translator.DEFAULT_SOCKET_TIMEOUT_SEC;
import static org.commonjava.maven.ext.io.rest.Translator.RETRY_DURATION_SEC;
import static org.commonjava.maven.ext.io.rest.VersionTranslatorTest.loadALotOfGAVs;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Otavio Piske <opiske@redhat.com>
 */
public class AutoSplitTest
{
    private static List<ProjectVersionRef> aLotOfGavs;

    private final Logger logger = LoggerFactory.getLogger( AutoSplitTest.class );

    private final SpyFailJettyHandler handler = new SpyFailJettyHandler();

    @Rule
    public TestName testName = new TestName();

    @Rule
    public MockServer mockServer = new MockServer( handler );

    @BeforeClass
    public static void startUp() throws IOException
    {
        aLotOfGavs = loadALotOfGAVs();
        assertTrue( aLotOfGavs.size() >= 37 );
    }

    @Before
    public void before()
    {
        logger.info( "Executing test " + testName.getMethodName() );

        handler.setStatusCode( HttpServletResponse.SC_OK );
    }

    @Test
    public void testConnection()
    {
        try
        {
            Unirest.post( mockServer.getUrl() ).asString();
        }
        catch ( Exception e )
        {
            fail( "Failed to connect to server, exception message: " + e.getMessage() );
        }
    }

    private List<List<Map<String, Object>>> translate(int size) {
        final DefaultTranslator versionTranslator = new DefaultTranslator(
                        mockServer.getUrl(), -1, 0, false, "", Collections.emptyMap(),
                        DEFAULT_CONNECTION_TIMEOUT_SEC, DEFAULT_SOCKET_TIMEOUT_SEC, RETRY_DURATION_SEC);

        List<ProjectVersionRef> data = aLotOfGavs.subList( 0, size );
        handler.getRequestData().clear();
        try
        {
            versionTranslator.lookupVersions( data );
        }
        catch ( RestException exception )
        {
            fail();
        }
        return handler.getRequestData();
    }

    private void testTranslateVersionsAutoSplit(int payload, int chunkCount, int chunkSize, int remaining)
    {
        List<List<Map<String, Object>>> requestData = translate(payload);

        logger.debug( requestData.toString() );
        assertEquals( chunkCount, requestData.size() );
        int i = 0;
        for (List<Map<String, Object>> partition : requestData)
        {
            int expected = chunkSize;

            // The last one will contain only 24 for this request size
            if (i == (requestData.size() - 1)) {
                expected = remaining;
            }

            assertEquals( expected, partition.size() );
            i++;
        }
    }


    @Test
    public void testTranslateVersionsAutoSplitLarge()
    {
        testTranslateVersionsAutoSplit(2200, 69, 32, 24);
    }

    @Test
    public void testTranslateVersionsAutoSplitMedium()
    {
        testTranslateVersionsAutoSplit(800, 13, 64, 32);
    }

    @Test
    public void testTranslateVersionsAutoSplitSmall()
    {
        testTranslateVersionsAutoSplit(400, 4, 128, 16);
    }
}
