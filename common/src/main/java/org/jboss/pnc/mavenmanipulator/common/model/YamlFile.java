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
package org.jboss.pnc.mavenmanipulator.common.model;

import java.io.Serializable;
import java.util.Map;

/**
 * This is used during loading of yaml configuration files. It relies on the following structure in the Yaml file:
 *
 * <pre>
 * {@code
 *
 * pme:
 *    key : value
 *    key : value
 * other-configuration:
 *    ....
 * }
 * </pre>
 *
 * All PME key/value commands should be the equivalent to the Java property -D&lt;key&gt;=&lt;value&gt;
 * <p>
 * Note that any other configuration values are ignoring by PME when reading the yaml file.
 * </p>
 */
public class YamlFile implements Serializable {
    private Map<String, String> pme;

    public Map<String, String> getPme() {
        return pme;
    }

    public void setPme(Map<String, String> pme) {
        this.pme = pme;
    }
}
