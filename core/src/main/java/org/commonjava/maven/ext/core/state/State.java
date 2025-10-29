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
package org.commonjava.maven.ext.core.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.core.impl.Manipulator;

/**
 * Basic list of methods that state collections related to different {@link Manipulator}'s should implement. This is
 * also a marker interface to
 * help ensure the validity of content stored in the session.
 *
 * A State implementation can contain a mixture of configuration (parsed/configured from command-line properties or
 * other sources) and storage output
 * from the Manipulators.
 *
 * @author jdcasey
 */
public interface State {
    /**
     * Contains list of manipulations that are active by default for checking in applyChanges.
     */
    List<Class<? extends State>> activeByDefault = new ArrayList<>();

    /**
     * @return true if this State is enabled.
     */
    boolean isEnabled();

    void initialise(Properties userProperties) throws ManipulationException;
}
