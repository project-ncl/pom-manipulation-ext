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
package org.commonjava.maven.ext.common.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import kong.unirest.jackson.JacksonObjectMapper;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef;
import org.commonjava.maven.ext.common.json.ExtendedMavenLookupResult;
import org.commonjava.maven.ext.common.json.PME;
import org.goots.hiderdoclet.doclet.JavadocExclude;
import org.jboss.da.lookup.model.MavenLookupResult;
import org.jboss.da.model.rest.GAV;

import java.io.File;
import java.io.IOException;

public class JSONUtils
{
    private static final String GROUP_ID = "groupId";
    private static final String ARTIFACT_ID = "artifactId";
    private static final String VERSION = "version";
    private static final String BEST_MATCH = "bestMatchVersion";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static
    {
        MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
        MAPPER.configure( SerializationFeature.FAIL_ON_EMPTY_BEANS, false );
        MAPPER.setSerializationInclusion( JsonInclude.Include.NON_NULL );
        MAPPER.setSerializationInclusion( JsonInclude.Include.NON_EMPTY );
    }

    /**
     * Converts the POJO to a JSON document.
     *
     * @param jsonReport The JSON POJO to convert to a string.
     * @return A string with the converted JSON.
     * @throws IOException if an error occurs.
     */
    // Public API.
    public static String jsonToString( Object jsonReport )
                    throws IOException
    {
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString( jsonReport );
    }

    /**
     * Converts JSON document to POJO form.
     *
     * @param jsonFile the file to read
     * @return PME ; the root of the JSON hierarchy.
     * @throws IOException if an error occurs
     */
    @SuppressWarnings("WeakerAccess") // Public API.
    public static PME fileToJSON( File jsonFile ) throws IOException
    {
        return MAPPER.readValue( jsonFile, PME.class );
    }


    public static class ProjectVersionRefDeserializer extends JsonDeserializer<ProjectVersionRef>
    {
        @Override
        public ProjectVersionRef deserialize( JsonParser p, DeserializationContext ctxt)
                throws IOException
        {
            JsonNode node = p.getCodec().readTree( p);
            final String groupId = node.get(GROUP_ID).asText();
            final String artifactId = node.get(ARTIFACT_ID).asText();
            final String version = node.get(VERSION).asText();

            return new SimpleProjectVersionRef ( groupId, artifactId, version);
        }
    }

    public static class ProjectVersionRefSerializer extends JsonSerializer<ProjectVersionRef>
    {
        @Override
        public void serialize( ProjectVersionRef value, JsonGenerator gen, SerializerProvider serializers) throws IOException
        {
            gen.writeStartObject();
            gen.writeStringField(GROUP_ID, value.getGroupId());
            gen.writeStringField(ARTIFACT_ID, value.getArtifactId());
            gen.writeStringField(VERSION, value.getVersionString());
            gen.writeEndObject();
        }
    }

    @JavadocExclude
    public static class MavenLookupResultDeserializer extends JsonDeserializer<MavenLookupResult>
    {
        @Override
        public ExtendedMavenLookupResult deserialize( JsonParser p, DeserializationContext ctxt)
                        throws IOException
        {
            JsonNode node = p.getCodec().readTree( p);
            final String groupId = node.get(GROUP_ID).asText();
            final String artifactId = node.get( ARTIFACT_ID ).asText();
            final String version = node.get( VERSION ).asText();

            ExtendedMavenLookupResult result;

            String bestMatch = null;
            if ( node.has( BEST_MATCH ) && !node.get( BEST_MATCH ).getNodeType().equals( JsonNodeType.NULL ) )
            {
                bestMatch = node.get( BEST_MATCH ).asText();
            }

            result = new ExtendedMavenLookupResult( new GAV( groupId, artifactId, version ), bestMatch );
            result.setProjectVersionRef( new SimpleProjectVersionRef( groupId, artifactId, version ) );

            return result;
        }
    }

    @JavadocExclude
    public static class InternalObjectMapper extends JacksonObjectMapper
    {
        public InternalObjectMapper ( ObjectMapper mapper)
        {
            super( mapper );

            mapper.configure( JsonGenerator.Feature.IGNORE_UNKNOWN, true );
            mapper.configure( DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false );
            mapper.setSerializationInclusion( JsonInclude.Include.NON_NULL);

            SimpleModule module = new SimpleModule();
            module.addDeserializer( ProjectVersionRef.class, new ProjectVersionRefDeserializer());
            module.addSerializer(ProjectVersionRef.class, new ProjectVersionRefSerializer());
            module.addDeserializer( MavenLookupResult.class, new MavenLookupResultDeserializer() );
            mapper.registerModule( module );

        }
    }
}
