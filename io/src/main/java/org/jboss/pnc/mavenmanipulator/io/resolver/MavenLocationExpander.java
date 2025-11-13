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
package org.jboss.pnc.mavenmanipulator.io.resolver;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.repository.MirrorSelector;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Profile;
import org.apache.maven.settings.Repository;
import org.apache.maven.settings.RepositoryPolicy;
import org.apache.maven.settings.Settings;
import org.commonjava.atlas.maven.ident.util.JoinString;
import org.commonjava.maven.galley.TransferException;
import org.commonjava.maven.galley.model.ConcreteResource;
import org.commonjava.maven.galley.model.Location;
import org.commonjava.maven.galley.model.Resource;
import org.commonjava.maven.galley.model.SimpleLocation;
import org.commonjava.maven.galley.model.VirtualResource;
import org.commonjava.maven.galley.spi.transport.LocationExpander;
import org.commonjava.maven.galley.transport.htcli.model.SimpleHttpLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Galley {@link LocationExpander} implementation that expands a shorthand URI
 * given in the betterdep goals into the actual list of locations to check for
 * artifacts.
 *
 * @author jdcasey
 */
public class MavenLocationExpander
        implements LocationExpander {

    public static final Location EXPANSION_TARGET = new SimpleLocation("maven:repositories");

    static {
        EXPANSION_TARGET.setAttribute(Location.CONNECTION_TIMEOUT_SECONDS, 60);
    }

    private final List<Location> locations;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public MavenLocationExpander(
            final List<Location> customLocations,
            final List<ArtifactRepository> artifactRepositories,
            final ArtifactRepository localRepository,
            final MirrorSelector mirrorSelector,
            final Settings settings,
            final List<String> activeProfiles)
            throws MalformedURLException {
        final Set<Location> locs = new LinkedHashSet<>();

        if (localRepository != null) {
            locs.add(
                    new SimpleLocation(
                            localRepository.getId(),
                            new File(localRepository.getBasedir()).toURI()
                                    .toString()));
        }

        if (customLocations != null) {
            locs.addAll(customLocations);
        }

        addSettingsProfileRepositoriesTo(locs, settings, activeProfiles, mirrorSelector);
        addRequestRepositoriesTo(locs, artifactRepositories, settings, mirrorSelector);

        if (logger.isDebugEnabled() && !locs.isEmpty()) {
            logger.debug(
                    "Configured to use Maven locations:{}  {}",
                    System.lineSeparator(),
                    new JoinString(System.lineSeparator() + "  ", locs));
        }
        this.locations = new ArrayList<>(locs);
    }

    private void addRequestRepositoriesTo(
            final Set<Location> locs,
            final List<ArtifactRepository> artifactRepositories,
            final Settings settings,
            final MirrorSelector mirrorSelector)
            throws MalformedURLException {
        if (artifactRepositories != null) {
            for (final ArtifactRepository repo : artifactRepositories) {
                // TODO: Authentication via memory password manager.
                String id = repo.getId();
                String url = repo.getUrl();

                if (url.startsWith("file:")) {
                    locs.add(new SimpleLocation(id, url));
                } else {
                    final List<Mirror> mirrors = settings.getMirrors(); // Settings::getMirrors never returns null
                    final Mirror mirror = mirrorSelector == null ? null : mirrorSelector.getMirror(repo, mirrors);
                    if (mirror != null) {
                        id = mirror.getId();
                        url = mirror.getUrl();
                    }

                    final ArtifactRepositoryPolicy releases = repo.getReleases();
                    final ArtifactRepositoryPolicy snapshots = repo.getSnapshots();

                    SimpleHttpLocation addition = new SimpleHttpLocation(
                            id,
                            url,
                            snapshots != null && snapshots.isEnabled(),
                            releases == null || releases.isEnabled(),
                            true,
                            false,
                            null);

                    addition.setAttribute(Location.CONNECTION_TIMEOUT_SECONDS, 60);

                    locs.add(addition);
                }
            }
        }
    }

    private void addSettingsProfileRepositoriesTo(
            final Set<Location> locs,
            final Settings settings,
            final List<String> activeProfiles,
            final MirrorSelector mirrorSelector)
            throws MalformedURLException {
        if (settings != null) {
            final Map<String, Profile> profiles = settings.getProfilesAsMap(); // Settings::getProfilesAsMap never returns null
            if (activeProfiles != null && !activeProfiles.isEmpty()) {
                final LinkedHashSet<String> active = new LinkedHashSet<>(activeProfiles);

                final List<String> settingsActiveProfiles = settings.getActiveProfiles(); // Settings::getActiveProfiles never returns null
                if (!settingsActiveProfiles.isEmpty()) {
                    active.addAll(settingsActiveProfiles);
                }

                for (final String profileId : active) {
                    final Profile profile = profiles.get(profileId);
                    if (profile != null) {
                        final List<Repository> repositories = profile.getRepositories(); // Profile::getRepositories never returns null
                        final List<Mirror> mirrors = settings.getMirrors(); // Settings::getMirrors never returns null
                        final ArtifactRepositoryLayout layout = new DefaultRepositoryLayout();
                        for (final Repository repo : repositories) {
                            String id = repo.getId();
                            String url = repo.getUrl();

                            final ArtifactRepositoryPolicy snapshots = convertPolicy(repo.getSnapshots());
                            final ArtifactRepositoryPolicy releases = convertPolicy(repo.getReleases());

                            final MavenArtifactRepository arepo = new MavenArtifactRepository(
                                    id,
                                    url,
                                    layout,
                                    snapshots,
                                    releases);

                            final Mirror mirror = mirrorSelector == null ? null
                                    : mirrorSelector.getMirror(arepo, mirrors);

                            if (mirror != null) {
                                id = mirror.getId();
                                url = mirror.getUrl();
                            }

                            SimpleHttpLocation addition = new SimpleHttpLocation(
                                    id,
                                    url,
                                    snapshots.isEnabled(),
                                    releases.isEnabled(),
                                    true,
                                    false,
                                    null);

                            addition.setAttribute(Location.CONNECTION_TIMEOUT_SECONDS, 60);

                            locs.add(addition);
                        }
                    }
                }
            }
        }
    }

    private ArtifactRepositoryPolicy convertPolicy(final RepositoryPolicy policy) {
        if (policy == null) {
            return new ArtifactRepositoryPolicy();
        }

        return new ArtifactRepositoryPolicy(policy.isEnabled(), policy.getUpdatePolicy(), policy.getChecksumPolicy());
    }

    @Override
    public List<Location> expand(final Location... locations)
            throws TransferException {
        final List<Location> result = new ArrayList<>();
        for (final Location loc : locations) {
            expandSingle(loc, result);
        }

        if (logger.isDebugEnabled()) {
            logger.debug(
                    "Expanded to:{} {}",
                    System.lineSeparator(),
                    new JoinString(System.lineSeparator() + "  ", result));
        }
        return result;
    }

    @Override
    public <T extends Location> List<Location> expand(final Collection<T> locations)
            throws TransferException {
        final List<Location> result = new ArrayList<>();
        for (final Location loc : locations) {
            expandSingle(loc, result);
        }

        if (logger.isDebugEnabled()) {
            logger.debug(
                    "Expanded to:{} {}",
                    System.lineSeparator(),
                    new JoinString(System.lineSeparator() + "  ", result));
        }

        return result;
    }

    @Override
    public VirtualResource expand(final Resource resource)
            throws TransferException {
        final List<ConcreteResource> result = new ArrayList<>();
        if (resource instanceof ConcreteResource) {
            expandSingle((ConcreteResource) resource, result);
        } else {
            for (final ConcreteResource cr : ((VirtualResource) resource).toConcreteResources()) {
                expandSingle(cr, result);
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug(
                    "Expanded to:{} {}",
                    System.lineSeparator(),
                    new JoinString(System.lineSeparator() + "  ", result));
        }
        return new VirtualResource(result);
    }

    private void expandSingle(final ConcreteResource cr, final List<ConcreteResource> result) {
        final Location loc = cr.getLocation();
        final List<Location> expanded = new ArrayList<>();
        expandSingle(loc, expanded);

        final String path = cr.getPath();
        for (final Location location : expanded) {
            result.add(new ConcreteResource(location, path));
        }
    }

    private void expandSingle(final Location loc, final List<Location> result) {
        logger.debug("Expanding: {}", loc);
        if (EXPANSION_TARGET.equals(loc)) {
            logger.debug("Expanding...");
            result.addAll(this.locations);
        } else {
            logger.debug("No expansion available.");
            result.add(loc);
        }
    }

}
