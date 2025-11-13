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
package org.jboss.pnc.mavenmanipulator.common.model;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.apache.maven.model.Activation;
import org.apache.maven.model.ActivationProperty;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.Profile;
import org.commonjava.atlas.maven.ident.ref.ArtifactRef;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectVersionRef;
import org.commonjava.atlas.maven.ident.util.VersionUtils;
import org.commonjava.maven.galley.maven.internal.defaults.StandardMaven350PluginDefaults;
import org.commonjava.maven.galley.maven.spi.defaults.MavenPluginDefaults;
import org.jboss.pnc.mavenmanipulator.common.ManipulationException;
import org.jboss.pnc.mavenmanipulator.common.session.MavenSessionHandler;
import org.jboss.pnc.mavenmanipulator.common.util.ProfileUtils;
import org.jboss.pnc.mavenmanipulator.common.util.PropertyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides a convenient way of passing around related information about a Maven
 * project without passing multiple parameters. The model in this class
 * represents the model that is being modified by the extension. Also stored is
 * the original POM file related to these models.
 *
 * @author jdcasey
 */
public class Project {
    private static final MavenPluginDefaults PLUGIN_DEFAULTS = new StandardMaven350PluginDefaults();

    private enum PluginResolver {
        NONE, PLUGIN_DEFAULTS, ALL
    }

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Original POM file from which this model information was loaded.
     */
    private final File pom;

    /**
     * Model undergoing modification during execution. This model is what
     * will eventually be written back to disk.
     */
    private final Model model;

    /**
     * Denotes if this Project represents the top level POM of a build.
     */
    private boolean inheritanceRoot;

    /**
     * Denotes if this Project is the execution root.
     */
    private boolean executionRoot;

    private boolean incrementalPME;

    /**
     * Tracking inheritance across the project.
     */
    private Project projectParent;

    public Project(final File pom, final Model model) throws ManipulationException {
        this.pom = pom;
        this.model = model;

        // Validate the model.
        if (model == null) {
            throw new ManipulationException("Invalid null model.");
        } else if (model.getVersion() == null && model.getParent() == null) {
            throw new ManipulationException("Invalid model ({}) - cannot find version!");
        }
    }

    /**
     * Create a project with only a Model. Only used by tests currently.
     *
     * @param model the Model to use.
     * @throws ManipulationException if an error occurs.
     */
    public Project(final Model model)
            throws ManipulationException {
        this(model.getPomFile(), model);
    }

    /**
     * Create a project by copying another.
     *
     * @param original the Project to use.
     */
    @SuppressWarnings("IncompleteCopyConstructor")
    public Project(final Project original) {
        this.pom = original.pom;
        this.model = original.model.clone();
        this.inheritanceRoot = original.inheritanceRoot;
        this.executionRoot = original.executionRoot;
        this.incrementalPME = original.incrementalPME;
        if (original.projectParent != null) {
            this.projectParent = new Project(original.projectParent);
        }
    }

    @Override
    public int hashCode() {
        // The interpolated-pom.xml check is to avoid problems in the integration tests.
        if (pom != null && !"interpolated-pom.xml".equals(pom.getName())) {
            // Previously was using a hash of the artifact, group and version but as the
            // version (and potentially the others) could mutate during the process this is
            // not reliable. Therefore use the location (i.e. file path) of this project.
            return pom.hashCode();
        } else {
            // Fallback to using the original GAV method. This fallback should never happen in production
            // but can happen in integration tests/manually constructed models in tests.
            final int prime = 31;
            int result = 1;
            result = prime * result + getArtifactId().hashCode();
            result = prime * result + getGroupId().hashCode();
            result = prime * result + getVersion().hashCode();
            return result;
        }
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Project other = (Project) obj;

        // Simply inlined ProjectVersionRef comparison here as ProjectVersionRef are created now
        // on demand to ensure they have the current values. However we are using VersionSpec.equals
        // in order to maintain the same semantics as ProjectVersionRef.equals.
        return getGroupId().equals(other.getGroupId()) && getArtifactId().equals(other.getArtifactId())
                && VersionUtils.createFromSpec(getVersion())
                        .equals(VersionUtils.createFromSpec(other.getVersion()));
    }

