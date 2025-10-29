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

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.codehaus.plexus.interpolation.InterpolationException;
import org.codehaus.plexus.interpolation.PrefixAwareRecursionInterceptor;
import org.codehaus.plexus.interpolation.PrefixedObjectValueSource;
import org.codehaus.plexus.interpolation.PropertiesBasedValueSource;
import org.codehaus.plexus.interpolation.StringSearchInterpolator;
import org.commonjava.maven.ext.common.ManipulationException;

public class PropertyInterpolator {
    private final StringSearchInterpolator interp = new StringSearchInterpolator();
    private final PrefixAwareRecursionInterceptor ri;

    public PropertyInterpolator(Properties props, Object objectValueSource) {
        if (props != null) {
            interp.addValueSource(new PropertiesBasedValueSource(props));
        }

        // According to https://maven.apache.org/guides/introduction/introduction-to-the-pom.html
        // the prefix project and the deprecated prefix pom are possible.
        final List<String> prefixes = Arrays.asList("pom", "project");

        ri = new PrefixAwareRecursionInterceptor(prefixes, true);
        interp.addValueSource(new PrefixedObjectValueSource(prefixes, objectValueSource, true));
    }

    public String interp(String value) throws ManipulationException {
        try {
            return interp.interpolate(value, ri);
        } catch (final InterpolationException e) {
            throw new ManipulationException("Failed to interpolate: {}. Reason: {}", value, e.getMessage(), e);
        }
    }
}
