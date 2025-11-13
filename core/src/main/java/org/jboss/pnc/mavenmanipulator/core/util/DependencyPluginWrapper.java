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
package org.jboss.pnc.mavenmanipulator.core.util;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.InputLocationTracker;
import org.apache.maven.model.Plugin;
import org.jboss.pnc.mavenmanipulator.common.ManipulationException;

/**
 * Simple wrapper to allow a Plugin or Dependency to be handled within the same way.
 */
public class DependencyPluginWrapper {
    private Plugin plugin = null;

    private Dependency dependency = null;

    private PluginReference pluginReference = null;

    public DependencyPluginWrapper(InputLocationTracker o) throws ManipulationException {
        if (o instanceof Dependency) {
            dependency = (Dependency) o;
        } else if (o instanceof Plugin) {
            plugin = (Plugin) o;
        } else if (o instanceof PluginReference) {
            pluginReference = (PluginReference) o;
        } else {
            throw new ManipulationException("Unknown type for wrapper {}", o);
        }
    }

    public String getVersion() {
        if (dependency != null) {
            return dependency.getVersion();
        } else if (plugin != null) {
            return plugin.getVersion();
        } else {
            return pluginReference.getVersion();
        }
    }

    public void setVersion(String target)
            throws ManipulationException {
        if (dependency != null) {
            dependency.setVersion(target);
        } else if (plugin != null) {
            plugin.setVersion(target);
        } else {
            pluginReference.setVersion(target);
        }
    }

    public void addExclusion(Exclusion e) throws ManipulationException {
        if (dependency == null) {
            throw new ManipulationException("Type is not a dependency {}", e);
        }
        dependency.addExclusion(e);
    }
}
