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
package org.commonjava.maven.ext.common.json;

/*
 * Created by JacksonGenerator on 23/07/2019.
 */

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@JsonPropertyOrder({ "executionRoot", "modules" })
public class PME {
    /**
     * Represents the root of the project and is used by Repour to calculate the project GAV change.
     */
    @JsonProperty("executionRoot")
    private GAV gav = new GAV();

    /**
     * A collection of one or more modules containing the changes made.
     */
    @JsonProperty
    private List<ModulesItem> modules = new ArrayList<>();
}