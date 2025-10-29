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

import static org.commonjava.maven.ext.core.util.IdUtils.ga;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.model.Project;
import org.commonjava.maven.ext.core.ManipulationSession;
import org.commonjava.maven.ext.core.state.BOMInjectingState;
import org.commonjava.maven.ext.io.PomIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple manipulator that will look for all module artifacts and construct a BOM that is deployed with the root
 * artifact. It has a predictable naming
 * scheme making it useful in automated scenarios. Configuration is documented in {@link BOMInjectingState}.
 */
@Named("bom-builder")
@Singleton
public class BOMBuilderManipulator
        implements Manipulator {
    private static final String POM_DEPLOYER_GID = "org.goots.maven.plugins";

    private static final String POM_DEPLOYER_AID = "pom-deployer-maven-plugin";

    private static final String POM_DEPLOYER_VID = "1.2";

    private static final String POM_DEPLOYER_COORD = ga(POM_DEPLOYER_GID, POM_DEPLOYER_AID);

    private static final String IDBOM = "pme-bom";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private PomIO pomIO;

    private ManipulationSession session;

    @Inject
    public BOMBuilderManipulator(PomIO pomIO) {
        this.pomIO = pomIO;
    }

    @Override
    public void init(final ManipulationSession session) {
        this.session = session;
        session.setState(new BOMInjectingState(session.getUserProperties()));
    }

    /**
     * If enabled, grab the execution root pom (which will be the topmost POM in terms of directory structure). Within
     * that
     * handle the manipulation of the bom injection.
     */
    @Override
    public Set<Project> applyChanges(final List<Project> projects)
            throws ManipulationException {
        final BOMInjectingState state = session.getState(BOMInjectingState.class);

        if (!session.isEnabled() || !state.isEnabled()) {
            logger.debug("{}: Nothing to do!", getClass().getSimpleName());
            return Collections.emptySet();
        }

        List<Dependency> projectArtifacts = getArtifacts(projects);

        for (final Project project : projects) {
            if (project.isExecutionRoot()) {
                logger.info("Examining {} to add BOM generation.", project);

                final Model model = project.getModel();

                Build build = model.getBuild();
                if (build == null) {
                    build = new Build();
                    model.setBuild(build);
                }

                Model bomModel = createModel(project, IDBOM);
                bomModel.setDescription("PME Generated BOM for other projects to use to align to.");
                DependencyManagement dm = new DependencyManagement();
                dm.setDependencies(projectArtifacts);
                bomModel.setDependencyManagement(dm);

                // Write new bom back out.
                File pmebom = new File(session.getTargetDir(), IDBOM + ".xml");
                session.getTargetDir().mkdir();
                pomIO.writeModel(bomModel, pmebom);

                final Map<String, Plugin> pluginMap = build.getPluginsAsMap();

                if (!pluginMap.containsKey(POM_DEPLOYER_COORD)) {
                    final PluginExecution execution = new PluginExecution();
                    execution.setId(IDBOM);
                    execution.setPhase("install");
                    execution.setGoals(Collections.singletonList("add-pom"));

                    final Plugin plugin = new Plugin();
                    plugin.setGroupId(POM_DEPLOYER_GID);
                    plugin.setArtifactId(POM_DEPLOYER_AID);
                    plugin.setVersion(POM_DEPLOYER_VID);
                    plugin.addExecution(execution);
                    plugin.setInherited(false);

                    build.addPlugin(plugin);

                    final Xpp3Dom xml = new Xpp3Dom("configuration");

                    final Map<String, Object> config = new HashMap<>();
                    config.put("pomName", "target" + File.separatorChar + pmebom.getName());
                    config.put("errorOnMissing", false);
                    config.put("artifactId", bomModel.getArtifactId());
                    config.put("groupId", bomModel.getGroupId());

                    for (final Map.Entry<String, Object> entry : config.entrySet()) {
                        final Xpp3Dom child = new Xpp3Dom(entry.getKey());
                        if (entry.getValue() != null) {
                            child.setValue(entry.getValue().toString());
                        }

                        xml.addChild(child);
                    }

                    execution.setConfiguration(xml);
                }

                return Collections.singleton(project);
            }
        }

        return Collections.emptySet();
    }

    private Model createModel(Project project, String s) {
        final Model newModel = new Model();
        newModel.setModelVersion(project.getModel().getModelVersion());

        newModel.setGroupId(project.getGroupId() + '.' + project.getArtifactId());
        newModel.setArtifactId(s);
        newModel.setVersion(project.getVersion());
        newModel.setPackaging("pom");

        return newModel;
    }

    // TODO: This will grab every module ; so those modules activated under profiles will also get included
    private List<Dependency> getArtifacts(List<Project> projects) {
        List<Dependency> results = new ArrayList<>();

        for (Project p : projects) {
            Dependency d = new Dependency();
            d.setGroupId(p.getGroupId());
            d.setArtifactId(p.getArtifactId());
            d.setVersion(p.getVersion());
            d.setType(p.getModel().getPackaging());
            results.add(d);
        }
        return results;
    }

    @Override
    public int getExecutionIndex() {
        return 80;
    }

}
