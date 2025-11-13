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
package org.jboss.pnc.mavenmanipulator.io;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.startsWith;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.commonjava.atlas.maven.ident.ref.ArtifactRef;
import org.commonjava.atlas.maven.ident.ref.ProjectRef;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectRef;
import org.commonjava.maven.galley.TransferException;
import org.commonjava.maven.galley.maven.GalleyMavenException;
import org.commonjava.maven.galley.maven.model.view.DependencyView;
import org.commonjava.maven.galley.maven.model.view.MavenPomView;
import org.commonjava.maven.galley.maven.model.view.PluginView;
import org.commonjava.maven.galley.model.Transfer;
import org.jboss.pnc.mavenmanipulator.common.ManipulationException;
import org.jboss.pnc.mavenmanipulator.io.resolver.GalleyAPIWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to resolve artifact descriptors (pom files) from a maven repository
 */
@Named
@Singleton
public class ModelIO {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final GalleyAPIWrapper galleyWrapper;

    @Inject
    public ModelIO(GalleyAPIWrapper galleyWrapper) {
        this.galleyWrapper = galleyWrapper;
    }

    /**
     * Read the raw model (equivalent to the pom file on disk) from a given GAV.
     *
     * @param ref the ProjectVersion to read.
     * @return the Maven Model for the GAV
     * @throws ManipulationException if an error occurs.
     */
    public Model resolveRawModel(final ProjectVersionRef ref)
            throws ManipulationException {
        Transfer transfer;
        try {
            transfer = galleyWrapper.resolveArtifact(ref.asPomArtifact());
        } catch (final TransferException e) {
            throw new ManipulationException("Failed to resolve POM ({}) : ", ref, e.getMessage(), e);
        }
        if (transfer == null) {
            throw new ManipulationException("Failed to resolve POM: {}", ref.asPomArtifact());
        }

        try (InputStream in = transfer.openInputStream()) {
            return new MavenXpp3Reader().read(in);
        } catch (final IOException | XmlPullParserException e) {
            throw new ManipulationException("Failed to build model for POM ({}) : ", ref, e.getMessage(), e);
        }
    }

    /**
     * Read the raw file from a given GAVTC (GAV + Type and Classifier). Useful if we need to read
     * a remote file.
     *
     * @param ref the ArtifactRef to read.
     * @return the file for the GAVTC
     * @throws ManipulationException if an error occurs.
     */
    public File resolveRawFile(final ArtifactRef ref)
            throws ManipulationException {
        Transfer transfer;
        try {
            transfer = galleyWrapper.resolveArtifact(ref);
        } catch (final TransferException e) {
            throw new ManipulationException("Failed to resolve POM ({}) : {}", ref, e.getMessage(), e);
        }
        if (transfer == null) {
            throw new ManipulationException("Failed to resolve POM: {}", ref.asPomArtifact());
        }

        return transfer.getDetachedFile();
    }

    public Map<ArtifactRef, String> getRemoteDependencyVersionOverrides(final ProjectVersionRef ref)
            throws ManipulationException {
        logger.debug("Resolving dependency management GAV: {}", ref);

        final Map<ArtifactRef, String> versionOverrides = new LinkedHashMap<>();
        try {
            final MavenPomView pomView = galleyWrapper.readPomView(ref);

            // TODO: active profiles!
            final List<DependencyView> deps = pomView.getAllManagedDependencies();
            if (deps == null || deps.isEmpty()) {
                logger.warn("Attempting to align to a BOM that does not have a dependencyManagement section");
            } else {
                for (final DependencyView dep : deps) {
                    versionOverrides.put(dep.asArtifactRef(), dep.getVersion());
                    logger.debug("Added version override for {}:{}", dep.asProjectRef(), dep.getVersion());
                }
            }
        } catch (final GalleyMavenException e) {
            throw new ManipulationException("Unable to resolve: {}", ref, e);
        }

        return versionOverrides;
    }

    public Map<ProjectRef, String> getRemoteDependencyVersionOverridesByProject(final ProjectVersionRef ref)
            throws ManipulationException {
        Map<ArtifactRef, String> byArtifact = getRemoteDependencyVersionOverrides(ref);
        return byArtifact.entrySet()
                .stream()
                .collect(
                        Collectors.toMap(
                                e -> e.getKey()
                                        .asProjectRef(),
                                Map.Entry::getValue,
                                (key1, key2) -> {
                                    // Avoid problem where a ProjectRef leads to duplicates when the ArtifactRef didn't
                                    return key1;
                                }));
    }

    public Properties getRemotePropertyMappingOverrides(final ProjectVersionRef ref)
            throws ManipulationException {
        logger.debug("Resolving remote property mapping POM: {}", ref);

        final Model m = resolveRawModel(ref);
        final Properties p = m.getProperties();

        logger.debug("Returning override of {}", p);

        return p;
    }

