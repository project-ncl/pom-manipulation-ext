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
package org.jboss.pnc.maven_manipulator.common.json;

/*
 * Created by JacksonGenerator on 23/07/2019.
 */

import java.util.HashMap;
import java.util.Map;

import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.jboss.pnc.maven_manipulator.common.util.JSONUtils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ManagedPluginsItem {
    /**
     * A collection of plugins. Each plugin is rendered as
     *
     * <pre>
     *     original-GAV : {
     *         groupId
     *         artifactId
     *         version
     *     }
     * </pre>
     */
    @JsonProperty("plugins")
    @JsonDeserialize(contentUsing = JSONUtils.ProjectVersionRefDeserializer.class)
    @JsonSerialize(contentUsing = JSONUtils.ProjectVersionRefSerializer.class)
    private Map<String, ProjectVersionRef> plugins = new HashMap<>();
}
