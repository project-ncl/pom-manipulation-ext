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
package org.jboss.pnc.maven_manipulator.core.util;

import java.util.HashSet;
import java.util.Set;

import org.commonjava.atlas.maven.ident.ref.ProjectRef;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Used to hold multiple mapping information when mapping properties to update within the Manipulators.
 * For instance, normally we map:
 * Project -&gt; String(PropertyName) : PropertyMapper
 * And this then allows us to record the new version, the old version and any groupId:artifactId of the
 * various Dependencies and Plugins that attempt to update this property.
 */
@Setter
@Getter
@ToString
public class PropertyMapper {
    private String originalVersion;

    private String newVersion;

    private final Set<ProjectRef> dependencies = new HashSet<>();
}
