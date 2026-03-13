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
package org.jboss.pnc.mavenmanipulator.common.util;

import static org.jboss.pnc.mavenmanipulator.common.util.JSONUtils.ARTIFACT_ID;
import static org.jboss.pnc.mavenmanipulator.common.util.JSONUtils.GROUP_ID;
import static org.jboss.pnc.mavenmanipulator.common.util.JSONUtils.VERSION;

import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectVersionRef;
import org.goots.hiderdoclet.doclet.JavadocExclude;
import org.jboss.da.model.rest.GAV;
import org.jboss.pnc.mavenmanipulator.common.json.DependencyAnalyserResult;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.JsonNodeType;

import kong.unirest.jackson.JacksonObjectMapper;

/**
 * ObjectMapper for PME JSON (REST lookup results, etc.) that registers custom deserializers/serializers
 * for {@link ProjectVersionRef} and {@link DependencyAnalyserResult}.
 */
@JavadocExclude
public class PMEObjectMapper extends JacksonObjectMapper {
    private static final String BEST_MATCH_VERSION = "bestMatchVersion";
    private static final String LATEST_VERSION = "latestVersion";

    public PMEObjectMapper(ObjectMapper mapper) {
        super(mapper);

        mapper.configure(JsonGenerator.Feature.IGNORE_UNKNOWN, true);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        SimpleModule module = new SimpleModule();
        module.addDeserializer(ProjectVersionRef.class, new JSONUtils.ProjectVersionRefDeserializer());
        module.addSerializer(ProjectVersionRef.class, new JSONUtils.ProjectVersionRefSerializer());
        module.addDeserializer(DependencyAnalyserResult.class, new MavenResultDeserializer());
        mapper.registerModule(module);
    }

    @JavadocExclude
    public static class MavenResultDeserializer extends JsonDeserializer<DependencyAnalyserResult> {
        @Override
        public DependencyAnalyserResult deserialize(JsonParser p, DeserializationContext ctxt)
                throws java.io.IOException {
            JsonNode node = p.getCodec().readTree(p);
            final String groupId = node.get(GROUP_ID).asText();
            final String artifactId = node.get(ARTIFACT_ID).asText();
            final String version = node.get(VERSION).asText();

            DependencyAnalyserResult result = new DependencyAnalyserResult();
            result.setGav(new GAV(groupId, artifactId, version));

            if (node.has(BEST_MATCH_VERSION) && !node.get(BEST_MATCH_VERSION).getNodeType().equals(JsonNodeType.NULL)) {
                result.setBestMatchVersion(node.get(BEST_MATCH_VERSION).asText());
            }
            if (node.has(LATEST_VERSION) && !node.get(LATEST_VERSION).getNodeType().equals(JsonNodeType.NULL)) {
                result.setLatestVersion(node.get(LATEST_VERSION).asText());
            }

            result.setProjectVersionRef(new SimpleProjectVersionRef(groupId, artifactId, version));

            return result;
        }
    }
}
