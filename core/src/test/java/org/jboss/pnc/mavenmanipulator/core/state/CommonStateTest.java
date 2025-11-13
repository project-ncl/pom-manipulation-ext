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
package org.jboss.pnc.mavenmanipulator.core.state;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.jboss.pnc.mavenmanipulator.core.state.DependencyState.DEPENDENCY_SOURCE;

import java.util.Properties;

import org.jboss.pnc.mavenmanipulator.common.ManipulationException;
import org.junit.Test;

public class CommonStateTest {
    @Test
    public void testEnum() throws ManipulationException {
        Properties p = new Properties();
        p.setProperty(DEPENDENCY_SOURCE, "");
        new DependencyState(p);
    }

    @Test
    public void excludedScopes() throws ManipulationException {
        final CommonState state = new CommonState(new Properties());

        assertEquals(0, state.getExcludedScopes().size());
    }

    @Test
    public void excludedScopesTestScope() throws ManipulationException {
        Properties p = new Properties();
        p.setProperty(CommonState.EXCLUDED_SCOPES, "test");

        final CommonState state = new CommonState(p);

        assertTrue(state.getExcludedScopes().contains("test"));
    }

    @Test
    public void excludedScopesMultipleScope() throws ManipulationException {
        Properties p = new Properties();
        p.setProperty(CommonState.EXCLUDED_SCOPES, "test,provided");

        final CommonState state = new CommonState(p);

        assertEquals(2, state.getExcludedScopes().size());
        assertTrue(state.getExcludedScopes().contains("test"));
        assertTrue(state.getExcludedScopes().contains("provided"));
    }

    @Test(expected = ManipulationException.class)
    public void excludedScopesInvalid() throws ManipulationException {
        Properties p = new Properties();
        p.setProperty(CommonState.EXCLUDED_SCOPES, "test,foo");
        new CommonState(p);
    }
}
