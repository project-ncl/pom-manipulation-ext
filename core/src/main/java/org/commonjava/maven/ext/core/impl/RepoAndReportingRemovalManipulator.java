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
package org.commonjava.maven.ext.core.impl;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.model.Model;
import org.apache.maven.model.Profile;
import org.apache.maven.model.Repository;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.SettingsUtils;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.model.Project;
import org.commonjava.maven.ext.common.util.ProfileUtils;
import org.commonjava.maven.ext.common.util.PropertyResolver;
import org.commonjava.maven.ext.core.ManipulationSession;
import org.commonjava.maven.ext.core.state.RepoReportingState;
import org.commonjava.maven.ext.io.SettingsIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link Manipulator} implementation that can remove Reporting and Repository sections from a project's pom file.
 * Configuration is stored in a {@link RepoReportingState} instance, which is in turn stored in the
 * {@link ManipulationSession}.
 */
@Named("enforce-repo-reporting-removal")
@Singleton
public class RepoAndReportingRemovalManipulator
        implements Manipulator {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final SettingsIO settingsWriter;

    private ManipulationSession session;

    @Inject
    public RepoAndReportingRemovalManipulator(SettingsIO settingsWriter) {
        this.settingsWriter = settingsWriter;
    }

    /**
     * Initialize the {@link RepoReportingState} state holder in the {@link ManipulationSession}. This state holder
     * detects
     * version-change configuration from the Maven user properties (-D properties from the CLI) and makes it available
     * for
     * later.
     */
    @Override
    public void init(final ManipulationSession session) {
        this.session = session;
        session.setState(new RepoReportingState(session.getUserProperties()));
    }

    /**
     * Apply the reporting and repository removal changes to the list of {@link Project}'s given.
     */
    @Override
    public Set<Project> applyChanges(final List<Project> projects)
            throws ManipulationException {
        final RepoReportingState state = session.getState(RepoReportingState.class);
        if (!session.isEnabled() || !state.isEnabled()) {
            logger.debug("{}: Nothing to do!", getClass().getSimpleName());
            return Collections.emptySet();
        }

        final Set<Project> changed = new HashSet<>();

        Settings backupSettings = new Settings();
        Profile backupProfile = new Profile();
        backupProfile.setId("removed-by-pme");
        backupSettings.addActiveProfile("removed-by-pme");

        for (final Project project : projects) {
            logger.debug("Applying changes to: {}:{}", project.getGroupId(), project.getArtifactId());
            final Model model = project.getModel();

            Iterator<Repository> it = model.getRepositories().iterator();
            while (it.hasNext()) {
                Repository repository = it.next();

                if (removeRepository(state, repository)) {
                    processRepository(session, project, repository);
                    if (!backupProfile.getRepositories().contains(repository)) {
                        backupProfile.addRepository(repository);
                    }
                    it.remove();
                    changed.add(project);
                }
            }

            it = model.getPluginRepositories().iterator();
            while (it.hasNext()) {
                Repository repository = it.next();
                if (removeRepository(state, repository)) {
                    processRepository(session, project, repository);
                    if (!backupProfile.getPluginRepositories().contains(repository)) {
                        backupProfile.addPluginRepository(repository);
                    }
                    it.remove();
                    changed.add(project);
                }
            }

            if (model.getReporting() != null) {
                model.setReporting(null);
                changed.add(project);
            }

            // Remove repositories in the profiles as well
            final List<Profile> profiles = ProfileUtils.getProfiles(session, model);

            for (final Profile profile : profiles) {
                Profile repoProfile = new Profile();
                repoProfile.setId(profile.getId());

                it = profile.getRepositories().iterator();
                while (it.hasNext()) {
                    Repository repository = it.next();
                    if (removeRepository(state, repository)) {
                        processRepository(session, project, repository);
                        if (!repoProfile.getRepositories().contains(repository)) {
                            repoProfile.addRepository(repository);
                        }
                        if (!backupSettings.getActiveProfiles().contains(profile.getId())) {
                            backupSettings.addActiveProfile(profile.getId());
                        }
                        it.remove();
                        changed.add(project);
                    }
                }

                it = profile.getPluginRepositories().iterator();
                while (it.hasNext()) {
                    Repository repository = it.next();
                    if (removeRepository(state, repository)) {
                        processRepository(session, project, repository);
                        if (!repoProfile.getPluginRepositories().contains(repository)) {
                            repoProfile.addPluginRepository(repository);
                        }
                        if (!backupSettings.getActiveProfiles().contains(profile.getId())) {
                            backupSettings.addActiveProfile(profile.getId());
                        }
                        it.remove();
                        changed.add(project);
                    }
                }

                if (profile.getReporting() != null) {
                    profile.setReporting(null);
                    changed.add(project);
                }

                if (!repoProfile.getRepositories().isEmpty() || !repoProfile.getPluginRepositories().isEmpty()) {
                    backupSettings.addProfile(SettingsUtils.convertToSettingsProfile(repoProfile));
                }
            }
        }

        // create new settings file with the removed repositories and reporting
        if (!backupProfile.getRepositories().isEmpty() || !backupProfile.getPluginRepositories().isEmpty()) {
            backupSettings.addProfile(SettingsUtils.convertToSettingsProfile(backupProfile));

            String settingsFilePath = state.getRemovalBackupSettings();

            if (!isEmpty(settingsFilePath)) {
                File settingsFile;
                if (settingsFilePath.equals("settings.xml")) {
                    String topDir = session.getTargetDir().getParentFile().getPath();
                    settingsFile = new File(topDir, settingsFilePath);
                } else {
                    settingsFile = new File(settingsFilePath);
                }
                settingsWriter.update(backupSettings, settingsFile);
            }
        }

        return changed;
    }

    private void processRepository(ManipulationSession session, Project project, Repository repository)
            throws ManipulationException {
        repository.setUrl(PropertyResolver.resolveInheritedProperties(session, project, repository.getUrl()));
        repository.setId(PropertyResolver.resolveInheritedProperties(session, project, repository.getId()));
        repository.setName(PropertyResolver.resolveInheritedProperties(session, project, repository.getName()));
        if (isBlank(repository.getName())) {
            repository.setName(repository.getId());
        }
    }

    /**
     * Examine the repository to see if we should not remove it.
     * 
     * @param state to query the ignoreLocal value
     * @param repo the repository to examine
     * @return boolean whether to remove this repository
     */
    private boolean removeRepository(RepoReportingState state, Repository repo) {
        boolean result = true;

        if (state.ignoreLocal()) {
            String url = repo.getUrl();
            // According to https://maven.apache.org/plugins/maven-site-plugin/examples/adding-deploy-protocol.html
            // supported repositories are file, http and https.
            if (url.startsWith("file:") ||
                    url.startsWith("http://localhost") ||
                    url.startsWith("https://localhost") ||
                    url.startsWith("http://127.0.0.1") ||
                    url.startsWith("https://127.0.0.1") ||
                    url.startsWith("http://::1") ||
                    url.startsWith("https://::1")) {
                result = false;
            }
        }
        return result;
    }

    @Override
    public int getExecutionIndex() {
        return 50;
    }
}
