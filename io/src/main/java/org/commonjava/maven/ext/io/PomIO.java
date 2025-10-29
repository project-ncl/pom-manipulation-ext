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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.release.ReleaseExecutionException;
import org.apache.maven.shared.release.config.ReleaseDescriptorBuilder;
import org.apache.maven.shared.release.config.ReleaseUtils;
import org.apache.maven.shared.release.transform.ModelETL;
import org.apache.maven.shared.release.transform.ModelETLRequest;
import org.apache.maven.shared.release.transform.jdom2.JDomModelETL;
import org.apache.maven.shared.release.transform.jdom2.JDomModelETLFactory;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.jdom.JDOMModelConverter;
import org.commonjava.maven.ext.common.model.Project;
import org.commonjava.maven.ext.common.session.MavenSessionHandler;
import org.commonjava.maven.ext.common.util.LineSeparator;
import org.commonjava.maven.ext.common.util.ManifestUtils;
import org.commonjava.maven.galley.maven.parse.PomPeek;
import org.jdom2.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class used to read raw models for POMs, and rewrite any project POMs that were changed.
 *
 * @author jdcasey
 */
@Named
@Singleton
public class PomIO {
    // TODO: Remove this if no side affects reported in 2022.
    public static final String PARSE_POM_TEMPLATES = "parsePomTemplates";

    private static final String MODIFIED_BY = "Modified by POM Manipulation Extension for Maven";

    private static final Logger logger = LoggerFactory.getLogger(PomIO.class);

    private final JDomModelETLFactory modelETLFactories = new JDomModelETLFactory();
    private final ReleaseDescriptorBuilder releaseDescriptorBuilder = new ReleaseDescriptorBuilder();

    private final JDOMModelConverter jdomModelConverter = new JDOMModelConverter();

    private final boolean parsePomTemplates;

    private String manifestComment;

    @Inject
    public PomIO(MavenSessionHandler handler) {
        parsePomTemplates = Boolean.parseBoolean(
                handler.getUserProperties().getProperty(PARSE_POM_TEMPLATES, "true"));
    }

    // Test use only.
    public PomIO() {
        parsePomTemplates = true;
    }

    public List<Project> parseProject(final File pom) throws ManipulationException {
        final List<PomPeek> peeked = peekAtPomHierarchy(pom);
        try {
            return readModelsForManipulation(pom.getCanonicalFile(), peeked);
        } catch (IOException e) {
            throw new ManipulationException("Error getting canonical file", e);
        }
    }

    /**
     * Read {@link Model} instances by parsing the POM directly. This is useful to escape some post-processing that
     * happens when the
     * {@link MavenProject#getOriginalModel()} instance is set.
     *
     * @param executionRoot the top level pom file.
     * @param peeked a collection of poms resolved from the top level file.
     * @return a collection of Projects
     * @throws ManipulationException if an error occurs.
     */
    private List<Project> readModelsForManipulation(File executionRoot, final List<PomPeek> peeked)
            throws ManipulationException {
        final List<Project> projects = new ArrayList<>();
        final HashMap<Project, ProjectVersionRef> projectToParent = new HashMap<>();

        for (final PomPeek peek : peeked) {
            final File pom = peek.getPom();

            // Sucks, but we have to brute-force reading in the raw model.
            // The effective-model building, below, has a tantalizing getRawModel()
            // method on the result, BUT this seems to return models that have
            // the plugin versions set inside profiles...so they're not entirely
            // raw.
            Model raw;
            try (InputStream in = Files.newInputStream(pom.toPath())) {
                raw = new MavenXpp3Reader().read(in);
            } catch (final IOException | XmlPullParserException e) {
                throw new ManipulationException("Failed to build model for POM: ({}) : {}", pom, e.getMessage(), e);
            }

            if (raw == null) {
                continue;
            }

            final Project project = new Project(pom, raw);
            projectToParent.put(project, peek.getParentKey());
            project.setInheritanceRoot(peek.isInheritanceRoot());

            if (executionRoot.equals(pom)) {

                if (logger.isDebugEnabled()) {
                    final String s = project.isInheritanceRoot() ? " and is the inheritance root. " : "";
                    logger.debug("Setting execution root to {} with file {}{}", project, pom, s);
                }

                project.setExecutionRoot();

                try {
                    if (FileUtils.readFileToString(pom, StandardCharsets.UTF_8).contains(MODIFIED_BY)) {
                        project.setIncrementalPME(true);
                    }
                } catch (final IOException e) {
                    throw new ManipulationException("Failed to read POM: {}", pom, e);
                }
            }

            projects.add(project);
        }

        // Fill out inheritance info for every project we have created.
        for (Project p : projects) {
            ProjectVersionRef pvr = projectToParent.get(p);
            p.setProjectParent(getParent(projects, pvr));
        }

        return projects;
    }

