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

import java.util.List;
import java.util.Properties;

import org.jboss.pnc.mavenmanipulator.common.ManipulationException;
import org.jboss.pnc.mavenmanipulator.common.ManipulationUncheckedException;
import org.jboss.pnc.mavenmanipulator.common.model.Project;
import org.jboss.pnc.mavenmanipulator.common.session.MavenSessionHandler;

import lombok.experimental.UtilityClass;

/**
 * Commonly used manipulations / extractions from project / user (CLI) properties.
 */
@UtilityClass
public final class PropertyResolver {
    /**
     * This recursively checks the supplied value and recursively resolves it if its a property.
     *
     * @param session the manipulation session.
     * @param start the {@link Project} to start resolving from.
     * @param value value to check
     * @return the version string
     * @throws ManipulationException if an error occurs
     */
    public static String resolveInheritedProperties(MavenSessionHandler session, Project start, String value)
            throws ManipulationException {
        return resolveProperties(session, start.getInheritedList(), value);
    }

    private static Properties searchProfiles(MavenSessionHandler session, Project p) {
        final Properties result = new Properties();

        ProfileUtils.getProfiles(session, p.getModel()).forEach(pr -> result.putAll(pr.getProperties()));

        return result;
    }

    /**
     * This is a wrapper around {@link #resolveProperties(MavenSessionHandler, List, String)}. It simply
     * wraps any checked exception inside an unchecked exception.
     *
     * @param session the current session
     * @param projects set of projects
     * @param value value to check
     * @return the version string
     */
    public static String resolvePropertiesUnchecked(MavenSessionHandler session, List<Project> projects, String value) {
        try {
            return resolveProperties(session, projects, value);
        } catch (ManipulationException e) {
            throw new ManipulationUncheckedException(e);
        }
    }

    /**
     * This recursively checks the supplied value and recursively resolves it if its a property.
     *
     * @param session the current session
     * @param projects set of projects
     * @param value value to check
     * @return the version string
     * @throws ManipulationException if an error occurs
     */
    public static String resolveProperties(MavenSessionHandler session, List<Project> projects, String value)
            throws ManipulationException {
        final Properties amalgamated = new Properties();

        // The projects passed in are in a crafted order (determined by Project::getInherited or getReverseInherited)
        // so therefore there is no need to save the execution root.
        for (Project p : projects) {
            amalgamated.putAll(p.getModel().getProperties());
            amalgamated.putAll(searchProfiles(session, p));
        }
        PropertyInterpolator pi = new PropertyInterpolator(amalgamated, projects.get(0));
        return pi.interp(value);
    }
}
