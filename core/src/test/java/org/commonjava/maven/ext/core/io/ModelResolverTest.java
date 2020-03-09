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
package org.commonjava.maven.ext.core.io;

import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.core.ManipulationSession;
import org.commonjava.maven.ext.io.ModelIO;
import org.commonjava.maven.ext.io.resolver.GalleyAPIWrapper;
import org.commonjava.maven.ext.io.resolver.GalleyInfrastructure;
import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

@RunWith(BMUnitRunner.class)
public class ModelResolverTest
{
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test(expected = ManipulationException.class)
    @BMRule(name = "retrieve-first-null",
                    targetClass = "ArtifactManagerImpl",
                    targetMethod = "retrieveFirst(List<? extends Location> locations, ArtifactRef ref)",
                    targetLocation = "AT ENTRY",
                    action = "RETURN null"
    )
    public void resolveArtifactTest()
        throws Exception
    {
        final ManipulationSession session = new ManipulationSession();
        final GalleyInfrastructure galleyInfra =
            new GalleyInfrastructure( session, null).init( null, null, temp.newFolder(
                            "cache-dir" ) );
        final GalleyAPIWrapper wrapper = new GalleyAPIWrapper( galleyInfra );
        final ModelIO model = new ModelIO(wrapper);

        model.resolveRawModel( SimpleProjectVersionRef.parse( "org.commonjava:commonjava:5"  ) );
    }
}
