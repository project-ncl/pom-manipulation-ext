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
package org.jboss.pnc.mavenmanipulator.core.impl;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.jboss.pnc.mavenmanipulator.core.util.IdUtils.ga;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.model.Build;
import org.apache.maven.model.BuildBase;
import org.apache.maven.model.ConfigurationContainer;
import org.apache.maven.model.Model;
import org.apache.maven.model.ModelBase;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.Profile;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.commonjava.maven.galley.maven.parse.GalleyMavenXMLException;
import org.jboss.pnc.mavenmanipulator.common.ManipulationException;
import org.jboss.pnc.mavenmanipulator.common.model.Project;
import org.jboss.pnc.mavenmanipulator.common.util.ProfileUtils;
import org.jboss.pnc.mavenmanipulator.core.ManipulationSession;
import org.jboss.pnc.mavenmanipulator.core.state.DistributionEnforcingState;
import org.jboss.pnc.mavenmanipulator.core.state.EnforcingMode;
import org.jboss.pnc.mavenmanipulator.io.resolver.GalleyAPIWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * {@link Manipulator} implementation that looks for the deploy- and install-plugin &lt;skip/&gt; options, and enforces
 * one of a couple scenarios:
 * <ul>
 * <li><code>-Denforce-skip=(on|true)</code> forces them to be set to <code>true</code> (install/deploy will NOT
 * happen)</li>
 * <li><code>-Denforce-skip=(off|false)</code> forces them to be set to <code>false</code> (install/deploy will
 * happen)</li>
 * <li><code>-Denforce-skip=detect</code> forces the deploy- and install-plugin's skip option to be aligned with that of
 * the first detected install
 * plugin</li>
 * <li><code>-Denforce-skip=none</code> disables enforcement.</li>
 * </ul>
 *
 * <b>NOTE:</b> When using the <code>detect</code> mode, only the install-plugin configurations in the main pom
 * (<b>not</b> those in profiles) will
 * be considered for detection. Of these, only parameters in the plugin-wide configuration OR the
 * <code>default-install</code> execution
 * configuration will be considered. If no matching skip-flag configuration is detected, the default mode of
 * <code>on</code> will be used.
 * <p>
 * Likewise, it's possible to set the enforcement mode DIFFERENTLY for a single project, using:
 * </p>
 *
 * <pre>
 * <code>-DenforceSkip.org.group.id:artifact-id=(on|true|off|false|detect|none)</code>
 * </pre>
 *
 * This is for systems that compare the installed artifacts against the
 * deployed artifacts as part of a post-build validation process.
 *
 * @author jdcasey
 */
