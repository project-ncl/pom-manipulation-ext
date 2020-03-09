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
package org.commonjava.maven.ext.io;

import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Profile;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.io.xpp3.SettingsXpp3Writer;
import org.commonjava.maven.ext.common.ManipulationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author vdedik@redhat.com
 */
@Named
@Singleton
public class SettingsIO
{
    protected final Logger logger = LoggerFactory.getLogger( getClass() );

    private SettingsBuilder settingsBuilder;

    @Inject
    public SettingsIO (SettingsBuilder settingsBuilder)
    {
        this.settingsBuilder = settingsBuilder;
    }

    public void write( Settings settings, File settingsFile )
        throws ManipulationException
    {
        try
        {
            PrintWriter printWriter = new PrintWriter( settingsFile, settings.getModelEncoding() );
            new SettingsXpp3Writer().write( printWriter, settings );
        }
        catch ( IOException e )
        {
            throw new ManipulationException( "Failed to create repo removal backup settings.xml file: {}",
                                             settingsFile, e );
        }
    }

    public void update( Settings settings, File settingsFile )
        throws ManipulationException
    {
        try
        {
            Settings defaultSettings = new Settings();

            if ( settingsFile.exists() )
            {
                DefaultSettingsBuildingRequest settingsRequest = new DefaultSettingsBuildingRequest();
                settingsRequest.setGlobalSettingsFile( settingsFile );
                defaultSettings = settingsBuilder.build( settingsRequest ).getEffectiveSettings();
            }

            for ( Profile profile : settings.getProfiles() )
            {
                defaultSettings.getProfiles().removeIf( profile1 -> profile1.getId().equals( profile.getId() ) );
                defaultSettings.addProfile( profile );
            }
            for ( String activeProfile : settings.getActiveProfiles() )
            {
                defaultSettings.getActiveProfiles().removeIf( s -> s.equals( activeProfile ) );
                defaultSettings.addActiveProfile( activeProfile );
            }
            for ( Mirror mirror : settings.getMirrors() )
            {
                defaultSettings.addMirror( mirror );
            }
            for ( Proxy proxy : settings.getProxies() )
            {
                defaultSettings.addProxy( proxy );
            }
            for ( Server server : settings.getServers() )
            {
                defaultSettings.addServer( server );
            }
            for ( String pluginGroup : settings.getPluginGroups() )
            {
                defaultSettings.addPluginGroup( pluginGroup );
            }
            if ( settings.getLocalRepository() != null )
            {
                defaultSettings.setLocalRepository( settings.getLocalRepository() );
            }

            write( defaultSettings, settingsFile );
        }
        catch ( SettingsBuildingException e )
        {
            throw new ManipulationException( "Failed to build existing settings.xml for repo removal backup.", settingsFile, e.getMessage(), e );
        }
    }
}