    private Project getParent(List<Project> projects, ProjectVersionRef pvr) {
        for (Project p : projects) {
            if (p.getKey().equals(pvr)) {
                return p;
            }
        }
        // If the PVR refers to something outside of the hierarchy we'll break the inheritance here.
        return null;
    }

    /**
     * For any project listed as changed (tracked by GA in the session), write the modified model out to disk.
     * Uses {@link ModelETL} to preserve as much formatting as possible.
     *
     * @param changed the modified Projects to write out.
     * @throws ManipulationException if an error occurs.
     */
    public void rewritePOMs(final Set<Project> changed)
            throws ManipulationException {
        manifestComment = "Modified by POM Manipulation Extension for Maven "
                + ManifestUtils.getManifestInformation(PomIO.class);

        for (final Project project : changed) {
            if (logger.isDebugEnabled()) {
                logger.debug("{} modified! Rewriting.", project);
            }

            File pom = project.getPom();

            final Model model = project.getModel();

            logger.trace(
                    "Rewriting: {} in place of: {}{}       to POM: {}",
                    model.getId(),
                    project.getKey(),
                    System.lineSeparator(),
                    pom);

            write(project, pom, model);

            // this happens with integration tests!
            // This is a total hack, but the alternative seems to be adding complexity through a custom model processor.
            if (pom.getName()
                    .equals("interpolated-pom.xml")) {
                final File dir = pom.getParentFile();
                pom = dir == null ? new File("pom.xml") : new File(dir, "pom.xml");

                write(project, pom, model);
            }
        }
    }

    /**
     * Writes out the Model to the selected target file.
     *
     * @param model the Model to write out.
     * @param target the file to write to.
     * @throws ManipulationException if an error occurs.
     */
    public void writeModel(final Model model, final File target)
            throws ManipulationException {
        try {
            new MavenXpp3Writer().write(new FileWriter(target), model);
        } catch (IOException e) {
            throw new ManipulationException("Unable to write file", e);
        }
    }

    private void write(final Project project, final File pom, final Model model)
            throws ManipulationException {
        try {
            // We possibly could store the EOL type in the Project when we first read
            // the file but we would then have to do a dual read, then write as opposed
            // to a read, then read + write now.
            LineSeparator ls = FileIO.determineEOL(pom);

            MavenProject mp = new MavenProject(model);
            ModelETLRequest request = new ModelETLRequest();
            request.setLineSeparator(ls.value());
            request.setProject(mp);
            request.setReleaseDescriptor(ReleaseUtils.buildReleaseDescriptor(releaseDescriptorBuilder));

            ModelETL etl = modelETLFactories.newInstance(request);

            // Reread in order to fill in JdomModelETL
            etl.extract(pom);

            // Annoyingly the document is private but we need to access it in order to ensure the model is written to the Document.
            //
            // Currently the fields we want to access are private - https://issues.apache.org/jira/browse/MRELEASE-1044 requests
            // them to be protected to avoid this reflection.
            Document doc = (Document) FieldUtils.getDeclaredField(JDomModelETL.class, "document", true).get(etl);

            jdomModelConverter.convertModelToJDOM(model, doc);

            if (project.isExecutionRoot()) {
                // Previously it was possible to add a comment outside of the root element (which maven3-model-jdom-support handled)
                // but the release plugin code only takes account of code within the root element and everything else is handled separately.
                //
                String outtro = (String) FieldUtils.getDeclaredField(JDomModelETL.class, "outtro", true).get(etl);

                String commentStart = ls.value() +
                        "<!--" +
                        ls.value();
                String commentEnd = ls.value() +
                        "-->" +
                        ls.value();

                if (outtro.equals(ls.value())) {
                    logger.debug("Outtro contains newlines only");

                    outtro = commentStart + manifestComment + commentEnd;
                } else {
                    outtro = outtro.replaceAll("Modified by.*", manifestComment);
                }
                FieldUtils.writeDeclaredField(
                        etl,
                        "outtro",
                        outtro,
                        true);
            }

            etl.load(pom);
        } catch (ReleaseExecutionException | IllegalAccessException e) {
            throw new ManipulationException("Failed to parse POM for rewrite: {}. Reason: ", pom, e.getMessage(), e);
        }
    }