    @Override
    public String toString() {
        return getKey() + " [pom=" + pom + "]";
    }

    public File getPom() {
        return pom;
    }

    /**
     * Retrieve the model undergoing modification.
     *
     * @return the Model being modified.
     */
    public Model getModel() {
        return model;
    }

    public ProjectVersionRef getKey() {
        return new SimpleProjectVersionRef(getGroupId(), getArtifactId(), getVersion());
    }

    public Parent getModelParent() {
        return model.getParent();
    }

    /**
     * Returns the Project groupId. Also used by Interpolator.
     *
     * @return the groupId
     */
    public String getGroupId() {
        String g = model.getGroupId();

        if (g == null) {
            // Note: reliant upon model validation that the parent is not null.
            g = model.getParent().getGroupId();
        }
        return g;
    }

    /**
     * Returns the Project artifactId. Also used by Interpolator.
     *
     * @return the artifactId
     */
    public String getArtifactId() {
        return getModel().getArtifactId();
    }

    /**
     * Returns the Project version. Also used by Interpolator.
     *
     * @return the version
     */
    public String getVersion() {
        String v = model.getVersion();

        if (v == null) {
            // Note: reliant upon model validation that the parent is not null.
            v = model.getParent().getVersion();
        }
        return v;
    }

    /**
     * This method will scan the dependencies in the potentially active Profiles in this project and
     * return a fully resolved list. Note that this will only return full dependencies not managed
     * i.e. those with a group, artifact and version.
     *
     * Note that while updating the {@link Dependency} reference returned will be reflected in the
     * Model as it is the same object, if you wish to remove or add items to the Model then you
     * must use {@link #getModel()}
     *
     * @param session MavenSessionHandler, used by {@link PropertyResolver}
     * @return a list of fully resolved {@link ArtifactRef} to the original {@link Dependency}
     * @throws ManipulationException if an error occurs
     */
    public Map<Profile, Map<ArtifactRef, Dependency>> getResolvedProfileDependencies(MavenSessionHandler session)
            throws ManipulationException {
        Map<Profile, Map<ArtifactRef, Dependency>> resolvedProfileDependencies = new HashMap<>();

        for (final Profile profile : ProfileUtils.getProfiles(session, model)) {
            Map<ArtifactRef, Dependency> profileDeps = new HashMap<>();

            resolveDeps(session, profile.getDependencies(), false, profileDeps);

            resolvedProfileDependencies.put(profile, profileDeps);
        }

        return resolvedProfileDependencies;
    }

    /**
     * This method will scan the dependencies in the potentially active Profiles in this project and
     * return a fully resolved list. Note that this will return all dependencies including managed
     * i.e. those with a group, artifact and potentially empty version.
     *
     * Note that while updating the {@link Dependency} reference returned will be reflected in the
     * Model as it is the same object, if you wish to remove or add items to the Model then you
     * must use {@link #getModel()}
     *
     * @param session MavenSessionHandler, used by {@link PropertyResolver}
     * @return a list of fully resolved {@link ArtifactRef} to the original {@link Dependency}
     * @throws ManipulationException if an error occurs
     */
    public Map<Profile, Map<ArtifactRef, Dependency>> getAllResolvedProfileDependencies(MavenSessionHandler session)
            throws ManipulationException {
        Map<Profile, Map<ArtifactRef, Dependency>> allResolvedProfileDependencies = new HashMap<>();

        for (final Profile profile : ProfileUtils.getProfiles(session, model)) {
            Map<ArtifactRef, Dependency> profileDeps = new HashMap<>();

            resolveDeps(session, profile.getDependencies(), true, profileDeps);

            allResolvedProfileDependencies.put(profile, profileDeps);
        }

        return allResolvedProfileDependencies;
    }

