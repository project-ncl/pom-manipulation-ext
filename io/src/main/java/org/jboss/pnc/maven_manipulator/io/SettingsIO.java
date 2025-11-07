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
package org.jboss.pnc.maven_manipulator.io;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Profile;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.shared.release.ReleaseExecutionException;
import org.apache.maven.shared.release.transform.ModelETL;
import org.apache.maven.shared.release.transform.ModelETLRequest;
import org.apache.maven.shared.release.transform.jdom2.JDomModelETL;
import org.apache.maven.shared.release.transform.jdom2.JDomModelETLFactory;
import org.jboss.pnc.maven_manipulator.common.ManipulationException;
import org.jboss.pnc.maven_manipulator.common.jdom.JDOMSettingsConverter;
import org.jboss.pnc.maven_manipulator.common.util.LineSeparator;
import org.jdom2.JDOMException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author vdedik@redhat.com
 */
@Named
@Singleton
public class SettingsIO {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final JDOMSettingsConverter jdomSettingsConverter = new JDOMSettingsConverter();

    private final JDomModelETLFactory modelETLFactories = new JDomModelETLFactory();

    private final SettingsBuilder settingsBuilder;

    @Inject
    public SettingsIO(SettingsBuilder settingsBuilder) {
        this.settingsBuilder = settingsBuilder;
    }

    /**
     * Writes a settings file out to the denoted file. If the settings file exists it attempts to preserve existing
     * formatting.
     *
     * @param settings the Maven settings to write out.
     * @param settingsFile the File to write to
     * @throws ManipulationException if an error occurs.
     */
    public void write(Settings settings, File settingsFile)
            throws ManipulationException {
        try {
            LineSeparator ls;
            String intro = "";
            String outtro = "";

            // If we are building from an existing file then use that as a base otherwise we need to construct the Document
            // and root Element.
            if (settingsFile.length() > 0) {
                ls = FileIO.determineEOL(settingsFile);
                ModelETLRequest request = new ModelETLRequest();
                request.setLineSeparator(ls.value());
                ModelETL etl = modelETLFactories.newInstance(request);

                // Reread in order to fill in JdomModelETL
                etl.extract(settingsFile);

                // Annoyingly the document is private but we need to access it in order to ensure the model is written to the Document.
                //
                // Currently the fields we want to access are private - https://issues.apache.org/jira/browse/MRELEASE-1044 requests
                // them to be protected to avoid this reflection.
                intro = (String) FieldUtils.getDeclaredField(JDomModelETL.class, "intro", true).get(etl);
                outtro = (String) FieldUtils.getDeclaredField(JDomModelETL.class, "outtro", true).get(etl);
            } else {
                ls = LineSeparator.NL;
            }
            jdomSettingsConverter.setLineSeparator(ls.value());
            jdomSettingsConverter.write(settings, settingsFile, intro, outtro);
        } catch (IOException | JDOMException | ReleaseExecutionException | IllegalAccessException e) {
            throw new ManipulationException(
                    "Failed to create repo removal backup settings.xml file: {}",
                    settingsFile,
                    e);
        }
    }

    public void update(Settings settings, File settingsFile)
            throws ManipulationException {
        try {
            Settings defaultSettings = new Settings();

            if (settingsFile.exists()) {
                DefaultSettingsBuildingRequest settingsRequest = new DefaultSettingsBuildingRequest();
                settingsRequest.setGlobalSettingsFile(settingsFile);
                defaultSettings = settingsBuilder.build(settingsRequest).getEffectiveSettings();
            }

            for (Profile profile : settings.getProfiles()) {
                defaultSettings.getProfiles().removeIf(profile1 -> profile1.getId().equals(profile.getId()));
                defaultSettings.addProfile(profile);
            }
            for (String activeProfile : settings.getActiveProfiles()) {
                defaultSettings.getActiveProfiles().removeIf(s -> s.equals(activeProfile));
                defaultSettings.addActiveProfile(activeProfile);
            }
            for (Mirror mirror : settings.getMirrors()) {
                defaultSettings.addMirror(mirror);
            }
            for (Proxy proxy : settings.getProxies()) {
                defaultSettings.addProxy(proxy);
            }
            for (Server server : settings.getServers()) {
                defaultSettings.addServer(server);
            }
            for (String pluginGroup : settings.getPluginGroups()) {
                defaultSettings.addPluginGroup(pluginGroup);
            }
            if (settings.getLocalRepository() != null) {
                defaultSettings.setLocalRepository(settings.getLocalRepository());
            }

            write(defaultSettings, settingsFile);
        } catch (SettingsBuildingException e) {
            throw new ManipulationException(
                    "Failed to build existing settings.xml for repo removal backup.",
                    settingsFile,
                    e.getMessage(),
                    e);
        }
    }
}
