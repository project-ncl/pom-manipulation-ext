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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.File;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.repository.DefaultMirrorSelector;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Profile;
import org.apache.maven.settings.Repository;
import org.apache.maven.settings.Settings;
import org.commonjava.maven.galley.model.Location;
import org.junit.Test;

public class MavenLocationExpanderTest {
    @Test
    public void emptyValues()
            throws Exception {
        final MavenLocationExpander ex = new MavenLocationExpander(
                null,
                null,
                null,
                null,
                null,
                null);
        assertThat(ex.expand(Collections.emptyList()), equalTo(Collections.emptyList()));
    }

    @Test
    public void mirrorAdjustsLocationURLs()
            throws Exception {
        final Mirror mirror = new Mirror();
        mirror.setId("test-mirror");
        mirror.setMirrorOf("*");
        mirror.setUrl("http://nowhere.com");

        final ArtifactRepositoryLayout layout = new DefaultRepositoryLayout();

        final ArtifactRepositoryPolicy snapshots = new ArtifactRepositoryPolicy(
                true,
                ArtifactRepositoryPolicy.UPDATE_POLICY_DAILY,
                ArtifactRepositoryPolicy.CHECKSUM_POLICY_WARN);

        final ArtifactRepositoryPolicy releases = new ArtifactRepositoryPolicy(
                true,
                ArtifactRepositoryPolicy.UPDATE_POLICY_NEVER,
                ArtifactRepositoryPolicy.CHECKSUM_POLICY_WARN);

        final File localRepo = File.createTempFile("local.repo.", ".dir");
        localRepo.deleteOnExit();

        final ArtifactRepository local = new MavenArtifactRepository(
                "local",
                localRepo.toURI()
                        .toString(),
                layout,
                snapshots,
                releases);

        final ArtifactRepository remote = new MavenArtifactRepository(
                "remote",
                "http:///repo.maven.apache.org/maven2",
                layout,
                snapshots,
                releases);

        final Settings settings = new Settings();
        settings.addMirror(mirror);

        final MavenLocationExpander ex = new MavenLocationExpander(
                Collections.emptyList(),
                Collections.singletonList(remote),
                local,
                new DefaultMirrorSelector(),
                settings,
                Collections.emptyList());

        final List<Location> result = ex.expand(MavenLocationExpander.EXPANSION_TARGET);

        assertThat(result.size(), equalTo(2));

        final Iterator<Location> iterator = result.iterator();
        Location loc = iterator.next();

        assertThat(loc.getName(), equalTo(local.getId()));
        assertThat(loc.getUri(), equalTo(local.getUrl()));

        loc = iterator.next();

        assertThat(loc.getName(), equalTo(mirror.getId()));
        assertThat(loc.getUri(), equalTo(mirror.getUrl()));
    }

    @Test
    public void useActiveSettingsProfileRepos()
            throws Exception {
        final ArtifactRepositoryLayout layout = new DefaultRepositoryLayout();

        final ArtifactRepositoryPolicy snapshots = new ArtifactRepositoryPolicy(
                true,
                ArtifactRepositoryPolicy.UPDATE_POLICY_DAILY,
                ArtifactRepositoryPolicy.CHECKSUM_POLICY_WARN);

        final ArtifactRepositoryPolicy releases = new ArtifactRepositoryPolicy(
                true,
                ArtifactRepositoryPolicy.UPDATE_POLICY_NEVER,
                ArtifactRepositoryPolicy.CHECKSUM_POLICY_WARN);

        final File localRepo = File.createTempFile("local.repo.", ".dir");
        localRepo.deleteOnExit();

        final ArtifactRepository local = new MavenArtifactRepository(
                "local",
                localRepo.toURI()
                        .toString(),
                layout,
                snapshots,
                releases);

        final Repository remote = new Repository();
        remote.setId("remote");
        remote.setUrl("http:///repo.maven.apache.org/maven2");

        final Profile profile = new Profile();
        profile.setId("test");
        profile.addRepository(remote);

        final Settings settings = new Settings();
        settings.addProfile(profile);

        final MavenLocationExpander ex = new MavenLocationExpander(
                Collections.emptyList(),
                Collections.emptyList(),
                local,
                new DefaultMirrorSelector(),
                settings,
                Collections.singletonList(profile.getId()));

        final List<Location> result = ex.expand(MavenLocationExpander.EXPANSION_TARGET);

        assertThat(result.size(), equalTo(2));

        final Iterator<Location> iterator = result.iterator();
        Location loc = iterator.next();

        assertThat(loc.getName(), equalTo(local.getId()));
        assertThat(loc.getUri(), equalTo(local.getUrl()));

        loc = iterator.next();

        assertThat(loc.getName(), equalTo(remote.getId()));
        assertThat(loc.getUri(), equalTo(remote.getUrl()));
    }

    @Test
    public void locationURLs()
            throws Exception {
        final ArtifactRepositoryLayout layout = new DefaultRepositoryLayout();

        final ArtifactRepositoryPolicy snapshots = new ArtifactRepositoryPolicy(
                true,
                ArtifactRepositoryPolicy.UPDATE_POLICY_DAILY,
                ArtifactRepositoryPolicy.CHECKSUM_POLICY_WARN);

        final ArtifactRepositoryPolicy releases = new ArtifactRepositoryPolicy(
                true,
                ArtifactRepositoryPolicy.UPDATE_POLICY_NEVER,
                ArtifactRepositoryPolicy.CHECKSUM_POLICY_WARN);

        final File localRepo = File.createTempFile("local.repo.", ".dir");
        localRepo.deleteOnExit();

        final ArtifactRepository local = new MavenArtifactRepository(
                "local",
                localRepo.toURI()
                        .toString(),
                layout,
                snapshots,
                releases);

        final ArtifactRepository remote = new MavenArtifactRepository(
                "remote",
                "http:///repo.maven.apache.org/maven2",
                layout,
                snapshots,
                releases);

        final Settings settings = new Settings();

        final MavenLocationExpander ex = new MavenLocationExpander(
                Collections.emptyList(),
                Collections.singletonList(remote),
                local,
                new DefaultMirrorSelector(),
                settings,
                Collections.emptyList());

        final List<Location> result = ex.expand(MavenLocationExpander.EXPANSION_TARGET);

        assertThat(result.size(), equalTo(2));

        final Iterator<Location> iterator = result.iterator();
        Location loc = iterator.next();

        assertThat(loc.getName(), equalTo(local.getId()));
        assertThat(loc.getUri(), equalTo(local.getUrl()));

        loc = iterator.next();

        assertThat(loc.getName(), equalTo(remote.getId()));
        assertThat(loc.getUri(), equalTo(remote.getUrl()));

    }
}