    /**
     * This method will scan the dependencies in the dependencyManagement section of the potentially active Profiles in
     * this project and return a fully resolved list. Note that while updating the {@link Dependency}
     * reference returned will be reflected in the Model as it is the same object, if you wish to remove or add items
     * to the Model then you must use {@link #getModel()}
     *
     * @param session MavenSessionHandler, used by {@link PropertyResolver}
     * @return a list of fully resolved {@link ArtifactRef} to the original {@link Dependency} (that were within
     *         DependencyManagement)
     * @throws ManipulationException if an error occurs
     */
    public Map<Profile, Map<ArtifactRef, Dependency>> getResolvedProfileManagedDependencies(MavenSessionHandler session)
            throws ManipulationException {
        Map<Profile, Map<ArtifactRef, Dependency>> resolvedProfileManagedDependencies = new HashMap<>();

        for (final Profile profile : ProfileUtils.getProfiles(session, model)) {
            Map<ArtifactRef, Dependency> profileDeps = new HashMap<>();

            final DependencyManagement dm = profile.getDependencyManagement();

            if (dm != null) {
                resolveDeps(session, dm.getDependencies(), false, profileDeps);
            }

            resolvedProfileManagedDependencies.put(profile, profileDeps);
        }
        return resolvedProfileManagedDependencies;
    }

    /**
     * This method will scan the plugins in this project and return a fully resolved list. Note that
     * while updating the {@link Plugin} reference returned will be reflected in the Model as it is the
     * same object, if you wish to remove or add items to the Model then you must use {@link #getModel()}
     *
     * @param session MavenSessionHandler, used by {@link PropertyResolver}
     * @return a list of fully resolved {@link ProjectVersionRef} to the original {@link Plugin}
     * @throws ManipulationException if an error occurs
     */
    public Map<ProjectVersionRef, Plugin> getResolvedPlugins(MavenSessionHandler session) throws ManipulationException {
        Map<ProjectVersionRef, Plugin> resolvedPlugins = new HashMap<>();

        if (getModel().getBuild() != null) {
            resolvePlugins(session, getModel().getBuild().getPlugins(), PluginResolver.NONE, resolvedPlugins);
        }

        return resolvedPlugins;
    }

    /**
     * This method will scan the plugins in this project including those without a version and return a fully resolved
     * list. Note that while updating the {@link Plugin} reference returned will be reflected in the Model as it is the
     * same object, if you wish to remove or add items to the Model then you must use {@link #getModel()}
     *
     * @param session MavenSessionHandler, used by {@link PropertyResolver}
     * @return a list of fully resolved {@link ProjectVersionRef} to the original {@link Plugin}
     * @throws ManipulationException if an error occurs
     */
    public Map<ProjectVersionRef, Plugin> getAllResolvedPlugins(MavenSessionHandler session)
            throws ManipulationException {
        Map<ProjectVersionRef, Plugin> resolvedPlugins = new HashMap<>();

        if (getModel().getBuild() != null) {
            resolvePlugins(session, getModel().getBuild().getPlugins(), PluginResolver.ALL, resolvedPlugins);
        }

        return resolvedPlugins;
    }

    /**
     * This method will scan the plugins in the pluginManagement section of this project and return a fully
     * resolved list. Note that while updating the {@link Plugin} reference returned will be reflected in
     * the Model as it is the same object, if you wish to remove or add items to the Model then you must
     * use {@link #getModel()}
     *
     * @param session MavenSessionHandler, used by {@link PropertyResolver}
     * @return a list of fully resolved {@link ProjectVersionRef} to the original {@link Plugin}
     * @throws ManipulationException if an error occurs
     */
    public Map<ProjectVersionRef, Plugin> getResolvedManagedPlugins(MavenSessionHandler session)
            throws ManipulationException {
        Map<ProjectVersionRef, Plugin> resolvedManagedPlugins = new HashMap<>();

        if (getModel().getBuild() != null) {
            final PluginManagement pm = getModel().getBuild().getPluginManagement();
            if (!(pm == null || pm.getPlugins() == null)) {
                resolvePlugins(session, pm.getPlugins(), PluginResolver.PLUGIN_DEFAULTS, resolvedManagedPlugins);
            }
        }

        return resolvedManagedPlugins;
    }