    /**
     * Return remote pluginManagements to override
     *
     * @param ref the remote reference to resolve.
     * @param userProperties a collection of properties to ignore when resolving the remote plugin property expressions.
     * @return a map containing ProjectRef to Plugins
     * @throws ManipulationException if an error occurs
     */
    public Set<Plugin> getRemotePluginManagementVersionOverrides(
            final ProjectVersionRef ref,
            final Properties userProperties)
            throws ManipulationException {
        logger.debug("Resolving remote pluginManagement POM: {}", ref);

        final Set<Plugin> pluginOverrides = new HashSet<>();
        final Map<ProjectRef, ProjectVersionRef> pluginOverridesPomView = new HashMap<>();
        final Model m = resolveRawModel(ref);

        try {
            final MavenPomView pomView = galleyWrapper.readPomView(ref);
            final List<PluginView> deps = pomView.getAllManagedBuildPlugins();
            for (final PluginView p : deps) {
                pluginOverridesPomView.put(p.asProjectRef(), p.asProjectVersionRef());
            }
        } catch (GalleyMavenException e) {
            throw new ManipulationException("Unable to resolve: {}", ref, e);
        }

        logger.debug("Found pluginOverridesResolvedVersions {}", pluginOverridesPomView);

        // The list of pluginOverridesPomView may be larger than those in current model pluginMgtm. Dummy up an extra
        // set of plugins with versions to handle those.
        for (Map.Entry<ProjectRef, ProjectVersionRef> entry : pluginOverridesPomView.entrySet()) {
            Plugin p = new Plugin();
            p.setArtifactId(entry.getKey().getArtifactId());
            p.setGroupId(entry.getKey().getGroupId());
            p.setVersion(entry.getValue().getVersionString());

            pluginOverrides.add(p);
        }

        // TODO: active profiles!
        if (m.getBuild() != null && m.getBuild().getPluginManagement() != null) {
            Iterator<Plugin> plit = null;

            if (m.getBuild().getPluginManagement() != null) {
                logger.debug("Returning override of {}", m.getBuild().getPluginManagement().getPlugins());
                plit = m.getBuild().getPluginManagement().getPlugins().iterator();
            }

            while (plit != null && plit.hasNext()) {
                Plugin p = plit.next();
                ProjectRef pr = new SimpleProjectRef(p.getGroupId(), p.getArtifactId());

                if (isEmpty(p.getVersion()) || startsWith(p.getVersion(), "${")) {
                    // Property reference to something in the remote pom. Resolve and inline it now.
                    String newVersion = resolveProperty(userProperties, m.getProperties(), p.getVersion());

                    if (isEmpty(newVersion) || startsWith(newVersion, "${")) {
                        // Use PomView as that contains a pre-resolved list of plugins.
                        newVersion = pluginOverridesPomView.get(pr).getVersionString();
                    }

                    logger.debug("Replacing plugin override version {} with {}", p.getVersion(), newVersion);
                    p.setVersion(newVersion);
                }

                // Replacing the element with the fully parsed element from the Model.
                pluginOverrides.remove(p);
                pluginOverrides.add(p);

                // If we have a configuration block, as per with plugin versions ensure we
                // resolve any properties.
                if (p.getConfiguration() != null) {
                    processChildren(userProperties, m, (Xpp3Dom) p.getConfiguration());
                }

                if (p.getExecutions() != null) {
                    List<PluginExecution> exes = p.getExecutions();

                    for (PluginExecution pe : exes) {
                        if (pe.getConfiguration() != null) {
                            processChildren(userProperties, m, (Xpp3Dom) pe.getConfiguration());
                        }
                    }
                }

                if (p.getDependencies() != null) {
                    for (Dependency d : p.getDependencies()) {
                        if (startsWith(d.getVersion(), "${")) {
                            final String version = resolveProperty(userProperties, m.getProperties(), d.getVersion());
                            logger.debug("Processing dependency {} and updating with {}", d, version);
                            d.setVersion(version);
                        }
                    }
                }

                if (logger.isDebugEnabled()) {
                    logger.debug(
                            "Added plugin override for {} with configuration {}{} and executions {} and dependencies {}",
                            p.getId(),
                            System.lineSeparator(),
                            p.getConfiguration(),
                            p.getExecutions(),
                            p.getDependencies());
                }
            }
        } else {
            throw new ManipulationException(
                    "Attempting to align to a BOM that does not have a pluginManagement section");
        }
        return pluginOverrides;
    }

    /**
     * Recursively process the DOM elements to inline any property values from the model.
     */
    private void processChildren(Properties userProperties, Model model, Xpp3Dom parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            Xpp3Dom child = parent.getChild(i);

            if (child.getChildCount() > 0) {
                processChildren(userProperties, model, child);
            }
            if (startsWith(child.getValue(), "${")) {
                String replacement = resolveProperty(userProperties, model.getProperties(), child.getValue());

                if (!isEmpty(replacement)) {
                    logger.debug("Replacing child value {} with {}", child.getValue(), replacement);
                    child.setValue(replacement);
                }
            }

        }
    }

    /**
     * Recursively resolve a property value.
     */
    private String resolveProperty(Properties userProperties, Properties p, String key) {
        String result = "";
        String child = (isEmpty(key) ? "" : key.substring(2, key.length() - 1));

        if (p.containsKey(child) && !userProperties.containsKey(child)) {
            result = p.getProperty(child);

            if (startsWith(result, "${")) {
                result = resolveProperty(userProperties, p, result);
            }
        }
        return result;
    }
}
