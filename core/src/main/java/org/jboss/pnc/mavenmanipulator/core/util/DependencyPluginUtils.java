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

import static org.apache.commons.lang3.StringUtils.countMatches;
import static org.apache.commons.lang3.StringUtils.startsWith;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.maven.model.ConfigurationContainer;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.maven.galley.maven.parse.GalleyMavenXMLException;
import org.jboss.pnc.mavenmanipulator.common.ManipulationException;
import org.jboss.pnc.mavenmanipulator.common.model.Project;
import org.jboss.pnc.mavenmanipulator.core.ManipulationSession;
import org.jboss.pnc.mavenmanipulator.io.resolver.GalleyAPIWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import lombok.experimental.UtilityClass;

/**
 * Commonly used manipulations on Plugins.
 */
@UtilityClass
public final class DependencyPluginUtils {
    private final Logger logger = LoggerFactory.getLogger(DependencyPluginUtils.class);

    /**
     * This handles updating a value (be it from a Dependency or Plugin) e.g. {@code artifactId} to its new value taking
     * into
     * account if there are any properties.
     *
     * @param project a references to the Maven Project
     * @param session the ManipulationSession
     * @param originalValue the original property value e.g. the value of {@code artifactId}
     * @param originalRelocation the complete original relocation
     * @param relocation the portion of the relocation that is currently being processed
     * @param c a Consumer function that will update the corresponding Dependency/Plugin if its not a property.
     * @throws ManipulationException if an error occurs.
     */
    public static void updateString(
            Project project,
            ManipulationSession session,
            String originalValue,
            ProjectVersionRef originalRelocation,
            String relocation,
            Consumer<String> c) throws ManipulationException {
        if (startsWith(originalValue, "$")) {
            originalValue = originalValue.substring(2, originalValue.length() - 1);
            if (countMatches(originalValue, "${") > 1) {
                throw new ManipulationException("Relocations with multiple embedded version properties not supported");
            }
            logger.debug(
                    "Updating relocation for {} with property {} to new value {}",
                    originalRelocation,
                    originalValue,
                    relocation);
            PropertiesUtils.updateProperties(session, project, true, originalValue, relocation);
        } else {
            c.accept(relocation);
        }
    }

    private static PluginReference findPluginReference(
            GalleyAPIWrapper galleyWrapper,
            final ConfigurationContainer container,
            final Node parent) {
        if (parent == null) {
            return null;
        }

        final NodeList children = parent.getChildNodes();
        final int length = children.getLength();

        logger.debug("Update child nodes for {} with {} children", parent.getNodeName(), length);

        Node groupIdNode = null;
        Node artifactIdNode = null;
        Node versionNode = null;

        for (int i = 0; i < length; i++) {
            final Node node = children.item(i);

            switch (node.getNodeName()) {
                case "groupId":
                    groupIdNode = node;
                    break;
                case "artifactId":
                    artifactIdNode = node;
                    break;
                case "version":
                    versionNode = node;
                    break;
            }
        }

        if (groupIdNode != null && artifactIdNode != null) {
            PluginReference ref = new PluginReference(
                    galleyWrapper,
                    container,
                    groupIdNode,
                    artifactIdNode,
                    versionNode);

            logger.debug("Found plugin reference: {}", ref);

            return ref;
        }

        return null;
    }

    private static List<PluginReference> findPluginReferences(
            GalleyAPIWrapper galleyWrapper,
            final Entry<ConfigurationContainer, String> entry,
            final NodeList children) {
        final int length = children.getLength();

        logger.debug("Got {} children to update plugin GAVs", length);

        final List<PluginReference> refs = new ArrayList<>(length);

        for (int i = 0; i < length; i++) {
            final Node node = children.item(i);

            logger.debug("Child name is {} and text content is {}", node.getNodeName(), node.getTextContent());

            final PluginReference ref = findPluginReference(galleyWrapper, entry.getKey(), node);

            if (ref != null) {
                refs.add(ref);
            }
        }

        return refs;
    }

    public static List<PluginReference> findPluginReferences(
            GalleyAPIWrapper galleyWrapper,
            final Project project,
            final Map<ProjectVersionRef, Plugin> pluginMap) throws ManipulationException {
        final Collection<Plugin> plugins = pluginMap.values();
        final List<PluginReference> refs = new ArrayList<>();

        for (final Plugin plugin : plugins) {
            final Map<ConfigurationContainer, String> configs = findConfigurations(plugin);

            logger.debug(
                    "Found {} configs for plugin {}:{}:{}",
                    configs.size(),
                    plugin.getGroupId(),
                    plugin.getArtifactId(),
                    plugin.getVersion());

            for (final Entry<ConfigurationContainer, String> entry : configs.entrySet()) {
                try {
                    final Document doc = galleyWrapper.parseXml(entry.getValue());
                    final XPath xPath = XPathFactory.newInstance().newXPath();
                    final PluginReference ref = findPluginReference(
                            galleyWrapper,
                            entry.getKey(),
                            doc.getFirstChild());

                    if (ref != null) {
                        refs.add(ref);
                    }

                    final NodeList artifactItems = (NodeList) xPath
                            .evaluate(".//artifactItems/*", doc, XPathConstants.NODESET);
                    final List<PluginReference> artifactItemRefs = findPluginReferences(
                            galleyWrapper,
                            entry,
                            artifactItems);

                    refs.addAll(artifactItemRefs);
                } catch (final GalleyMavenXMLException e) {
                    throw new ManipulationException(
                            "Unable to parse config for plugin {} in {}",
                            plugin.getId(),
                            project.getKey(),
                            e);
                } catch (final XPathExpressionException e) {
                    throw new ManipulationException(
                            "Invalid XPath expression for plugin {} in {}",
                            plugin.getId(),
                            project.getKey(),
                            e);
                }
            }
        }

        return refs;
    }

    private static Map<ConfigurationContainer, String> findConfigurations(final Plugin plugin) {
        if (plugin == null) {
            return Collections.emptyMap();
        }

        final Map<ConfigurationContainer, String> configs = new LinkedHashMap<>();
        final Object pluginConfiguration = plugin.getConfiguration();

        if (pluginConfiguration != null) {
            configs.put(plugin, pluginConfiguration.toString());
        }

        final List<PluginExecution> executions = plugin.getExecutions();

        if (executions != null) {
            for (PluginExecution execution : executions) {
                final Object executionConfiguration = execution.getConfiguration();

                if (executionConfiguration != null) {
                    configs.put(execution, executionConfiguration.toString());
                }
            }
        }

        return configs;
    }

    public static Xpp3Dom getConfigXml(GalleyAPIWrapper galleyWrapper, final Node node) throws ManipulationException {
        final String config = galleyWrapper.toXML(node.getOwnerDocument(), false).trim();

        try {
            return Xpp3DomBuilder.build(new StringReader(config));
        } catch (final XmlPullParserException | IOException e) {
            throw new ManipulationException(
                    "Failed to re-parse plugin configuration into Xpp3Dom: {}. Config was: {}",
                    e.getMessage(),
                    config,
                    e);
        }
    }
}