    /**
     * This method will scan the plugins in the potentially active Profiles in this project and
     * return a fully resolved list. Note that while updating the {@link Plugin} reference
     * returned will be reflected in the Model as it is the same object, if you wish to
     * remove or add items to the Model then you must use {@link #getModel()}
     *
     * @param session MavenSessionHandler, used by {@link PropertyResolver}
     * @return a list of fully resolved {@link ProjectVersionRef} to the original {@link Plugin}
     * @throws ManipulationException if an error occurs
     */
    public Map<Profile, Map<ProjectVersionRef, Plugin>> getResolvedProfilePlugins(MavenSessionHandler session)
            throws ManipulationException {
        Map<Profile, Map<ProjectVersionRef, Plugin>> resolvedProfilePlugins = new HashMap<>();

        for (final Profile profile : ProfileUtils.getProfiles(session, model)) {
            Map<ProjectVersionRef, Plugin> profileDeps = new HashMap<>();

            if (profile.getBuild() != null) {
                resolvePlugins(session, profile.getBuild().getPlugins(), PluginResolver.NONE, profileDeps);

            }
            resolvedProfilePlugins.put(profile, profileDeps);
        }

        return resolvedProfilePlugins;
    }

    /**
     * This method will scan the plugins in the potentially active Profiles in this project including those without a
     * version and return a fully resolved list. Note that while updating the {@link Plugin} reference returned will be
     * reflected in the Model as it is the same object, if you wish to remove or add items to the Model then you must
     * use {@link #getModel()}
     *
     * @param session MavenSessionHandler, used by {@link PropertyResolver}
     * @return a list of fully resolved {@link ProjectVersionRef} to the original {@link Plugin}
     * @throws ManipulationException if an error occurs
     */
    public Map<Profile, Map<ProjectVersionRef, Plugin>> getAllResolvedProfilePlugins(MavenSessionHandler session)
            throws ManipulationException {
        Map<Profile, Map<ProjectVersionRef, Plugin>> allResolvedProfilePlugins = new HashMap<>();

        for (final Profile profile : ProfileUtils.getProfiles(session, model)) {
            Map<ProjectVersionRef, Plugin> profileDeps = new HashMap<>();

            if (profile.getBuild() != null) {
                resolvePlugins(session, profile.getBuild().getPlugins(), PluginResolver.ALL, profileDeps);

            }
            allResolvedProfilePlugins.put(profile, profileDeps);
        }

        return allResolvedProfilePlugins;
    }

    /**
     * This method will scan the plugins in the pluginManagement section in the potentially active Profiles
     * in this project and return a fully resolved list. Note that while updating the {@link Plugin}
     * reference returned will be reflected in the Model as it is the same object, if you wish to remove
     * or add items to the Model then you must use {@link #getModel()}
     *
     * @param session MavenSessionHandler, used by {@link PropertyResolver}
     * @return a list of fully resolved {@link ProjectVersionRef} to the original {@link Plugin}
     * @throws ManipulationException if an error occurs
     */
    public Map<Profile, Map<ProjectVersionRef, Plugin>> getResolvedProfileManagedPlugins(MavenSessionHandler session)
            throws ManipulationException {
        Map<Profile, Map<ProjectVersionRef, Plugin>> resolvedProfileManagedPlugins = new HashMap<>();

        for (final Profile profile : ProfileUtils.getProfiles(session, model)) {
            Map<ProjectVersionRef, Plugin> profileDeps = new HashMap<>();

            if (profile.getBuild() != null) {
                final PluginManagement pm = profile.getBuild().getPluginManagement();

                if (pm != null) {
                    resolvePlugins(session, pm.getPlugins(), PluginResolver.PLUGIN_DEFAULTS, profileDeps);
                }
            }
            resolvedProfileManagedPlugins.put(profile, profileDeps);
        }
        return resolvedProfileManagedPlugins;
    }

    /**
     * This method will scan the dependencies in this project and return a fully resolved list. Note that this
     * will only return full dependencies not managed i.e. those with a group, artifact and version.
     *
     * Note that while updating the {@link Dependency} reference returned will be reflected in the Model
     * as it is the same object, if you wish to remove or add items to the Model then you must use {@link #getModel()}
     *
     * @param session MavenSessionHandler, used by {@link PropertyResolver}
     * @return a list of fully resolved {@link ArtifactRef} to the original {@link Dependency}
     * @throws ManipulationException if an error occurs
     */
    public Map<ArtifactRef, Dependency> getResolvedDependencies(MavenSessionHandler session)
            throws ManipulationException {
        Map<ArtifactRef, Dependency> resolvedDependencies = new HashMap<>();

        resolveDeps(session, getModel().getDependencies(), false, resolvedDependencies);

        return resolvedDependencies;
    }

