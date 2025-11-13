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
package org.jboss.pnc.mavenmanipulator.integrationtest.invoker;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.SystemUtils;

/**
 * @author vdedik@redhat.com
 */
public interface ExecutionParser {
    ExecutionParserHandler SKIP_HANDLER = (execution, params) -> {
        String key = params.get("key");
        String value = params.get("value");

        if (key.matches("invoker\\.os\\.family.*") && value != null) {
            value = value.trim();
            boolean invert = false;
            if (value.startsWith("!")) {
                invert = true;
                value = value.substring(1);
            }
            // Very limited handling of selector conditions - just enough to skip for Windows.
            if ("windows".equalsIgnoreCase(value)) {
                if (invert && SystemUtils.IS_OS_WINDOWS) {
                    execution.setSkip(true);
                }
            }
        }
    };

    /**
     * Sets mvn command of Execution
     */
    ExecutionParserHandler BUILD_HANDLER = (execution, params) -> {
        String key = params.get("key");
        String value = params.get("value");

        if (key.matches("invoker\\.goals.*")) {
            execution.setMvnCommand(value);
        } else if (!key.matches("invoker\\..*") && value.isEmpty()) {
            execution.setMvnCommand(key);
        }
    };

    ExecutionParserHandler BUILD_PROFILES_HANDLER = (execution, params) -> {
        String key = params.get("key");
        String value = params.get("value");

        if (key.matches("invoker\\.profiles.*")) {
            Map<String, String> flags = execution.getFlags();
            if (flags == null) {
                flags = new HashMap<>();
            }
            flags.put("activeProfiles", value);
            execution.setFlags(flags);
        }
    };

    /**
     * Sets expected result of Execution (i.e. success true/false)
     */
    ExecutionParserHandler BUILD_RESULT_HANDLER = (execution, params) -> {
        String key = params.get("key");
        String value = params.get("value");

        if (key.matches("invoker\\.buildResult.*") && value.equals("failure")) {
            execution.setSuccess(false);
        }
    };

    /**
     * Sets java parameters to Execution
     */
    ExecutionParserHandler SYSTEM_PROPERTIES_HANDLER = (execution, params) -> {
        String key = params.get("key");
        String value = params.get("value");

        if (key.matches("invoker\\.systemPropertiesFile.*")) {
            Properties props = loadProps(execution.getLocation() + "/" + value);

            // Properties to Map
            Map<String, String> javaParams = propsToMap(props);
            if (execution.getJavaParams() != null) {
                javaParams.putAll(execution.getJavaParams());
            }

            // And put it all into execution
            execution.setJavaParams(javaParams);
        }
    };

    /**
     * Used after all other handlers were run
     */
    ExecutionParserHandler POST_HANDLER = (execution, params) -> {
        if (execution.getJavaParams() == null) {
            Properties props = loadProps(execution.getLocation() + "/test.properties");
            Map<String, String> javaParams = propsToMap(props);
            execution.setJavaParams(javaParams);
        }
    };

    /**
     * Loads *.properties file.
     *
     * @param filePath - File path of the *.properties file
     * @return Loaded properties
     */
    static Properties loadProps(String filePath) {
        File propsFile = new File(filePath);
        Properties props = new Properties();
        if (propsFile.isFile()) {
            try (Reader reader = Files.newBufferedReader(propsFile.toPath())) {
                props.load(reader);
            } catch (IOException e) {
                // ignore
            }
        }

        return props;
    }

    static Map<String, String> propsToMap(Properties props) {
        Map<String, String> map = new HashMap<>(props.size());
        for (Object p : props.keySet()) {
            map.put((String) p, props.getProperty((String) p));
        }

        return map;
    }

    /**
     * Parse invoker properties from workingDir into a collection of executions.
     *
     * @param workingDir - Working directory where invoker.properties are present
     * @return Collection of executions
     */
    Collection<Execution> parse(String workingDir);
}