    private List<PomPeek> peekAtPomHierarchy(final File topPom)
            throws ManipulationException {
        final List<PomPeek> peeked = new ArrayList<>();

        try {
            final LinkedList<File> pendingPoms = new LinkedList<>();
            pendingPoms.add(topPom.getCanonicalFile());

            final String topDir = topPom.getCanonicalFile().getParentFile().getCanonicalPath();

            final Set<File> seen = new HashSet<>();

            File topLevelParent = topPom;

            while (!pendingPoms.isEmpty()) {
                final File pom = pendingPoms.removeFirst();
                seen.add(pom);

                logger.debug("PEEK: {}", pom);

                final PomPeek peek = new PomPeek(pom);

                // Deprecated : we now default to scanning every XML file even templated
                // ones but the if block provides a fallback if there are issues.
                //
                // Effectively either parse_pom_templates [default to true] ||
                //      parse_pom_templates overridden to false so key MUST be NOT null
                if (parsePomTemplates || peek.getKey() != null) {
                    peeked.add(peek);

                    final File dir = pom.getParentFile();

                    final String relPath = peek.getParentRelativePath();
                    if (relPath != null) {
                        logger.debug("Found parent relativePath: {} in pom: {}", relPath, pom);

                        File parent = new File(dir, relPath);
                        if (parent.isDirectory()) {
                            parent = new File(parent, "pom.xml");
                        }

                        parent = parent.getCanonicalFile();
                        if (parent.getParentFile()
                                .getCanonicalPath()
                                .startsWith(topDir) && parent.exists() && !seen.contains(parent)
                                && !pendingPoms.contains(parent)) {
                            topLevelParent = parent;

                            logger.debug("Possible top-level parent {}", parent);
                            pendingPoms.add(parent);
                        } else {
                            logger.debug(
                                    "Skipping reference to non-existent parent relativePath: '{}' in: {}",
                                    relPath,
                                    pom);
                        }
                    }

                    final Set<String> modules = peek.getModules();
                    if (modules != null && !modules.isEmpty()) {
                        for (final String module : modules) {
                            if (logger.isDebugEnabled()) {
                                logger.debug("Found module: {} in pom: {}", module, pom);
                            }

                            File modPom = new File(dir, module);
                            if (modPom.isDirectory()) {
                                modPom = new File(modPom, "pom.xml");
                            }

                            if (modPom.exists() && !seen.contains(modPom)
                                    && !pendingPoms.contains(modPom)) {
                                pendingPoms.addLast(modPom);
                            } else {
                                logger.debug("Skipping reference to non-existent module: '{}' in: {}", module, pom);
                            }
                        }
                    }
                } else {
                    logger.debug("Skipping {} as its a template file.", pom);
                }
            }

            final HashSet<ProjectVersionRef> projectrefs = new HashSet<>();

            for (final PomPeek p : peeked) {
                if (p.getKey() != null) {
                    projectrefs.add(p.getKey());
                }
                if (p.getPom().equals(topLevelParent)) {
                    logger.debug("Setting top level parent to {} :: {}", p.getPom(), p.getKey());
                    p.setInheritanceRoot(true);
                }
            }

            for (final PomPeek p : peeked) {
                if (p.getParentKey() == null ||
                        !seenThisParent(projectrefs, p.getParentKey())) {

                    logger.debug("Found a standalone pom {} :: {}", p.getPom(), p.getKey());

                    p.setInheritanceRoot(true);
                }
            }
        } catch (final IOException e) {
            throw new ManipulationException("Problem peeking at POMs.", e);
        }

        return peeked;
    }

    /**
     * Search the list of project references to establish if this parent reference exists in them. This
     * determines whether the module is inheriting something inside the project or an external reference.
     * 
     * @param projectrefs GAVs to search
     * @param parentKey Key to find
     * @return whether its been found
     */
    private boolean seenThisParent(final HashSet<ProjectVersionRef> projectrefs, final ProjectVersionRef parentKey) {
        for (final ProjectVersionRef p : projectrefs) {
            if (p.versionlessEquals(parentKey)) {
                return true;
            }
        }
        return false;
    }
}