    /**
     * This method will scan the dependencies in this project and return a fully resolved list. Note that this
     * will return all dependencies including managed i.e. those with a group, artifact and potentially empty
     * version.
     *
     * Note that while updating the {@link Dependency} reference returned will be reflected in the Model
     * as it is the same object, if you wish to remove or add items to the Model then you must use {@link #getModel()}
     *
     * @param session MavenSessionHandler, used by {@link PropertyResolver}
     * @return a list of fully resolved {@link ArtifactRef} to the original {@link Dependency}
     * @throws ManipulationException if an error occurs
     */
    public Map<ArtifactRef, Dependency> getAllResolvedDependencies(MavenSessionHandler session)
            throws ManipulationException {
        Map<ArtifactRef, Dependency> allResolvedDependencies = new HashMap<>();

        resolveDeps(session, getModel().getDependencies(), true, allResolvedDependencies);

        return allResolvedDependencies;
    }

    /**
     * This method will scan the dependencies for *every* plugin in this project and return a list of
     * maps of fully resolved version to dependency.
     *
     * Note that this will scan for plugins included managed plugins, build plugins and those in profiles.
     * It utilises a list to return as each plugin could have the same dependencies which would conflict.
     *
     * Note that while updating the {@link Dependency} reference returned will be reflected in the Model
     * as it is the same object, if you wish to remove or add items to the Model then you must use {@link #getModel()}
     *
     * @param session MavenSessionHandler, used by {@link PropertyResolver}
     * @return a list containing maps of fully resolved {@link ArtifactRef} to the original {@link Dependency}
     * @throws ManipulationException if an error occurs
     */
    public List<Map<ArtifactRef, Dependency>> getAllResolvedPluginDependencies(MavenSessionHandler session)
            throws ManipulationException {
        List<Map<ArtifactRef, Dependency>> allResolvedDependencies = new ArrayList<>();

        if (getModel().getBuild() != null) {
            for (Plugin p : getModel().getBuild().getPlugins()) {
                Map<ArtifactRef, Dependency> dependencies = new HashMap<>();
                resolveDeps(session, p.getDependencies(), false, dependencies);
                allResolvedDependencies.add(dependencies);
            }
            if (getModel().getBuild().getPluginManagement() != null) {
                for (Plugin p : getModel().getBuild().getPluginManagement().getPlugins()) {
                    Map<ArtifactRef, Dependency> dependencies = new HashMap<>();
                    resolveDeps(session, p.getDependencies(), false, dependencies);
                    allResolvedDependencies.add(dependencies);
                }
            }
        }
        for (final Profile profile : ProfileUtils.getProfiles(session, model)) {
            if (profile.getBuild() != null) {
                for (Plugin p : profile.getBuild().getPlugins()) {
                    Map<ArtifactRef, Dependency> dependencies = new HashMap<>();
                    resolveDeps(session, p.getDependencies(), false, dependencies);
                    allResolvedDependencies.add(dependencies);
                }
                if (profile.getBuild().getPluginManagement() != null) {
                    for (Plugin p : profile.getBuild().getPluginManagement().getPlugins()) {
                        Map<ArtifactRef, Dependency> dependencies = new HashMap<>();
                        resolveDeps(session, p.getDependencies(), false, dependencies);
                        allResolvedDependencies.add(dependencies);
                    }
                }
            }
        }
        return allResolvedDependencies;
    }

    /**
     * This method will scan the dependencies in the dependencyManagement section of this project and return a
     * fully resolved list. Note that while updating the {@link Dependency} reference returned will be reflected
     * in the Model as it is the same object, if you wish to remove or add items to the Model then you must use
     * {@link #getModel()}
     *
     * @param session MavenSessionHandler, used by {@link PropertyResolver}
     * @return a list of fully resolved {@link ArtifactRef} to the original {@link Dependency} (that were within
     *         DependencyManagement)
     * @throws ManipulationException if an error occurs
     */
    public Map<ArtifactRef, Dependency> getResolvedManagedDependencies(MavenSessionHandler session)
            throws ManipulationException {
        Map<ArtifactRef, Dependency> resolvedManagedDependencies = new HashMap<>();

        final DependencyManagement dm = getModel().getDependencyManagement();
        if (!(dm == null || dm.getDependencies() == null)) {
            resolveDeps(session, dm.getDependencies(), false, resolvedManagedDependencies);
        }

        return resolvedManagedDependencies;
    }

