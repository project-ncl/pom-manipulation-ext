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
package org.jboss.pnc.mavenmanipulator.core;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.settings.Settings;
import org.jboss.pnc.mavenmanipulator.annotation.ConfigValue;
import org.jboss.pnc.mavenmanipulator.common.ManipulationException;
import org.jboss.pnc.mavenmanipulator.common.model.Project;
import org.jboss.pnc.mavenmanipulator.common.session.MavenSessionHandler;
import org.jboss.pnc.mavenmanipulator.common.util.ManifestUtils;
import org.jboss.pnc.mavenmanipulator.core.impl.Manipulator;
import org.jboss.pnc.mavenmanipulator.core.state.CommonState;
import org.jboss.pnc.mavenmanipulator.core.state.State;
import org.jboss.pnc.mavenmanipulator.core.state.VersioningState;

/**
 * Repository for components that help manipulate POMs as needed, and state related to each {@link Manipulator}
 * (which contains configuration and changes to be applied). This is basically a clearing house for state required by
 * the different parts of the
 * manipulator extension.
 *
 * @author jdcasey
 */
@Named
@Singleton
public class ManipulationSession
        implements MavenSessionHandler {
    @ConfigValue(docIndex = "../index.html#disabling-the-extension")
    private static final String MANIPULATIONS_DISABLED_PROP = "manipulation.disable";

    private final Map<Class<?>, State> states = new HashMap<>();

    private MavenSession mavenSession;

    /**
     * List of <code>Project</code> instances.
     */
    private List<Project> projects;

    private ManipulationException error;

    public ManipulationSession() {
        System.out.println(
                "[INFO] Running Maven Manipulation Extension (PME) "
                        + ManifestUtils.getManifestInformation(ManipulationSession.class));
    }

    /**
     * True (enabled) by default, this is the kill switch for all manipulations. Manipulator implementations MAY also be
     * enabled/disabled
     * individually.
     *
     * @see #MANIPULATIONS_DISABLED_PROP
     * @see VersioningState#isEnabled()
     *
     * @return whether the PME subsystem is enabled.
     */
    public boolean isEnabled() {
        return !Boolean.parseBoolean(getUserProperties().getProperty(MANIPULATIONS_DISABLED_PROP, "false"));
    }

    public void setState(final State state) {
        states.put(state.getClass(), state);
    }

    /**
     * This will re-initialise any state linked to this session. This is useful if the control properties have been
     * updated.
     *
     * @throws ManipulationException if an error occurs
     */
    public void reinitialiseStates() throws ManipulationException {
        for (State s : states.values()) {
            s.initialise(getUserProperties());
        }
    }

    public <T extends State> T getState(final Class<T> stateType) {
        return stateType.cast(states.get(stateType));
    }

    public void setMavenSession(final MavenSession mavenSession) {
        this.mavenSession = mavenSession;
    }

    @Override
    public Properties getUserProperties() {
        return mavenSession == null ? new Properties()
                : mavenSession.getRequest()
                        .getUserProperties();
    }

    public void setProjects(final List<Project> projects) {
        this.projects = projects;
    }

    public List<Project> getProjects() {
        return projects;
    }

    @Override
    public List<ArtifactRepository> getRemoteRepositories() {
        return mavenSession == null ? null
                : mavenSession.getRequest()
                        .getRemoteRepositories();
    }

    @Override
    public File getPom() throws ManipulationException {
        if (mavenSession == null) {
            throw new ManipulationException("Invalid session");
        }

        return mavenSession.getRequest().getPom();
    }

    @Override
    public File getTargetDir() {
        if (mavenSession == null) {
            return new File("target");
        }

        final File pom = mavenSession.getRequest()
                .getPom();
        if (pom == null) {
            return new File("target");
        }

        return new File(pom.getParentFile(), "target");
    }

    @Override
    public ArtifactRepository getLocalRepository() {
        return mavenSession == null ? null
                : mavenSession.getRequest()
                        .getLocalRepository();
    }

    /**
     * Used by extension ManipulatingEventSpy to store any errors during project construction and manipulation
     *
     * @param error record any exception that occurred.
     */
    public void setError(final ManipulationException error) {
        this.error = error;
    }

    /**
     * Used by extension ManipulatinglifeCycleParticipant to retrieve any errors stored
     * by ManipulatingEventSpy
     *
     * @return ManipulationException
     */
    public ManipulationException getError() {
        return error;
    }

    @Override
    public List<String> getActiveProfiles() {
        return mavenSession == null || mavenSession.getRequest() == null ? Collections.emptyList()
                : mavenSession.getRequest().getActiveProfiles();
    }

    @Override
    public Settings getSettings() {
        return mavenSession == null ? null : mavenSession.getSettings();
    }

    /**
     * Checks all known states to determine whether any are enabled. Will ignore any states within
     * the supplied list.
     *
     * @param ignoreList the list of States that should be ignored when checking if any are enabled.
     * @return whether any of the States are enabled.
     */
    public boolean anyStateEnabled(List<Class<? extends State>> ignoreList) {
        boolean result = false;

        for (final Entry<Class<?>, State> entry : states.entrySet()) {
            final Class<?> c = entry.getKey();
            final State state = entry.getValue();

            if (!ignoreList.contains(c) && state.isEnabled()) {
                result = true;
                break;
            }
        }
        return result;
    }

    @Override
    public List<String> getExcludedScopes() {
        // In some tests, CommonState is not available so check for it first.
        if (states.containsKey(CommonState.class)) {
            return getState(CommonState.class).getExcludedScopes();
        }
        return Collections.emptyList();
    }

    /**
     * @return Returns the current MavenSession
     */
    MavenSession getSession() {
        return mavenSession;
    }
}
