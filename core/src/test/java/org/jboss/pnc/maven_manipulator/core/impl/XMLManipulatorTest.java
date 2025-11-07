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
package org.jboss.pnc.maven_manipulator.core.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.xmlunit.builder.Input.fromFile;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import org.apache.commons.io.FileUtils;
import org.jboss.pnc.maven_manipulator.common.ManipulationException;
import org.jboss.pnc.maven_manipulator.common.model.Project;
import org.jboss.pnc.maven_manipulator.core.fixture.TestUtils;
import org.jboss.pnc.maven_manipulator.core.state.XMLState;
import org.jboss.pnc.maven_manipulator.io.XMLIO;
import org.jboss.pnc.maven_manipulator.io.XMLIOTest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.Diff;
import org.xmlunit.xpath.JAXPXPathEngine;

public class XMLManipulatorTest {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private XMLManipulator xmlManipulator = new XMLManipulator(new XMLIO());

    private File xmlFile;

    @Rule
    public TemporaryFolder tf = new TemporaryFolder();

    @Before
    public void setup() throws IOException, IllegalAccessException, URISyntaxException {
        URL resource = XMLIOTest.class.getResource("activemq-artemis-dep.xml");
        xmlFile = tf.newFile();
        FileUtils.copyURLToFile(resource, xmlFile);
    }

    @Test(expected = ManipulationException.class)
    public void testNotFound() throws Exception {
        String path = "//include[starts-with(.,'i-do-not-exist')]";

        File target = tf.newFile();
        FileUtils.copyFile(xmlFile, target);

        Project p = new Project(target, TestUtils.getDummyModel());

        xmlManipulator.internalApplyChanges(p, new XMLState.XMLOperation(target.getName(), path, null));
    }

    @Test
    public void alterFile() throws Exception {
        String replacementGA = "com.rebuild:servlet-api";
        String tomcatPath = "//include[starts-with(.,'org.apache.tomcat')]";

        File target = tf.newFile();
        FileUtils.copyFile(xmlFile, target);
        Project project = new Project(target, TestUtils.getDummyModel());

        xmlManipulator
                .internalApplyChanges(project, new XMLState.XMLOperation(target.getName(), tomcatPath, replacementGA));

        Diff diff = DiffBuilder.compare(fromFile(xmlFile)).withTest(fromFile(target)).build();
        assertTrue(diff.toString(), diff.hasDifferences());

        String xpathForHamcrest = "/*/*/*/*/*[starts-with(.,'com.rebuild') and local-name() = 'include']";
        Iterable<Node> i = new JAXPXPathEngine().selectNodes(xpathForHamcrest, fromFile(target).build());
        int count = 0;
        for (Node anI : i) {
            count++;
            assertTrue(anI.getTextContent().startsWith("com.rebuild:servlet-api"));
        }
        assertEquals(1, count);

    }

}
