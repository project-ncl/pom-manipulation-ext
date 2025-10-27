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

import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.project.MavenProject;
import org.commonjava.atlas.maven.ident.ref.ProjectRef;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectVersionRef;
import org.commonjava.maven.ext.common.model.Project;

import java.util.List;

/**
 * Convenience utilities for converting between {@link ProjectVersionRef}, {@link Model}, {@link MavenProject} and GA / GAV strings.
 *
 * @author jdcasey
 */
public final class IdUtils
{
    private IdUtils()
    {
    }

    /**
     * Splits the value on ',', then wraps each value in {@link SimpleProjectVersionRef#parse(String)}. Returns null
     * if the input value is null.
     * @param value a comma separated list of GAV to parse
     * @return a collection of parsed ProjectVersionRef.
     */
    public static List<ProjectVersionRef> parseGAVs( final String value )
    {
        return RefParseUtils.parseRefs( value, SimpleProjectVersionRef::parse );
    }

    /**
     * Splits the value on ',', then wraps each value in {@link SimpleProjectRef#parse(String)}. Returns null if the
     * input value is null.
     * @param value a comma separated list of GA to parse
     * @return a collection of parsed ProjectRef.
     */
    public static List<ProjectRef> parseGAs( final String value )
    {
        return RefParseUtils.parseRefs( value, SimpleProjectRef::parse );
    }

    public static String gav( final Project project )
    {
        return String.format( "%s:%s:%s", project.getGroupId(), project.getArtifactId(), project.getVersion() );
    }

    public static String ga( final MavenProject project )
    {
        return ga( project.getGroupId(), project.getArtifactId() );
    }

    public static String ga( final Project project )
    {
        return ga( project.getGroupId(), project.getArtifactId() );
    }

    public static String ga( final Parent project )
    {
        return ga( project.getGroupId(), project.getArtifactId() );
    }

    public static String ga( final String g, final String a )
    {
        return String.format( "%s:%s", g, a );
    }

    public static String gav( final String g, final String a, final String v )
    {
        return String.format( "%s:%s:%s", g, a, v );
    }
}
