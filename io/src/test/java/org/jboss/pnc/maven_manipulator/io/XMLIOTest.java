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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.xmlunit.builder.Input.fromFile;
import static org.xmlunit.matchers.HasXPathMatcher.hasXPath;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.FileUtils;
import org.jboss.pnc.maven_manipulator.common.ManipulationException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.DefaultNodeMatcher;
import org.xmlunit.diff.Diff;
import org.xmlunit.diff.ElementSelectors;
import org.xmlunit.matchers.EvaluateXPathMatcher;
import org.xmlunit.xpath.JAXPXPathEngine;

public class XMLIOTest {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private XMLIO xmlIO = new XMLIO();

    private File xmlFile;

    @Rule
    public TemporaryFolder tf = new TemporaryFolder();

    @Before
    public void setup() {
        xmlIO = new XMLIO();
        URL resource = this.getClass().getResource("activemq-artemis-dep.xml");
        xmlFile = new File(resource.getFile());
    }

    @Test
    public void readFile()
            throws ManipulationException {
        Document doc = xmlIO.parseXML(xmlFile);

        String strResult = xmlIO.convert(doc);

        Diff diff = DiffBuilder.compare(fromFile(xmlFile))
                .normalizeWhitespace()
                .withTest(Input.fromString(strResult))
                .build();

        assertFalse(diff.toString(), diff.hasDifferences());
    }

    @Test
    public void writeFile() throws ManipulationException, IOException {
        Document doc = xmlIO.parseXML(xmlFile);

        File target = tf.newFile();

        xmlIO.writeXML(target, doc);

        Diff diff = DiffBuilder.compare(fromFile(xmlFile))
                .normalizeWhitespace()
                .withNodeMatcher(new DefaultNodeMatcher(ElementSelectors.byName))
                .withTest(Input.fromFile(target))
                .build();

        System.out.println(diff.fullDescription());
        assertFalse(diff.toString(), diff.hasDifferences());
    }

    @Test
    public void writeFile2() throws ManipulationException, IOException {
        xmlIO = new XMLIO();
        xmlFile = new File(this.getClass().getResource("azure-file-system.windup.xml").getFile());

        Document doc = xmlIO.parseXML(xmlFile);

        File target = tf.newFile();

        xmlIO.writeXML(target, doc);

        Diff diff = DiffBuilder.compare(fromFile(xmlFile))
                .withNodeMatcher(new DefaultNodeMatcher(ElementSelectors.byName))
                .withTest(Input.fromFile(target))
                .build();

        System.out.println(diff.fullDescription());
        assertFalse(diff.toString(), diff.hasDifferences());
    }

    @Test
    public void modifyFile()
            throws ManipulationException, IOException, XPathExpressionException {
        String updatePath = "/assembly/includeBaseDirectory";
        String newBaseDirectory = "/home/MYNEWBASEDIR";
        Document doc = xmlIO.parseXML(xmlFile);

        XPath xPath = XPathFactory.newInstance().newXPath();
        Node node = (Node) xPath.evaluate(updatePath, doc, XPathConstants.NODE);
        node.setTextContent(newBaseDirectory);

        File target = tf.newFile();
        xmlIO.writeXML(target, doc);

        Diff diff = DiffBuilder.compare(fromFile(xmlFile)).withTest(Input.fromFile(target)).build();

        logger.debug("Difference {}", diff);

        String targetXML = FileUtils.readFileToString(target, Charset.defaultCharset());
        // XMLUnit only seems to support XPath 1.0 so modify the expression to find the value.
        String xpathForHamcrest = "/*/*[local-name() = '" + updatePath.substring(updatePath.lastIndexOf('/') + 1)
                + "']";

        assertThat(targetXML, hasXPath(xpathForHamcrest));
        assertThat(targetXML, EvaluateXPathMatcher.hasXPath(xpathForHamcrest, equalTo(newBaseDirectory)));
        assertTrue(diff.toString(), diff.hasDifferences());
    }

    @Test
    public void modifyMultiple()
            throws ManipulationException, IOException, XPathExpressionException {
        String updatePath = "/assembly/formats/format";
        Document doc = xmlIO.parseXML(xmlFile);

        XPath xPath = XPathFactory.newInstance().newXPath();
        NodeList nodeList = (NodeList) xPath.evaluate(updatePath, doc, XPathConstants.NODESET);
        logger.debug("Found node {} with size {}", nodeList, nodeList.getLength());

        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            logger.debug(
                    "Found node {} with type {} and value {}",
                    node.getNodeName(),
                    node.getNodeType(),
                    node.getTextContent());
            node.setTextContent("NEW-FORMAT-" + i);
        }

        File target = tf.newFile();
        xmlIO.writeXML(target, doc);

