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
package org.jboss.pnc.maven_manipulator.io.resolver;

import java.io.File;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.repository.MirrorSelector;
import org.apache.maven.settings.Settings;
import org.commonjava.maven.galley.TransferManager;
import org.commonjava.maven.galley.auth.MemoryPasswordManager;
import org.commonjava.maven.galley.cache.FileCacheProvider;
import org.commonjava.maven.galley.config.TransportManagerConfig;
import org.commonjava.maven.galley.event.NoOpFileEventManager;
import org.commonjava.maven.galley.filearc.FileTransport;
import org.commonjava.maven.galley.filearc.ZipJarTransport;
import org.commonjava.maven.galley.internal.TransferManagerImpl;
import org.commonjava.maven.galley.internal.xfer.DownloadHandler;
import org.commonjava.maven.galley.internal.xfer.ExistenceHandler;
import org.commonjava.maven.galley.internal.xfer.ListingHandler;
import org.commonjava.maven.galley.internal.xfer.UploadHandler;
import org.commonjava.maven.galley.io.HashedLocationPathGenerator;
import org.commonjava.maven.galley.io.NoOpTransferDecorator;
import org.commonjava.maven.galley.io.SpecialPathManagerImpl;
import org.commonjava.maven.galley.io.TransferDecoratorManager;
import org.commonjava.maven.galley.maven.ArtifactManager;
import org.commonjava.maven.galley.maven.ArtifactMetadataManager;
import org.commonjava.maven.galley.maven.internal.ArtifactManagerImpl;
import org.commonjava.maven.galley.maven.internal.ArtifactMetadataManagerImpl;
import org.commonjava.maven.galley.maven.internal.defaults.StandardMaven350PluginDefaults;
import org.commonjava.maven.galley.maven.internal.defaults.StandardMavenPluginImplications;
import org.commonjava.maven.galley.maven.internal.type.StandardTypeMapper;
import org.commonjava.maven.galley.maven.internal.version.VersionResolverImpl;
import org.commonjava.maven.galley.maven.model.view.XPathManager;
import org.commonjava.maven.galley.maven.parse.MavenMetadataReader;
import org.commonjava.maven.galley.maven.parse.MavenPomReader;
import org.commonjava.maven.galley.maven.parse.XMLInfrastructure;
import org.commonjava.maven.galley.maven.spi.defaults.MavenPluginDefaults;
import org.commonjava.maven.galley.maven.spi.defaults.MavenPluginImplications;
import org.commonjava.maven.galley.maven.spi.type.TypeMapper;
import org.commonjava.maven.galley.maven.spi.version.VersionResolver;
import org.commonjava.maven.galley.model.Location;
import org.commonjava.maven.galley.nfc.MemoryNotFoundCache;
import org.commonjava.maven.galley.spi.cache.CacheProvider;
import org.commonjava.maven.galley.spi.event.FileEventManager;
import org.commonjava.maven.galley.spi.io.PathGenerator;
import org.commonjava.maven.galley.spi.nfc.NotFoundCache;
import org.commonjava.maven.galley.spi.transport.LocationExpander;
import org.commonjava.maven.galley.spi.transport.Transport;
import org.commonjava.maven.galley.spi.transport.TransportManager;
import org.commonjava.maven.galley.transport.TransportManagerImpl;
import org.commonjava.maven.galley.transport.htcli.HttpClientTransport;
import org.commonjava.maven.galley.transport.htcli.HttpImpl;
import org.jboss.pnc.maven_manipulator.common.ManipulationException;
import org.jboss.pnc.maven_manipulator.common.session.MavenSessionHandler;

/**
 * Manager component responsible for setting up and managing the Galley API instances used to resolve POMs and metadata.
 *
 * @author jdcasey
 */
