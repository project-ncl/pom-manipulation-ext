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

import org.apache.maven.model.ConfigurationContainer;
import org.apache.maven.model.InputLocation;
import org.apache.maven.model.InputLocationTracker;
import org.jboss.pnc.mavenmanipulator.common.ManipulationException;
import org.jboss.pnc.mavenmanipulator.common.ManipulationUncheckedException;
import org.jboss.pnc.mavenmanipulator.io.resolver.GalleyAPIWrapper;
import org.w3c.dom.Node;

public final class PluginReference implements InputLocationTracker {
    public final ConfigurationContainer container;

    public final Node groupIdNode;

    public final Node artifactIdNode;

    public final Node versionNode;

    private final GalleyAPIWrapper galleyWrapper;

    public PluginReference(
            GalleyAPIWrapper galleyWrapper,
            final ConfigurationContainer container,
            final Node groupIdNode,
            final Node artifactIdNode,
            final Node versionNode) {
        this.container = container;
        this.groupIdNode = groupIdNode;
        this.artifactIdNode = artifactIdNode;
        this.versionNode = versionNode;
        this.galleyWrapper = galleyWrapper;
    }

    @Override
    public String toString() {
        return "PluginReference{" + "container=" + container + nodeToString(groupIdNode) + nodeToString(
                artifactIdNode) + nodeToString(versionNode) + '}';
    }

    private String nodeToString(Node node) {
        return node == null ? "" : ", [" + node.getNodeName() + "=" + node.getTextContent() + "]";
    }

    @Override
    public InputLocation getLocation(Object o) {
        throw new ManipulationUncheckedException("Unused");
    }

    @Override
    public void setLocation(Object o, InputLocation inputLocation) {
        throw new ManipulationUncheckedException("Unused");
    }

    public String getGroupId() {
        return groupIdNode.getTextContent();
    }

    public String getArtifactId() {
        return artifactIdNode.getTextContent();
    }

    public String getVersion() {
        return versionNode == null ? null : versionNode.getTextContent();
    }

    public void setVersion(String version) throws ManipulationException {
        versionNode.setTextContent(version);
        container.setConfiguration(DependencyPluginUtils.getConfigXml(galleyWrapper, groupIdNode));
    }
}
