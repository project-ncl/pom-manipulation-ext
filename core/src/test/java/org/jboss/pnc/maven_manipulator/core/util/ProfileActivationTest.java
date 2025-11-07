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
package org.jboss.pnc.maven_manipulator.core.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.jboss.pnc.maven_manipulator.common.model.Project;
import org.jboss.pnc.maven_manipulator.core.ManipulationSession;
import org.jboss.pnc.maven_manipulator.core.fixture.TestUtils;
import org.jboss.pnc.maven_manipulator.io.PomIO;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProfileActivationTest {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final String RESOURCE_BASE = "";

    // Locate the PME project pom file. Use that to verify inheritance tracking.
    private static final File PROJECTROOT = TestUtils.resolveFileResource(RESOURCE_BASE, "profile.pom");

    private List<Project> getProject() throws Exception {
        PomIO pomIO = new PomIO();
        List<Project> projects = pomIO.parseProject(PROJECTROOT);
        assertEquals(1, projects.size());

        return projects;
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testVerifyProfile1() throws Exception {
        List<Project> p = getProject();
        TestUtils.SMContainer smc = TestUtils.createSessionAndManager(null, PROJECTROOT);

        Set<String> activeProfiles = (Set<String>) TestUtils.executeMethod(
                smc.getManager(),
                "parseActiveProfiles",
                new Class[] { ManipulationSession.class, List.class },
                new Object[] { smc.getSession(), p });

        logger.info("Returning active profiles of {}", activeProfiles);

        assertEquals(2, activeProfiles.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testVerifyProfile2() throws Exception {
        List<Project> p = getProject();
        Properties properties = new Properties();
        properties.setProperty("testProperty", "testvalue");
        TestUtils.SMContainer smc = TestUtils.createSessionAndManager(properties, PROJECTROOT);

        Set<String> activeProfiles = (Set<String>) TestUtils.executeMethod(
                smc.getManager(),
                "parseActiveProfiles",
                new Class[] {
                        ManipulationSession.class,
                        List.class },
                new Object[] { smc.getSession(), p });

        if (logger.isInfoEnabled()) {
            activeProfiles.forEach(activeProfile -> logger.info("Active list is {}", activeProfile));
        }

        assertEquals(3, activeProfiles.size());
        assertTrue(activeProfiles.stream().anyMatch(activeProfile -> "testProperty".equals(activeProfile)));
    }
}