@Named("galley")
@Singleton
public class GalleyInfrastructure
        implements ExtensionInfrastructure {
    private final MirrorSelector mirrorSelector;

    private final MavenSessionHandler sessionHandler;

    private MavenPomReader pomReader;

    private ArtifactManager artifactManager;

    private MavenMetadataReader metadataReader;

    private XMLInfrastructure xml;

    private XPathManager xpaths;

    private ExecutorService executor;

    public File getCacheDir() {
        return cacheDir;
    }

    private File cacheDir;

    @Inject
    public GalleyInfrastructure(MavenSessionHandler session, MirrorSelector mirrorSelector) {
        this.mirrorSelector = mirrorSelector;
        this.sessionHandler = session;
    }

    @Override
    public GalleyInfrastructure init()
            throws ManipulationException {
        if (sessionHandler == null) {
            return init(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
        } else {
            return init(
                    sessionHandler.getTargetDir(),
                    sessionHandler.getRemoteRepositories(),
                    sessionHandler.getLocalRepository(),
                    sessionHandler.getSettings(),
                    sessionHandler.getActiveProfiles(),
                    null,
                    null,
                    null);
        }
    }

    @Override
    public GalleyInfrastructure init(final Location customLocation, final Transport customTransport, File cacheDir)
            throws ManipulationException {
        if (sessionHandler == null) {
            return init(
                    null,
                    null,
                    null,
                    null,
                    null,
                    customLocation,
                    customTransport,
                    cacheDir);
        } else {
            return init(
                    sessionHandler.getTargetDir(),
                    sessionHandler.getRemoteRepositories(),
                    sessionHandler.getLocalRepository(),
                    sessionHandler.getSettings(),
                    sessionHandler.getActiveProfiles(),
                    customLocation,
                    customTransport,
                    cacheDir);
        }
    }

    /**
     * Allow for a 'shell' infrastructure with majority not implemented but just the cache directory for FileIO. This
     * is useful for both tests and usage of FileIO within GME.
     *
     * @param targetDirectory the directory to create the cache in
     * @return this instance
     * @throws ManipulationException if an error occurs.
     */
    @Override
    public GalleyInfrastructure init(File targetDirectory) throws ManipulationException {
        if (sessionHandler == null) {
            if (cacheDir == null) {
                cacheDir = new File(targetDirectory, "manipulator-cache");
            }
            return this;
        } else {
            throw new ManipulationException("Partial infrastructure creation");
        }
    }

    private GalleyInfrastructure init(
            final File targetDirectory,
            final List<ArtifactRepository> remoteRepositories,
            final ArtifactRepository localRepository,
            final Settings settings,
            final List<String> activeProfiles,
            final Location customLocation,
            final Transport customTransport,
            File cacheDir_)
            throws ManipulationException {
        LocationExpander locationExpander;
        try {
            final List<Location> custom = customLocation == null ? Collections.emptyList()
                    : Collections.singletonList(customLocation);

            locationExpander = new MavenLocationExpander(
                    custom,
                    remoteRepositories,
                    localRepository,
                    mirrorSelector,
                    settings,
                    activeProfiles);
        } catch (final MalformedURLException e) {
            throw new ManipulationException("Failed to setup Maven-specific LocationExpander: {}", e.getMessage(), e);
        }

        xml = new XMLInfrastructure();
        xpaths = new XPathManager();

        final TransportManager transports;
        if (customTransport != null) {
            transports = new TransportManagerImpl(customTransport);
        } else {
            transports = new TransportManagerImpl(
                    new HttpClientTransport(new HttpImpl(new MemoryPasswordManager())),
                    new FileTransport(),
                    new ZipJarTransport());
        }

        cacheDir = cacheDir_;
        if (cacheDir == null) {
            cacheDir = new File(targetDirectory, "manipulator-cache");
        }

        final FileEventManager fileEvents = new NoOpFileEventManager();
        final PathGenerator pathGenerator = new HashedLocationPathGenerator();
        final TransferDecoratorManager transferDecoratorManager = new TransferDecoratorManager(
                new NoOpTransferDecorator());
        final CacheProvider cache = new FileCacheProvider(
                cacheDir,
                pathGenerator,
                fileEvents,
                transferDecoratorManager);

        final NotFoundCache nfc = new MemoryNotFoundCache();
        executor = Executors.newCachedThreadPool();

        final TransportManagerConfig config = new TransportManagerConfig();

        final TransferManager transfers = new TransferManagerImpl(
                transports,
                cache,
                nfc,
                fileEvents,
                new DownloadHandler(nfc, config, executor),
                new UploadHandler(nfc, config, executor),
                new ListingHandler(nfc),
                new ExistenceHandler(nfc),
                new SpecialPathManagerImpl(),
                executor);

        final TypeMapper types = new StandardTypeMapper();
        final ArtifactMetadataManager metadataManager = new ArtifactMetadataManagerImpl(transfers, locationExpander);
        final VersionResolver versionResolver = new VersionResolverImpl(
                new MavenMetadataReader(xml, locationExpander, metadataManager, xpaths));

        artifactManager = new ArtifactManagerImpl(transfers, locationExpander, types, versionResolver);

        final MavenPluginDefaults pluginDefaults = new StandardMaven350PluginDefaults();
        final MavenPluginImplications pluginImplications = new StandardMavenPluginImplications(xml);

        pomReader = new MavenPomReader(
                xml,
                locationExpander,
                artifactManager,
                xpaths,
                pluginDefaults,
                pluginImplications);

        metadataReader = new MavenMetadataReader(xml, locationExpander, metadataManager, xpaths);

        return this;
    }

    public MavenPomReader getPomReader() {
        return pomReader;
    }

    public XMLInfrastructure getXml() {
        return xml;
    }

    public MavenMetadataReader getMetadataReader() {
        return metadataReader;
    }

    public ArtifactManager getArtifactManager() {
        return artifactManager;
    }

    public XPathManager getXPath() {
        return xpaths;
    }

    public void finish() {
        executor.shutdown();
    }
}