    private void resolveDeps(
            MavenSessionHandler session,
            List<Dependency> deps,
            boolean includeManagedDependencies,
            Map<ArtifactRef, Dependency> resolvedDependencies)
            throws ManipulationException {
        ListIterator<Dependency> iterator = deps.listIterator(deps.size());

        // Iterate in reverse order so later deps take precedence
        while (iterator.hasPrevious()) {
            Dependency d = iterator.previous();

            if (session.getExcludedScopes().contains(d.getScope())) {
                logger.debug("Ignoring dependency {} as scope matched {}", d, session.getExcludedScopes());
                continue;
            }

            String g = PropertyResolver.resolveInheritedProperties(
                    session,
                    this,
                    "${project.groupId}".equals(d.getGroupId()) ? getGroupId() : d.getGroupId());
            String a = PropertyResolver.resolveInheritedProperties(
                    session,
                    this,
                    "${project.artifactId}".equals(d.getArtifactId()) ? getArtifactId() : d.getArtifactId());
            String v = PropertyResolver.resolveInheritedProperties(session, this, d.getVersion());

            if (includeManagedDependencies && isEmpty(v)) {
                v = "*";
            }
            if (isNotEmpty(g) && isNotEmpty(a) && isNotEmpty(v)) {
                SimpleScopedArtifactRef sar = new SimpleScopedArtifactRef(
                        g,
                        a,
                        v,
                        d.getType(),
                        d.getClassifier(),
                        d.getScope());

                // If the GAVTC already exists within the map it means we have a duplicate entry. While Maven
                // technically allows this it does warn that this leads to unstable models. In PME case this breaks
                // the indexing as we don't have duplicate entries. Given they are exact matches, remove older duplicate.
                if (resolvedDependencies.containsKey(sar)) {
                    logger.error("Found duplicate entry within dependency list. Key of {} and dependency {}", sar, d);
                    iterator.remove();
                } else {
                    Dependency old = resolvedDependencies.put(sar, d);

                    if (old != null) {
                        logger.error(
                                "Internal project dependency resolution failure ; replaced {} in store by {}:{}:{}.",
                                old,
                                g,
                                a,
                                v);
                        throw new ManipulationException(
                                "Internal project dependency resolution failure ; replaced {} by {}",
                                old,
                                d);
                    }
                }
            }
        }
    }

    private void resolvePlugins(
            MavenSessionHandler session,
            List<Plugin> plugins,
            PluginResolver includeManagedPlugins,
            Map<ProjectVersionRef, Plugin> resolvedPlugins)
            throws ManipulationException {
        ListIterator<Plugin> iterator = plugins.listIterator(plugins.size());

        // Iterate in reverse order so later plugins take precedence
        while (iterator.hasPrevious()) {
            Plugin p = iterator.previous();

            String g = PropertyResolver.resolveInheritedProperties(
                    session,
                    this,
                    "${project.groupId}".equals(p.getGroupId()) ? getGroupId() : p.getGroupId());
            String a = PropertyResolver.resolveInheritedProperties(
                    session,
                    this,
                    "${project.artifactId}".equals(p.getArtifactId()) ? getArtifactId() : p.getArtifactId());
            String v = PropertyResolver.resolveInheritedProperties(session, this, p.getVersion());

            // Its possible the internal plugin list is either abbreviated or empty. Attempt to fill in default values for
            // comparison purposes.
            if (isEmpty(g)) {
                g = PLUGIN_DEFAULTS.getDefaultGroupId(a);
            }
            if (isEmpty(v)) {
                // For managed plugins, if the version is blank we always check the default list.
                // If getAll* has been called then if we can't find a version in the default list, dummy one up.
                if (includeManagedPlugins == PluginResolver.ALL
                        || includeManagedPlugins == PluginResolver.PLUGIN_DEFAULTS) {
                    v = PLUGIN_DEFAULTS.getDefaultVersion(g, a);
                    if ("[0.0.0.1,]".equals(v)) {
                        v = "";
                    }
                }
                if (isEmpty(v) && includeManagedPlugins == PluginResolver.ALL) {
                    v = "*";
                }
            }
            // Theoretically we could default an empty v via PLUGIN_DEFAULTS.getDefaultVersion( g, a ) but
            // this means managed plugins would be included which confuses things.
            if (isNotEmpty(g) && isNotEmpty(a) && isNotEmpty(v)) {
                SimpleProjectVersionRef spv = new SimpleProjectVersionRef(g, a, v);

                // If the GAV already exists within the map it means we have a duplicate entry. While Maven
                // technically allows this it does warn that this leads to unstable models. In PME case this breaks
                // the indexing as we don't have duplicate entries. Given they are exact matches, remove older duplicate.
                if (resolvedPlugins.containsKey(spv)) {
                    logger.error("Found duplicate entry within plugin list. Key of {} and plugin {}", spv, p);
                    iterator.remove();
                } else {
                    Plugin old = resolvedPlugins.put(spv, p);

                    if (old != null) {
                        logger.error(
                                "Internal project plugin resolution failure ; replaced {} in store by {}.",
                                old,
                                spv);
                        throw new ManipulationException(
                                "Internal project plugin resolution failure ; replaced {} in store by {}.",
                                old,
                                spv);
                    }
                }
            }
        }
    }

