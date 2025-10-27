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
package org.commonjava.maven.ext.common.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.maven.model.Dependency;
import org.commonjava.atlas.maven.ident.ref.InvalidRefException;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.atlas.maven.ident.ref.SimpleArtifactRef;
import org.commonjava.atlas.maven.ident.ref.TypeAndClassifier;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

/**
 * Wrapper around SimpleArtifactRef to also store the scope of the dependency.
 * <p>
 * This should <b>always</b> be used in preference to {@link SimpleArtifactRef} for consistency even if
 * there is no scope information.
 * </p>
 */
@Getter
@Setter
@EqualsAndHashCode( callSuper = true )
public class SimpleScopedArtifactRef extends SimpleArtifactRef
{
    private final String scope;

    public SimpleScopedArtifactRef( final String group, final String artifact, final String version, final String type,
                                    final String classifier, final String scope )
    {
        super( group, artifact, version, type, classifier );
        this.scope = scope;
    }

    public SimpleScopedArtifactRef( final ProjectVersionRef ref, final TypeAndClassifier tc, final String scope )
    {
        super( ref, tc );
        this.scope = scope;
    }

    public SimpleScopedArtifactRef( final Dependency dependency )
    {
        super( dependency.getGroupId(), dependency.getArtifactId(),
               isEmpty( dependency.getVersion() ) ? "*" : dependency.getVersion(),
               dependency.getType(), dependency.getClassifier());
        this.scope = dependency.getScope();
    }

    public static SimpleScopedArtifactRef parse( final String spec )
    {
        return parse( spec, null );
    }

    public static SimpleScopedArtifactRef parse( final String spec, String scope )
    {
        final String[] parts = spec.split( ":" );

        if ( parts.length < 3 || isEmpty( parts[0] ) || isEmpty( parts[1] ) || isEmpty( parts[2] ) )
        {
            throw new InvalidRefException(
                            "SimpleArtifactRef must contain AT LEAST non-empty groupId, artifactId, AND version. (Given: '"
                                            + spec + "')" );
        }

        final String g = parts[0];
        final String a = parts[1];

        // assume we're actually parsing a GAV into a POM artifact...
        String v = parts[2];
        String t = "pom";
        String c = null;

        if ( parts.length > 3 )
        {
            // oops, it's a type, not a version...see toString() for the specification.
            t = v;
            v = parts[3];

            if ( parts.length > 4 )
            {
                c = parts[4];
            }
        }

        // assume non-optional, because it might not matter if you're parsing a string like this...you'd be more careful if you were reading something
        // that had an optional field, because it's not in the normal GATV[C] spec.
        return new SimpleScopedArtifactRef( g, a, v, t, c, scope );
    }

    @Override
    public String toString()
    {
        if ( isNotEmpty( scope ) )
        {
            return super.toString() + " : scope=" + scope;
        }
        else
        {
            return super.toString();
        }
    }
}