@Named("enforce-skip")
@Singleton
public class DistributionEnforcingManipulator
        implements Manipulator {

    public static final String MAVEN_PLUGIN_GROUPID = "org.apache.maven.plugins";

    public static final String MAVEN_INSTALL_ARTIFACTID = "maven-install-plugin";

    public static final String MAVEN_DEPLOY_ARTIFACTID = "maven-deploy-plugin";

    private static final String SKIP_NODE = "skip";

    private static final String DEFAULT_INSTALL_EXEC = "default-install";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private GalleyAPIWrapper galleyWrapper;

    private ManipulationSession session;

    @Inject
    public DistributionEnforcingManipulator(GalleyAPIWrapper galleyWrapper) {
        this.galleyWrapper = galleyWrapper;
    }

    /**
     * Sets the mode to on, off, detect (from install plugin), or none (disabled) based on user properties.
     *
     * @see DistributionEnforcingState
     */
    @Override
    public void init(final ManipulationSession session) {
        this.session = session;
        session.setState(new DistributionEnforcingState(session.getUserProperties()));
    }

    /**
     * For each project in the current build set, enforce the value of the plugin-wide skip flag and that of the
     * 'default-deploy' execution, if they
     * exist. There are three possible modes for enforcement:
     *
     * <ul>
     * <li><b>on</b> - Ensure install and deploy skip is <b>disabled</b>, and that these functions will happen during
     * the build.</li>
     * <li><b>off</b> - Ensure install and deploy skip is <b>enabled</b>, and that neither of these functions will
     * happen during the build.</li>
     * <li><b>detect</b> - Detect the proper flag value from the install plugin's <code>skip</code> flag (either in the
     * plugin-wide config or the
     * <code>default-install</code> execution, if it's specified in the main POM, not a profile). If not present,
     * disable the skip flag.
     * Enforce consistency with this value install/deploy.</li>
     * </ul>
     *
     * <b>NOTE:</b> It's possible to specify an enforcement mode that's unique to a single project, using a command-line
     * parameter of:
     * <code>-DdistroExclusion.g:a=&lt;mode&gt;</code>.
     *
     * @see DistributionEnforcingState
     * @see EnforcingMode
     */
    @Override
    public Set<Project> applyChanges(final List<Project> projects)
            throws ManipulationException {
        final DistributionEnforcingState state = session.getState(DistributionEnforcingState.class);
        if (state == null || !state.isEnabled()) {
            logger.debug("Distribution skip-flag enforcement is disabled.");
            return Collections.emptySet();
        }

        final Map<String, String> excluded = state.getExcludedProjects();
        final Set<Project> changed = new HashSet<>();

        for (final Project project : projects) {
            final String ga = ga(project);

            EnforcingMode mode = state.getEnforcingMode();

            final String override = excluded.get(ga);
            if (override != null) {
                mode = EnforcingMode.getMode(override);
            }

            if (mode == EnforcingMode.none) {
                logger.debug("Install/Deploy skip-flag enforcement is disabled for: {}.", ga);
                continue;
            }

            logger.debug("Applying skip-flag enforcement mode of: {} to: {}", mode, ga);

            final Model model = project.getModel();

            // this is 3-value logic, where skip == on == true, don't-skip == off == false, and (detect from install) == detect == null
            Boolean baseSkipSetting = mode.defaultModificationValue();

            baseSkipSetting = enforceSkipFlag(model, baseSkipSetting, project, changed, true);

            for (final Profile profile : ProfileUtils.getProfiles(session, model)) {
                enforceSkipFlag(profile, baseSkipSetting, project, changed, false);
            }

            if (baseSkipSetting == Boolean.FALSE && model.getProperties().containsKey("maven.deploy.skip")) {
                model.getProperties().setProperty("maven.deploy.skip", "false");
            }
        }

        return changed;
    }

    /**
     * For every mention of a <code>skip</code> parameter in either the install or deploy plugins, enforce a particular
     * value that's passed in. If the
     * passed-in value is <code>null</code> AND the detectFlagValue parameter is true, then look for an install-plugin
     * configuration (in either the
     * plugin-wide config or that of the default-install execution ONLY) that contains the <code>skip</code> flag, and
     * use that as the enforced value.
     *
     * If detection is enabled and no install-plugin is found, set the value to false (don't skip install or deploy).
     *
     * @return the detected value, if detection is enabled.
     */
    private Boolean enforceSkipFlag(
            final ModelBase base,
            Boolean baseSkipSetting,
            final Project project,
            final Set<Project> changed,
            final boolean detectFlagValue)
            throws ManipulationException {
        // search for install/skip config option, use the first one found...
        Boolean skipSetting = baseSkipSetting;

        List<SkipReference> skipRefs = findSkipRefs(base, MAVEN_INSTALL_ARTIFACTID, project);

        if (!skipRefs.isEmpty()) {
            if (detectFlagValue && skipSetting == null) {
                // we need to set the local value AND the global value.
                final SkipReference ref = skipRefs.get(0);
                final ConfigurationContainer container = ref.container;
                if (!(container instanceof PluginExecution)
                        || ((PluginExecution) container).getId()
                                .equals(DEFAULT_INSTALL_EXEC)) {
                    String textVal = ref.getNode()
                            .getTextContent();

                    if (isNotEmpty(textVal)) {
                        textVal = textVal.trim();
                        skipSetting = Boolean.parseBoolean(textVal);
                    }
                }
            }
        } else if (detectFlagValue && skipSetting == null) {
            skipSetting = false;
        }

        if (skipSetting == null) {
            logger.warn("No setting to enforce for skip-flag! Aborting enforcement...");
            return null;
        }

        if (!skipRefs.isEmpty()) {
            for (final SkipReference ref : skipRefs) {
                setFlag(ref, skipSetting, project, changed);
            }
        }

        skipRefs = findSkipRefs(base, MAVEN_DEPLOY_ARTIFACTID, project);
        if (!skipRefs.isEmpty()) {
            for (final SkipReference ref : skipRefs) {
                setFlag(ref, skipSetting, project, changed);
            }
        }

        return skipSetting;
    }

    private void setFlag(
            final SkipReference ref,
            final Boolean skipSetting,
            final Project project,
            final Set<Project> changed)
            throws ManipulationException {
        final String old = ref.getNode()
                .getTextContent()
                .trim();
        final String nxt = Boolean.toString(skipSetting);
        ref.getNode()
                .setTextContent(nxt);

        ref.getContainer()
                .setConfiguration(getConfigXml(ref.getNode()));

        if (!old.equals(nxt)) {
            changed.add(project);
        }
    }

    private Xpp3Dom getConfigXml(final Node node)
            throws ManipulationException {
        final String config = galleyWrapper.toXML(node.getOwnerDocument(), false)
                .trim();

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

    /**
     * Go through the plugin / plugin-execution configurations and find references to the <code>skip</code> parameter
     * for the given Maven plugin
     * (specified by artifactId), both in managed and concrete plugin declarations (where available).
     */
    private List<SkipReference> findSkipRefs(final ModelBase base, final String pluginArtifactId, final Project project)
            throws ManipulationException {
        final String key = ga(MAVEN_PLUGIN_GROUPID, pluginArtifactId);

        Map<String, Plugin> pluginMap = getManagedPluginMap(base);
        Plugin plugin = pluginMap.get(key);
        final List<SkipReference> result = new ArrayList<>(findSkipRefs(plugin, project));

        pluginMap = getPluginMap(base);
        plugin = pluginMap.get(key);
        result.addAll(findSkipRefs(plugin, project));

        return result;
    }

    /**
     * Go through the plugin / plugin-execution configurations and find references to the <code>skip</code> parameter
     * for the given Maven plugin
     * instance.
     */
    private List<SkipReference> findSkipRefs(final Plugin plugin, final Project project)
            throws ManipulationException {
        if (plugin == null) {
            return Collections.emptyList();
        }

        final Map<ConfigurationContainer, String> configs = new LinkedHashMap<>();
        Object configuration = plugin.getConfiguration();
        if (configuration != null) {
            configs.put(plugin, configuration.toString());
        }

        final List<PluginExecution> executions = plugin.getExecutions();
        if (executions != null) {
            for (final PluginExecution execution : executions) {
                configuration = execution.getConfiguration();
                if (configuration != null) {
                    configs.put(execution, configuration.toString());
                }
            }
        }

        final List<SkipReference> result = new ArrayList<>();
        for (final Map.Entry<ConfigurationContainer, String> entry : configs.entrySet()) {
            try {
                final Document doc = galleyWrapper.parseXml(entry.getValue());
                final NodeList children = doc.getDocumentElement()
                        .getChildNodes();
                if (children != null) {
                    for (int i = 0; i < children.getLength(); i++) {
                        final Node n = children.item(i);
                        if (n.getNodeName()
                                .equals(SKIP_NODE)) {
                            result.add(new SkipReference(entry.getKey(), n));
                        }
                    }
                }
            } catch (final GalleyMavenXMLException e) {
                throw new ManipulationException(
                        "Unable to parse config for plugin {} in {}",
                        plugin.getId(),
                        project.getKey(),
                        e);
            }
        }

        return result;
    }

    /**
     * Store the tuple {container, node} where container is the plugin or plugin execution and node is the skip
     * configuration parameter.
     * This allows modification of the Model or extraction of the flag value (if we're trying to detect the install
     * plugin's skip flag state).
     */
    private static final class SkipReference {
        private final ConfigurationContainer container;

        private final Node node;

        SkipReference(final ConfigurationContainer container, final Node node) {
            this.container = container;
            this.node = node;
        }

        public ConfigurationContainer getContainer() {
            return container;
        }

        Node getNode() {
            return node;
        }

    }

    @Override
    public int getExecutionIndex() {
        return 75;
    }

    private Map<String, Plugin> getPluginMap(final ModelBase base) {
        final BuildBase build;
        if (base instanceof Model) {
            build = ((Model) base).getBuild();
        } else {
            build = ((Profile) base).getBuild();
        }

        if (build == null) {
            return Collections.emptyMap();
        }

        final Map<String, Plugin> result = build.getPluginsAsMap();
        if (result == null) {
            return Collections.emptyMap();
        }

        return result;
    }

    private Map<String, Plugin> getManagedPluginMap(final ModelBase base) {
        if (base instanceof Model) {
            final Build build = ((Model) base).getBuild();
            if (build == null) {
                return Collections.emptyMap();
            }

            final PluginManagement pm = build.getPluginManagement();
            if (pm == null) {
                return Collections.emptyMap();
            }

            final Map<String, Plugin> result = pm.getPluginsAsMap();
            if (result == null) {
                return Collections.emptyMap();
            }

            return result;
        }

        return Collections.emptyMap();
    }
}
