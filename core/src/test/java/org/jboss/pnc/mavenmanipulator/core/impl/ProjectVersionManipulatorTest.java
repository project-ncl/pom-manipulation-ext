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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.jboss.pnc.mavenmanipulator.core.fixture.TestUtils.createSession;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Profile;
import org.apache.maven.project.MavenProject;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectVersionRef;
import org.jboss.pnc.mavenmanipulator.common.ManipulationException;
import org.jboss.pnc.mavenmanipulator.common.model.Project;
import org.jboss.pnc.mavenmanipulator.common.util.ProfileUtils;
import org.jboss.pnc.mavenmanipulator.core.ManipulationSession;
import org.jboss.pnc.mavenmanipulator.core.state.VersioningState;
import org.jboss.pnc.mavenmanipulator.io.resolver.GalleyAPIWrapper;
import org.jboss.pnc.mavenmanipulator.io.resolver.GalleyInfrastructure;
import org.jboss.pnc.mavenmanipulator.io.rest.handler.AddSuffixJettyHandler;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProjectVersionManipulatorTest {
    @Test
    public void updateEffectiveAndOriginalModelMainVersions()
            throws Exception {
        final Model orig = new Model();
        orig.setGroupId("org.foo");
        orig.setArtifactId("bar");
        orig.setVersion("1.0");

        final Model eff = orig.clone();

        final String suff = AddSuffixJettyHandler.DEFAULT_SUFFIX;
        final String mv = orig.getVersion() + "." + suff;

        final Map<ProjectVersionRef, String> versionsByGAV = new HashMap<>();
        versionsByGAV.put(new SimpleProjectVersionRef(orig.getGroupId(), orig.getArtifactId(), orig.getVersion()), mv);

        final MavenProject project = new MavenProject(eff);
        project.setOriginalModel(orig);

        final Set<MavenProject> changes = newVersioningModifier()
                .applyVersioningChanges(Collections.singletonList(project), versionsByGAV);

        assertThat(changes.size(), equalTo(1));
        assertThat(orig.getVersion(), equalTo(mv));
        assertThat(eff.getVersion(), equalTo(mv));
    }

    @Test
    public void updateEffectiveAndOriginalModelParentVersions()
            throws Exception {
        final Model parent = new Model();
        parent.setGroupId("org.foo");
        parent.setArtifactId("bar-parent");
        parent.setVersion("1.0");

        final Model orig = new Model();
        orig.setArtifactId("bar");

        final Parent origParent = new Parent();
        origParent.setGroupId(parent.getGroupId());
        origParent.setArtifactId(parent.getArtifactId());
        origParent.setVersion(parent.getVersion());

        orig.setParent(origParent);

        final Model eff = orig.clone();

        final String suff = AddSuffixJettyHandler.DEFAULT_SUFFIX;
        final String mv = orig.getVersion() + "." + suff;

        final Map<ProjectVersionRef, String> versionsByGA = new HashMap<>();
        // Not putting original group/artifact/version as they are group & version are null which causes problems with ProjectVersionRef
        versionsByGA.put(
                new SimpleProjectVersionRef(
                        origParent.getGroupId(),
                        origParent.getArtifactId(),
                        origParent.getVersion()),
                mv);

        final List<MavenProject> projects = new ArrayList<>();

        MavenProject project = new MavenProject(parent.clone());
        project.setOriginalModel(parent);
        projects.add(project);

        project = new MavenProject(eff);
        project.setOriginalModel(orig);
        projects.add(project);

        final Set<MavenProject> changes = newVersioningModifier().applyVersioningChanges(projects, versionsByGA);

        assertThat(changes.size(), equalTo(2));
        for (final MavenProject p : changes) {
            if (p.getArtifactId()
                    .equals("bar")) {
                assertThat(
                        p.getOriginalModel()
                                .getParent()
                                .getVersion(),
                        equalTo(mv));
                assertThat(
                        p.getModel()
                                .getParent()
                                .getVersion(),
                        equalTo(mv));
                assertThat(p.getOriginalModel().getVersion(), nullValue());
                assertThat(
                        p.getModel()
                                .getVersion(),
                        nullValue());
            } else {
                assertThat(
                        p.getOriginalModel()
                                .getVersion(),
                        equalTo(mv));
                assertThat(
                        p.getModel()
                                .getVersion(),
                        equalTo(mv));
            }
        }
    }

    @Test
    public void updateEffectiveAndOriginalModelDependencyVersions()
            throws Exception {
        final Model orig = new Model();
        orig.setGroupId("org.foo");
        orig.setArtifactId("bar");
        orig.setVersion("1.0");

        final Model depModel = new Model();
        depModel.setGroupId("org.foo");
        depModel.setArtifactId("bar-dep");
        depModel.setVersion("1.0");

        final Dependency dep = new Dependency();
        dep.setGroupId(depModel.getGroupId());
        dep.setArtifactId(depModel.getArtifactId());
        dep.setVersion(depModel.getVersion());

        orig.addDependency(dep);

        final DependencyManagement mgmt = new DependencyManagement();

        final Model dmModel = new Model();
        dmModel.setGroupId("org.foo");
        dmModel.setArtifactId("bar-managed-dep");
        dmModel.setVersion("1.0");

        final Dependency managed = new Dependency();
        managed.setGroupId(dmModel.getGroupId());
        managed.setArtifactId(dmModel.getArtifactId());
        managed.setVersion(dmModel.getVersion());

        mgmt.addDependency(managed);
        orig.setDependencyManagement(mgmt);

        final String suff = AddSuffixJettyHandler.DEFAULT_SUFFIX;
        final String mv = orig.getVersion() + "." + suff;

        final Map<ProjectVersionRef, String> versionsByGA = new HashMap<>();
        versionsByGA.put(new SimpleProjectVersionRef(orig.getGroupId(), orig.getArtifactId(), orig.getVersion()), mv);
        versionsByGA.put(
                new SimpleProjectVersionRef(depModel.getGroupId(), depModel.getArtifactId(), depModel.getVersion()),
                mv);
        versionsByGA.put(
                new SimpleProjectVersionRef(dmModel.getGroupId(), dmModel.getArtifactId(), dmModel.getVersion()),
                mv);

        final List<MavenProject> projects = new ArrayList<>();

        MavenProject project = new MavenProject(depModel.clone());
        project.setOriginalModel(depModel);
        projects.add(project);

        project = new MavenProject(dmModel.clone());
        project.setOriginalModel(dmModel);
        projects.add(project);

        project = new MavenProject(orig.clone());
        project.setOriginalModel(orig);
        projects.add(project);

        final Set<MavenProject> changes = newVersioningModifier().applyVersioningChanges(projects, versionsByGA);

        assertThat(changes.size(), equalTo(3));
        for (final MavenProject p : changes) {
            final String a = p.getArtifactId();

            if (a.equals("bar")) {
                assertThat(
                        p.getOriginalModel()
                                .getVersion(),
                        equalTo(mv));
                assertThat(
                        p.getModel()
                                .getVersion(),
                        equalTo(mv));

                List<Dependency> deps = p.getOriginalModel()
                        .getDependencies();
                assertThat(deps.size(), equalTo(1));
                Dependency d = deps.get(0);

                assertThat(d, notNullValue());
                assertThat(d.getVersion(), equalTo(mv));

                deps = p.getModel()
                        .getDependencies();
                assertThat(deps.size(), equalTo(1));
                d = deps.get(0);

                assertThat(d, notNullValue());
                assertThat(d.getVersion(), equalTo(mv));

                deps = p.getOriginalModel()
                        .getDependencyManagement()
                        .getDependencies();
                assertThat(deps.size(), equalTo(1));
                d = deps.get(0);

                assertThat(d, notNullValue());
                assertThat(d.getVersion(), equalTo(mv));

                deps = p.getModel()
                        .getDependencyManagement()
                        .getDependencies();
                assertThat(deps.size(), equalTo(1));
                d = deps.get(0);

                assertThat(d, notNullValue());
                assertThat(d.getVersion(), equalTo(mv));

            } else {
                assertThat(
                        p.getOriginalModel()
                                .getVersion(),
                        equalTo(mv));
                assertThat(
                        p.getModel()
                                .getVersion(),
                        equalTo(mv));
            }
        }
    }

    @Test
    public void updateEffectiveAndOriginalModelDependencyVersions_OnlyWhenHasVersion()
            throws Exception {
        final Model orig = new Model();
        orig.setGroupId("org.foo");
        orig.setArtifactId("bar");
        orig.setVersion("1.0");

        final Model depModel = new Model();
        depModel.setGroupId("org.foo");
        depModel.setArtifactId("bar-dep");
        depModel.setVersion("1.0");

        final Dependency dep = new Dependency();
        dep.setGroupId(depModel.getGroupId());
        dep.setArtifactId(depModel.getArtifactId());

        orig.addDependency(dep);

        final DependencyManagement mgmt = new DependencyManagement();

        final Dependency managed = new Dependency();
        managed.setGroupId(depModel.getGroupId());
        managed.setArtifactId(depModel.getArtifactId());
        managed.setVersion(depModel.getVersion());

        mgmt.addDependency(managed);
        orig.setDependencyManagement(mgmt);

        final String suff = AddSuffixJettyHandler.DEFAULT_SUFFIX;
        final String mv = orig.getVersion() + "." + suff;

        final Map<ProjectVersionRef, String> versionsByGA = new HashMap<>();
        versionsByGA.put(new SimpleProjectVersionRef(orig.getGroupId(), orig.getArtifactId(), orig.getVersion()), mv);
        versionsByGA.put(
                new SimpleProjectVersionRef(depModel.getGroupId(), depModel.getArtifactId(), depModel.getVersion()),
                mv);

        final List<MavenProject> projects = new ArrayList<>();

        MavenProject project = new MavenProject(depModel.clone());
        project.setOriginalModel(depModel);
        projects.add(project);

        project = new MavenProject(orig.clone());
        project.setOriginalModel(orig);
        projects.add(project);

        final Set<MavenProject> changes = newVersioningModifier().applyVersioningChanges(projects, versionsByGA);

        assertThat(changes.size(), equalTo(2));
        for (final MavenProject p : changes) {
            final String a = p.getArtifactId();

            if (a.equals("bar")) {
                assertThat(
                        p.getOriginalModel()
                                .getVersion(),
                        equalTo(mv));
                assertThat(
                        p.getModel()
                                .getVersion(),
                        equalTo(mv));

                List<Dependency> deps = p.getOriginalModel()
                        .getDependencies();
                assertThat(deps.size(), equalTo(1));
                Dependency d = deps.get(0);

                assertThat(d, notNullValue());
                assertThat(d.getVersion(), nullValue());

                deps = p.getModel()
                        .getDependencies();
                assertThat(deps.size(), equalTo(1));
                d = deps.get(0);

                assertThat(d, notNullValue());
                assertThat(d.getVersion(), nullValue());

                deps = p.getOriginalModel()
                        .getDependencyManagement()
                        .getDependencies();
                assertThat(deps.size(), equalTo(1));
                d = deps.get(0);

                assertThat(d, notNullValue());
                assertThat(d.getVersion(), equalTo(mv));

                deps = p.getModel()
                        .getDependencyManagement()
                        .getDependencies();
                assertThat(deps.size(), equalTo(1));
                d = deps.get(0);

                assertThat(d, notNullValue());
                assertThat(d.getVersion(), equalTo(mv));

            } else {
                assertThat(
                        p.getOriginalModel()
                                .getVersion(),
                        equalTo(mv));
                assertThat(
                        p.getModel()
                                .getVersion(),
                        equalTo(mv));
            }
        }
    }

    @Test
    public void updateEffectiveAndOriginalModelDependencyVersions_InProfile()
            throws Exception {
        final Model orig = new Model();
        orig.setGroupId("org.foo");
        orig.setArtifactId("bar");
        orig.setVersion("1.0");

        final Model depModel = new Model();
        depModel.setGroupId("org.foo");
        depModel.setArtifactId("bar-dep");
        depModel.setVersion("1.0");

        final Dependency dep = new Dependency();
        dep.setGroupId(depModel.getGroupId());
        dep.setArtifactId(depModel.getArtifactId());
        dep.setVersion(depModel.getVersion());

        final Profile p = new Profile();
        p.setId("test");
        orig.addProfile(p);

        p.addDependency(dep);

        final DependencyManagement mgmt = new DependencyManagement();

        final Model dmModel = new Model();
        dmModel.setGroupId("org.foo");
        dmModel.setArtifactId("bar-managed-dep");
        dmModel.setVersion("1.0");

        final Dependency managed = new Dependency();
        managed.setGroupId(dmModel.getGroupId());
        managed.setArtifactId(dmModel.getArtifactId());
        managed.setVersion(dmModel.getVersion());

        mgmt.addDependency(managed);
        p.setDependencyManagement(mgmt);

        final String suff = AddSuffixJettyHandler.DEFAULT_SUFFIX;
        final String mv = orig.getVersion() + "." + suff;

        final Map<ProjectVersionRef, String> versionsByGA = new HashMap<>();
        versionsByGA.put(new SimpleProjectVersionRef(orig.getGroupId(), orig.getArtifactId(), orig.getVersion()), mv);
        versionsByGA.put(
                new SimpleProjectVersionRef(depModel.getGroupId(), depModel.getArtifactId(), depModel.getVersion()),
                mv);
        versionsByGA.put(
                new SimpleProjectVersionRef(dmModel.getGroupId(), dmModel.getArtifactId(), dmModel.getVersion()),
                mv);

        final List<MavenProject> projects = new ArrayList<>();

        MavenProject project = new MavenProject(depModel.clone());
        project.setOriginalModel(depModel);
        projects.add(project);

        project = new MavenProject(dmModel.clone());
        project.setOriginalModel(dmModel);
        projects.add(project);

        project = new MavenProject(orig.clone());
        project.setOriginalModel(orig);
        projects.add(project);

        final Set<MavenProject> changes = newVersioningModifier().applyVersioningChanges(projects, versionsByGA);

        assertThat(changes.size(), equalTo(3));
        for (final MavenProject proj : changes) {
            final String a = proj.getArtifactId();

            if (a.equals("bar")) {
                assertThat(
                        proj.getOriginalModel()
                                .getVersion(),
                        equalTo(mv));
                assertThat(
                        proj.getModel()
                                .getVersion(),
                        equalTo(mv));

                final Profile op = proj.getOriginalModel()
                        .getProfiles()
                        .get(0);
                final Profile ep = proj.getModel()
                        .getProfiles()
                        .get(0);

                List<Dependency> deps = op.getDependencies();
                assertThat(deps.size(), equalTo(1));
                Dependency d = deps.get(0);

                assertThat(d, notNullValue());
                assertThat(d.getVersion(), equalTo(mv));

                deps = ep.getDependencies();
                assertThat(deps.size(), equalTo(1));
                d = deps.get(0);

                assertThat(d, notNullValue());
                assertThat(d.getVersion(), equalTo(mv));

                deps = op.getDependencyManagement()
                        .getDependencies();
                assertThat(deps.size(), equalTo(1));
                d = deps.get(0);

                assertThat(d, notNullValue());
                assertThat(d.getVersion(), equalTo(mv));

                deps = ep.getDependencyManagement()
                        .getDependencies();
                assertThat(deps.size(), equalTo(1));
                d = deps.get(0);

                assertThat(d, notNullValue());
                assertThat(d.getVersion(), equalTo(mv));

            } else {
                assertThat(
                        proj.getOriginalModel()
                                .getVersion(),
                        equalTo(mv));
                assertThat(
                        proj.getModel()
                                .getVersion(),
                        equalTo(mv));
            }
        }
    }

    @Test
    public void updateEffectiveAndOriginalModelDependencyVersions_OnlyWhenHasVersion_InProfile()
            throws Exception {
        final Model orig = new Model();
        orig.setGroupId("org.foo");
        orig.setArtifactId("bar");
        orig.setVersion("1.0");

        final Model depModel = new Model();
        depModel.setGroupId("org.foo");
        depModel.setArtifactId("bar-dep");
        depModel.setVersion("1.0");

        final Dependency dep = new Dependency();
        dep.setGroupId(depModel.getGroupId());
        dep.setArtifactId(depModel.getArtifactId());

        final Profile p = new Profile();
        p.setId("test");
        orig.addProfile(p);

        p.addDependency(dep);

        final DependencyManagement mgmt = new DependencyManagement();

        final Dependency managed = new Dependency();
        managed.setGroupId(depModel.getGroupId());
        managed.setArtifactId(depModel.getArtifactId());
        managed.setVersion(depModel.getVersion());

        mgmt.addDependency(managed);
        p.setDependencyManagement(mgmt);

        final String suff = AddSuffixJettyHandler.DEFAULT_SUFFIX;
        final String mv = orig.getVersion() + "." + suff;

        final Map<ProjectVersionRef, String> versionsByGA = new HashMap<>();
        versionsByGA.put(new SimpleProjectVersionRef(orig.getGroupId(), orig.getArtifactId(), orig.getVersion()), mv);
        versionsByGA.put(
                new SimpleProjectVersionRef(depModel.getGroupId(), depModel.getArtifactId(), depModel.getVersion()),
                mv);

        final List<MavenProject> projects = new ArrayList<>();

        MavenProject project = new MavenProject(depModel.clone());
        project.setOriginalModel(depModel);
        projects.add(project);

        project = new MavenProject(orig.clone());
        project.setOriginalModel(orig);
        projects.add(project);

        final Set<MavenProject> changes = newVersioningModifier().applyVersioningChanges(projects, versionsByGA);

        assertThat(changes.size(), equalTo(2));
        for (final MavenProject proj : changes) {
            final String a = proj.getArtifactId();

            if (a.equals("bar")) {
                assertThat(
                        proj.getOriginalModel()
                                .getVersion(),
                        equalTo(mv));
                assertThat(
                        proj.getModel()
                                .getVersion(),
                        equalTo(mv));

                final Profile op = proj.getOriginalModel()
                        .getProfiles()
                        .get(0);
                final Profile ep = proj.getModel()
                        .getProfiles()
                        .get(0);

                List<Dependency> deps = op.getDependencies();
                assertThat(deps.size(), equalTo(1));
                Dependency d = deps.get(0);

                assertThat(d, notNullValue());
                assertThat(d.getVersion(), nullValue());

                deps = ep.getDependencies();
                assertThat(deps.size(), equalTo(1));
                d = deps.get(0);

                assertThat(d, notNullValue());
                assertThat(d.getVersion(), nullValue());

                deps = op.getDependencyManagement()
                        .getDependencies();
                assertThat(deps.size(), equalTo(1));
                d = deps.get(0);

                assertThat(d, notNullValue());
                assertThat(d.getVersion(), equalTo(mv));

                deps = ep.getDependencyManagement()
                        .getDependencies();
                assertThat(deps.size(), equalTo(1));
                d = deps.get(0);

                assertThat(d, notNullValue());
                assertThat(d.getVersion(), equalTo(mv));

            } else {
                assertThat(
                        proj.getOriginalModel()
                                .getVersion(),
                        equalTo(mv));
                assertThat(
                        proj.getModel()
                                .getVersion(),
                        equalTo(mv));
            }
        }
    }

    private TestVersioningModifier newVersioningModifier()
            throws ManipulationException {
        Properties p = new Properties();
        p.setProperty(ProfileUtils.PROFILE_SCANNING, "false");
        return new TestVersioningModifier(createSession(p));
    }

    private static final class TestVersioningModifier
            extends ProjectVersioningManipulator {

        private final Logger logger = LoggerFactory.getLogger(getClass());

        private ManipulationSession session;

        TestVersioningModifier(final ManipulationSession session)
                throws ManipulationException {
            super(new VersionCalculator(new GalleyAPIWrapper(new GalleyInfrastructure(session, null).init())));
            this.session = session;
            init(session);
        }

        Set<MavenProject> applyVersioningChanges(
                final Collection<MavenProject> projects,
                final Map<ProjectVersionRef, String> _versionsByGAV)
                throws ManipulationException {
            final VersioningState state = new VersioningState(session.getUserProperties());
            state.setVersionsByGAVMap(_versionsByGAV);

            final Set<MavenProject> changed = new HashSet<>();
            for (final MavenProject project : projects) {
                if (applyVersioningChanges(new Project(project.getOriginalModel()), state)) {
                    final String v = _versionsByGAV.get(SimpleProjectVersionRef.parse(gav(project)));

                    // this is a bigger model, so only do this if the originalModel was modded.
                    applyVersioningChanges(new Project(project.getModel()), state);
                    changed.add(project);

                    if (v != null) {
                        // belt and suspenders...be double sure this gets set everywhere.
                        project.setVersion(v);
                    }
                }
            }

            return changed;
        }

    }

    // Was in IdUtils but only used here. Use of MavenProject doesn't track Parent group/version
    // but not important for this test
    private static String gav(final MavenProject project) {
        return String.format("%s:%s:%s", project.getGroupId(), project.getArtifactId(), project.getVersion());
    }
}
