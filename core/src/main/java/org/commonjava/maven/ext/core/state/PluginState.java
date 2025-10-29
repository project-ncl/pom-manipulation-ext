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
package org.commonjava.maven.ext.core.state;

import static org.commonjava.maven.ext.core.util.PropertiesUtils.getPropertiesByPrefix;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.model.Plugin;
import org.commonjava.atlas.maven.ident.ref.ArtifactRef;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.annotation.ConfigValue;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.core.impl.PluginManipulator;
import org.commonjava.maven.ext.core.util.IdUtils;

import lombok.Getter;

/**
 * Captures configuration relating to plugin alignment from the POMs. Used by {@link PluginManipulator}.
 */
public class PluginState
        implements State {
    /**
     * Defines how dependencies are located.
     */
    @ConfigValue(docIndex = "plugin-manip.html#plugin-source")
    private static final String PLUGIN_SOURCE = "pluginSource";

    /**
     * Merging precedence for dependency sources:
     * 
     * <pre>
     * <code>BOM</code> Solely Remote POM i.e. BOM.
     * <code>REST</code> Solely restURL.
     * <code>RESTBOM</code> Merges the information but takes the rest as precedence.
     * <code>BOMREST</code> Merges the information but takes the bom as precedence.
     * </pre>
     * 
     * Configured by the property <code>-DpluginSource=[REST|BOM|RESTBOM|BOMREST]</code>
     */
    public enum PluginPrecedence {
        REST,
        BOM,
        RESTBOM,
        BOMREST,
        NONE
    }

    /**
     * The name of the property which contains the GAV of the remote pom from which to retrieve plugin management
     * information.
     * 
     * <pre>
     * <code>-DpluginManagement:org.foo:bar-plugin-mgmt:1.0</code>
     * </pre>
     */
    @ConfigValue(docIndex = "plugin-manip.html#basic-plugin-alignment")
    private static final String PLUGIN_MANAGEMENT_POM_PROPERTY = "pluginManagement";

    @ConfigValue(docIndex = "plugin-manip.html#basic-plugin-alignment")
    private static final String PLUGIN_MANAGEMENT_PRECEDENCE = "pluginManagementPrecedence";

    @ConfigValue(docIndex = "plugin-manip.html#plugin-override")
    private static final String PLUGIN_OVERRIDE_PREFIX = "pluginOverride.";

    /**
     * Two possible methods currently supported configuration merging precedence:
     * 
     * <pre>
     * <code>REMOTE</code> (default)
     * <code>LOCAL</code>
     * </pre>
     * 
     * Configured by the property <code>-DpluginManagementPrecedence=[REMOTE|LOCAL]</code>
     */
    public enum Precedence {
        REMOTE,
        LOCAL
    }

    @Getter
    private List<ProjectVersionRef> remotePluginMgmt;

    @Getter
    private Precedence configPrecedence;

    @Getter
    private final Set<Plugin> remoteRESTplugins = new HashSet<>();

    @Getter
    private PluginPrecedence precedence;

    @Getter
    private Map<String, String> pluginOverride;

    public PluginState(final Properties userProps) throws ManipulationException {
        initialise(userProps);
    }

    public void initialise(Properties userProps) throws ManipulationException {
        remotePluginMgmt = IdUtils.parseGAVs(userProps.getProperty(PLUGIN_MANAGEMENT_POM_PROPERTY));
        pluginOverride = getPropertiesByPrefix(userProps, PLUGIN_OVERRIDE_PREFIX);
        switch (Precedence.valueOf(
                userProps.getProperty(
                        PLUGIN_MANAGEMENT_PRECEDENCE,
                        Precedence.REMOTE.toString()).toUpperCase())) {
            case LOCAL: {
                configPrecedence = Precedence.LOCAL;
                break;
            }
            case REMOTE:
            default: {
                configPrecedence = Precedence.REMOTE;
                break;
            }
        }
        // While pluginState can have a separate precedence to dependencyState by default it takes the same. This
        // is a slight shortcut to avoid duplicate configuring while still allowing flexibility.
        switch (PluginPrecedence.valueOf(
                (userProps.getProperty(
                        PLUGIN_SOURCE,
                        userProps.getProperty(
                                DependencyState.DEPENDENCY_SOURCE,
                                DependencyState.DependencyPrecedence.BOM.toString())))
                        .toUpperCase())) {
            case REST: {
                precedence = PluginPrecedence.REST;
                break;
            }
            case BOM: {
                precedence = PluginPrecedence.BOM;
                break;
            }
            case RESTBOM: {
                precedence = PluginPrecedence.RESTBOM;
                break;
            }
            case BOMREST: {
                precedence = PluginPrecedence.BOMREST;
                break;
            }
            case NONE: {
                precedence = PluginPrecedence.NONE;
                break;
            }
            default: {
                throw new ManipulationException(
                        "Unknown value {} for {}",
                        userProps.getProperty(PLUGIN_SOURCE),
                        PLUGIN_SOURCE);
            }
        }
    }

    /**
     * Enabled ONLY if pluginManagement is provided in the user properties / CLI -D options.
     *
     * @see org.commonjava.maven.ext.core.state.State#isEnabled()
     */
    @Override
    public boolean isEnabled() {
        return (!(precedence == PluginPrecedence.NONE)) &&
                (remotePluginMgmt != null && !remotePluginMgmt.isEmpty()) ||
                (!remoteRESTplugins.isEmpty()) ||
                !pluginOverride.isEmpty();
    }

    public void setRemoteRESTOverrides(Map<ArtifactRef, String> overrides) {
        for (final Entry<ArtifactRef, String> entry : overrides.entrySet()) {
            final ArtifactRef a = entry.getKey();
            final Plugin p = new Plugin();
            p.setGroupId(a.getGroupId());
            p.setArtifactId(a.getArtifactId());
            p.setVersion(entry.getValue());
            remoteRESTplugins.add(p);
        }
    }

    public Set<Plugin> getRemoteRESTOverrides() {
        return remoteRESTplugins;
    }
}
