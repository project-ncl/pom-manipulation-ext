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
package org.jboss.pnc.maven_manipulator.io.rest.handler;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.ToString;

@ToString
public class GAVSchema {
    @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
    public List<Map<String, Object>> artifacts;

    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    public String mode;

    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    public Boolean brewPullActive;

    public GAVSchema() {
    }
}