        Diff diff = DiffBuilder.compare(fromFile(xmlFile)).withTest(Input.fromFile(target)).build();
        assertTrue(diff.toString(), diff.hasDifferences());
        String xpathForHamcrest = "/*/*/*[starts-with(.,'NEW-FORMAT') and local-name() = '"
                + updatePath.substring(updatePath.lastIndexOf('/') + 1) + "']";
        Iterable<Node> i = new JAXPXPathEngine().selectNodes(xpathForHamcrest, Input.fromFile(target).build());
        int count = 0;
        for (Node anI : i) {
            count++;
            assertTrue(anI.getTextContent().startsWith("NEW-FORMAT"));
        }
        assertEquals(3, count);
    }

    @Test
    public void modifyPartialFile()
            throws ManipulationException, IOException, XPathExpressionException {
        String replacementGA = "com.rebuild:servlet-api";
        String tomcatPath = "//include[starts-with(.,'org.apache.tomcat')]";

        Document doc = xmlIO.parseXML(xmlFile);

        XPath xPath = XPathFactory.newInstance().newXPath();
        NodeList nodeList = (NodeList) xPath.evaluate(tomcatPath, doc, XPathConstants.NODESET);
        logger.debug("Found node {} with size {}", nodeList, nodeList.getLength());

        assertEquals(1, nodeList.getLength());
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            logger.debug(
                    "Found node {} with type {} and value {}",
                    node.getNodeName(),
                    node.getNodeType(),
                    node.getTextContent());
            node.setTextContent(replacementGA);
        }

        File target = tf.newFile();
        xmlIO.writeXML(target, doc);

        Diff diff = DiffBuilder.compare(fromFile(xmlFile)).withTest(Input.fromFile(target)).build();
        assertTrue(diff.toString(), diff.hasDifferences());

        String xpathForHamcrest = "/*/*/*/*/*[starts-with(.,'com.rebuild') and local-name() = 'include']";
        Iterable<Node> i = new JAXPXPathEngine().selectNodes(xpathForHamcrest, Input.fromFile(target).build());
        int count = 0;
        for (Node anI : i) {
            count++;
            assertTrue(anI.getTextContent().startsWith("com.rebuild:servlet-api"));
        }
        assertEquals(1, count);
    }

    @Test
    public void modifyNotFoundFile()
            throws ManipulationException, XPathExpressionException {
        String tomcatPath = "//include[starts-with(.,'i-do-not-exist')]";

        Document doc = xmlIO.parseXML(xmlFile);

        XPath xPath = XPathFactory.newInstance().newXPath();
        NodeList nodeList = (NodeList) xPath.evaluate(tomcatPath, doc, XPathConstants.NODESET);
        logger.debug("Found node {} with size {}", nodeList, nodeList.getLength());

        assertEquals(0, nodeList.getLength());
    }

    @Test
    public void testFindSingleInstance() throws ManipulationException, XPathExpressionException {
        Document doc = xmlIO.parseXML(xmlFile);
        XPath xPath = XPathFactory.newInstance().newXPath();
        NodeList nodeList = (NodeList) xPath
                .evaluate("//include[starts-with(.,\"org.apache.tomcat\")]", doc, XPathConstants.NODESET);

        assertNotEquals(0, nodeList.getLength());
    }

    @Test
    public void removePartFile()
            throws ManipulationException, IOException, XPathExpressionException {
        String tomcatPath = "//include[starts-with(.,'org.apache.tomcat')]";

        Document doc = xmlIO.parseXML(xmlFile);

        XPath xPath = XPathFactory.newInstance().newXPath();
        NodeList nodeList = (NodeList) xPath.evaluate(tomcatPath, doc, XPathConstants.NODESET);
        logger.debug("Found node {} with size {}", nodeList, nodeList.getLength());

        assertEquals(1, nodeList.getLength());

        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);

            logger.debug(
                    "Found node {} with type {} and value {}",
                    node.getNodeName(),
                    node.getNodeType(),
                    node.getTextContent());
            node.getParentNode().removeChild(node);
        }

        File target = tf.newFile();
        xmlIO.writeXML(target, doc);

        Diff diff = DiffBuilder.compare(fromFile(xmlFile)).withTest(Input.fromFile(target)).build();
        assertTrue(diff.toString(), diff.hasDifferences());

        String xpathForHamcrest = "/*/*/*/*/*[starts-with(.,'org.apache.tomcat') and local-name() = 'include']";
        Iterable<Node> i = new JAXPXPathEngine().selectNodes(xpathForHamcrest, Input.fromFile(target).build());
        int count = 0;
        for (Node ignored : i) {
            count++;
        }
        assertEquals(0, count);
    }
}