    public void setInheritanceRoot(final boolean inheritanceRoot) {
        this.inheritanceRoot = inheritanceRoot;
    }

    /**
     * @return true if this Project represents the top level POM of a build.
     */
    public boolean isInheritanceRoot() {
        return inheritanceRoot;
    }

    public void setExecutionRoot() {
        executionRoot = true;
    }

    /**
     * Returns whether this project is the execution root.
     *
     * @return true if this Project is the execution root.
     */
    public boolean isExecutionRoot() {
        return executionRoot;
    }

    public void setIncrementalPME(boolean incrementalPME) {
        this.incrementalPME = incrementalPME;
    }

    public boolean isIncrementalPME() {
        return incrementalPME;
    }

    public void setProjectParent(Project parent) {
        this.projectParent = parent;
    }

    public Project getProjectParent() {
        return projectParent;
    }

    /**
     * @return inherited projects. Returned with order of root project first, down to this project.
     */
    public List<Project> getInheritedList() {
        final List<Project> found = new ArrayList<>();
        found.add(this);

        Project loop = this;
        while (loop.getProjectParent() != null) {
            // Place inherited first so latter down tree take precedence.
            found.add(0, loop.getProjectParent());
            loop = loop.getProjectParent();
        }
        return found;
    }

    /**
     * @return inherited projects. Returned with order of this project first, up to root project.
     */
    public List<Project> getReverseInheritedList() {
        final List<Project> found = new ArrayList<>();
        found.add(this);

        Project loop = this;
        while (loop.getProjectParent() != null) {
            // Place inherited last for iteration purposes
            found.add(loop.getProjectParent());
            loop = loop.getProjectParent();
        }
        return found;
    }

    public void updateProfiles(List<Profile> remoteProfiles) {
        final List<Profile> profiles = model.getProfiles();

        if (!remoteProfiles.isEmpty()) {
            for (Profile profile : remoteProfiles) {
                final Iterator<Profile> i = profiles.iterator();
                while (i.hasNext()) {
                    final Profile p = i.next();

                    if (profile.getId().equals(p.getId())) {
                        logger.debug("Removing local profile {}", p);
                        i.remove();
                        // Don't break out of the loop so we can check for active profiles
                    }

                    // If we have injected profiles and one of the current profiles is using
                    // activeByDefault it will get mistakenly deactivated due to the semantics
                    // of activeByDefault. Therefore replace the activation.
                    if (p.getActivation() != null && p.getActivation().isActiveByDefault()) {
                        logger.warn("Profile {} is activeByDefault", p);

                        final Activation replacement = new Activation();
                        final ActivationProperty replacementProp = new ActivationProperty();
                        replacementProp.setName("!disableProfileActivation");
                        replacement.setProperty(replacementProp);

                        p.setActivation(replacement);
                    }
                }

                logger.debug("Adding profile {}", profile);
                profiles.add(profile);
            }
        }
    }
}
