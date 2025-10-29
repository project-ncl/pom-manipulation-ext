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

import java.util.Properties;

import org.commonjava.maven.ext.annotation.ConfigValue;
import org.commonjava.maven.ext.common.ManipulationException;

/**
 * Captures configuration relating to groovy script execution.
 */
public class GroovyState
        implements State {
    /**
     * The name of the property which contains a comma separated list of remote groovy scripts to load.
     * 
     * <pre>
     * <code>-DgroovyScripts=org.foo:bar-script,....</code>
     * </pre>
     */
    @ConfigValue(docIndex = "groovy.html")
    private static final String GROOVY_SCRIPT = "groovyScripts";

    private String groovyScripts;

    public GroovyState(final Properties userProps) throws ManipulationException {
        initialise(userProps);
    }

    public void initialise(Properties userProps) throws ManipulationException {
        groovyScripts = userProps.getProperty(GROOVY_SCRIPT);

        // Catch old style groovy configuration. Not doc'ed as config value.
        if (userProps.getProperty("groovyManipulatorPrecedence") != null) {
            throw new ManipulationException("groovyManipulatorPrecedence is no longer valid");
        }
    }

    /**
     * Enabled ONLY if groovyScripts is provided in the user properties / CLI -D options.
     *
     * @see State#isEnabled()
     */
    @Override
    public boolean isEnabled() {
        return groovyScripts != null && !groovyScripts.isEmpty();
    }

    public String getGroovyScripts() {
        return groovyScripts;
    }
}
