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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.jboss.da.model.rest.GAV;

import lombok.experimental.UtilityClass;

/**
 * Commonly used manipulations from project profiles.
 */
@UtilityClass
public final class GAVUtils {
    public static Set<GAV> generateGAVs(List<ProjectVersionRef> dep) {
        Set<GAV> result = new HashSet<>();

        dep.forEach(d -> result.add(generateGAVs(d)));

        return result;
    }

    public static GAV generateGAVs(ProjectVersionRef dep) {
        return new GAV(dep.getGroupId(), dep.getArtifactId(), dep.getVersionString());
    }
}
