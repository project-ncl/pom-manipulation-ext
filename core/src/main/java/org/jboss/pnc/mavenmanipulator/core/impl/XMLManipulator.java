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

import static org.apache.commons.lang3.StringUtils.isEmpty;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;

import org.jboss.pnc.mavenmanipulator.common.ManipulationException;
import org.jboss.pnc.mavenmanipulator.common.model.Project;
import org.jboss.pnc.mavenmanipulator.core.ManipulationSession;
import org.jboss.pnc.mavenmanipulator.core.state.XMLState;
import org.jboss.pnc.mavenmanipulator.io.XMLIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * {@link Manipulator} implementation that can modify XML files. Configuration
 * is stored in a {@link XMLState} instance, which is in turn stored in the {@link ManipulationSession}.
 */
@Named("xml-manipulator")
@Singleton
public class XMLManipulator
        implements Manipulator {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final XPath xPath;

    private final XMLIO xmlIO;

    private ManipulationSession session;

    @Inject
    public XMLManipulator(XMLIO xmlIO) {
        this.xmlIO = xmlIO;
        this.xPath = xmlIO.getXPath();
    }

    /**
     * Initialize the {@link XMLState} state holder in the {@link ManipulationSession}. This state holder detects
     * configuration from the Maven user properties (-D properties from the CLI) and makes it available for
     * later.
     */
    @Override
    public void init(final ManipulationSession session)
            throws ManipulationException {
        this.session = session;
        session.setState(new XMLState(session.getUserProperties()));
    }

    /**
     * Apply the xml changes to the specified file(s).
     */
    @Override
    public Set<Project> applyChanges(final List<Project> projects)
            throws ManipulationException {
        final XMLState state = session.getState(XMLState.class);
        if (!session.isEnabled() || !state.isEnabled()) {
            logger.debug("{}: Nothing to do!", getClass().getSimpleName());
            return Collections.emptySet();
        }

        final Set<Project> changed = new HashSet<>();
        final List<XMLState.XMLOperation> scripts = state.getXMLOperations();

        for (final Project project : projects) {
            if (project.isExecutionRoot()) {
                for (XMLState.XMLOperation operation : scripts) {
                    internalApplyChanges(project, operation);

                    changed.add(project);
                }

                break;
            }
        }
        return changed;
    }

    void internalApplyChanges(Project project, XMLState.XMLOperation operation) throws ManipulationException {
        File target = new File(project.getPom().getParentFile(), operation.getFile());

        logger.info(
                "Attempting to start XML update to file {} with xpath {} and replacement {}",
                target,
                operation.getXPath(),
                operation.getUpdate());

        Document doc = xmlIO.parseXML(target);

        try {
            NodeList nodeList = (NodeList) xPath.evaluate(operation.getXPath(), doc, XPathConstants.NODESET);

            if (nodeList.getLength() == 0) {
                if (project.isIncrementalPME()) {
                    logger.warn("Did not locate XML using XPath {}", operation.getXPath());
                    return;
                } else {
                    logger.error(
                            "XPath {} did not find any expressions within {}",
                            operation.getXPath(),
                            operation.getFile());
                    throw new ManipulationException("Did not locate XML using XPath {}", operation.getXPath());
                }
            }

            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);

                if (isEmpty(operation.getUpdate())) {
                    // Delete
                    node.getParentNode().removeChild(node);
                } else {
                    // Update
                    node.setTextContent(operation.getUpdate());
                }
            }

            xmlIO.writeXML(target, doc);
        } catch (XPathExpressionException e) {
            logger.error("Caught XML exception processing file {}, document context {}", target, doc, e);
            throw new ManipulationException("Caught XML exception processing file", e);
        }
    }

    @Override
    public int getExecutionIndex() {
        return 91;
    }
}
