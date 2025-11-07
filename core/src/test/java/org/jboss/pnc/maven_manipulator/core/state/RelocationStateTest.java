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
package org.jboss.pnc.maven_manipulator.core.state;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import java.util.Properties;

import org.jboss.pnc.maven_manipulator.common.ManipulationException;
import org.junit.Test;

public class RelocationStateTest {

    @Test
    public void disabledByDefault()
            throws ManipulationException {
        final RelocationState state = new RelocationState(new Properties());

        assertThat(state.isEnabled(), equalTo(false));
    }

    @Test(expected = ManipulationException.class)
    public void invalidDepRelocationConfig()
            throws ManipulationException {
        final Properties p = new Properties();
        p.setProperty(RelocationState.DEPENDENCY_RELOCATIONS + "oldGroupId", "");

        final RelocationState state = new RelocationState(p);

        assertThat(state.isEnabled(), equalTo(true));
    }

    @Test(expected = ManipulationException.class)
    public void invalidDepRelocationConfigTwo()
            throws ManipulationException {
        final Properties p = new Properties();
        p.setProperty(RelocationState.DEPENDENCY_RELOCATIONS + "oldGroupId:newGroupId@", "");

        final RelocationState state = new RelocationState(p);

        assertThat(state.isEnabled(), equalTo(true));
    }

    @Test
    public void testRelocationsWithArtifact()
            throws ManipulationException {
        final Properties p = new Properties();
        p.setProperty(
                RelocationState.DEPENDENCY_RELOCATIONS + "oldGroupId:oldArtifactId@newGroupId:newArtifactId",
                "1.10");

        final RelocationState state = new RelocationState(p);

        assertThat(state.isEnabled(), equalTo(true));
    }

    @Test
    public void testRelocationsWithWildcard()
            throws ManipulationException {
        final Properties p = new Properties();
        p.setProperty(RelocationState.DEPENDENCY_RELOCATIONS + "oldGroupId:@newGroupId:", "1.10");

        final RelocationState state = new RelocationState(p);

        assertThat(state.isEnabled(), equalTo(true));
    }

    @Test
    public void testRelocationsWithWildcardEmptyVersion()
            throws ManipulationException {
        final Properties p = new Properties();
        p.setProperty(RelocationState.DEPENDENCY_RELOCATIONS + "oldGroupId:@newGroupId:", "");

        final RelocationState state = new RelocationState(p);

        assertThat(state.isEnabled(), equalTo(true));
    }

    @Test(expected = ManipulationException.class)
    public void testRelocationsWithArtifactInvalid()
            throws ManipulationException {
        final Properties p = new Properties();
        p.setProperty(RelocationState.DEPENDENCY_RELOCATIONS + "oldGroupId:@newGroupId:newA", "");

        final RelocationState state = new RelocationState(p);

        assertThat(state.isEnabled(), equalTo(true));
    }

    @Test(expected = ManipulationException.class)
    public void testRelocationsWithGroupInvalid()
            throws ManipulationException {
        final Properties p = new Properties();
        p.setProperty(RelocationState.DEPENDENCY_RELOCATIONS + "oldGroupId:oldA@:newA", "");

        final RelocationState state = new RelocationState(p);

        assertThat(state.isEnabled(), equalTo(true));
    }
}
