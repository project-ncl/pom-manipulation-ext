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

import org.commonjava.atlas.maven.ident.ref.ArtifactRef;
import org.commonjava.maven.ext.common.model.Project;
import org.commonjava.maven.ext.core.ManipulationSession;
import org.commonjava.maven.ext.core.fixture.TestUtils;
import org.commonjava.maven.ext.core.impl.RESTCollector;
import org.commonjava.maven.ext.io.PomIO;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class ResolveArtifactTest
{
    private static final String RESOURCE_BASE = "properties/";

    @Test
    public void testResolveArtifacts() throws Exception
    {
        final ManipulationSession session = new ManipulationSession();

        final File projectroot = TestUtils.resolveFileResource( RESOURCE_BASE, "infinispan-bom-8.2.0.Final.pom" );
        PomIO pomIO = new PomIO();
        List<Project> projects = pomIO.parseProject( projectroot );

        Set<ArtifactRef> artifacts = RESTCollector.establishAllDependencies( session, projects, null );
        System.out.println ("### artifact count is " + artifacts.size());
        assertEquals( 67, artifacts.size() );
    }
}
