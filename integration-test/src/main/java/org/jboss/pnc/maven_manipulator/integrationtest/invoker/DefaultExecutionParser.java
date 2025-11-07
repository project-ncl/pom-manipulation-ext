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
package org.jboss.pnc.maven_manipulator.integrationtest.invoker;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * @author vdedik@redhat.com
 */
public class DefaultExecutionParser
        implements ExecutionParser {
    private static final List<ExecutionParserHandler> handlers = Arrays.asList(
            ExecutionParser.SKIP_HANDLER,
            ExecutionParser.BUILD_HANDLER,
            ExecutionParser.BUILD_RESULT_HANDLER,
            ExecutionParser.BUILD_PROFILES_HANDLER,
            ExecutionParser.SYSTEM_PROPERTIES_HANDLER);

    @Override
    public Collection<Execution> parse(String workingDir) {
        final Properties invokerProperties = ExecutionParser.loadProps(workingDir + "/invoker.properties");
        Map<Integer, Execution> executions = new TreeMap<>();

        for (Object rawKey : invokerProperties.keySet()) {
            final String key = (String) rawKey;
            String[] parts = key.split("\\.");
            int id;
            try {
                id = Integer.parseInt(parts[parts.length - 1]);
            } catch (NumberFormatException e) {
                id = 1;
            }
            Execution execution;
            if (executions.containsKey(id)) {
                execution = executions.get(id);
            } else {
                // Create new execution
                execution = new Execution();
                execution.setLocation(workingDir);
            }

            // Run through all handlers
            for (ExecutionParserHandler handler : handlers) {
                handler.handle(execution, new HashMap<String, String>() {
                    {
                        put("key", key);
                        put("value", invokerProperties.getProperty(key));
                    }
                });
            }

            executions.put(id, execution);
        }
        if (invokerProperties.keySet().isEmpty()) {
            // by default do one execution (maven install)
            Execution execution = new Execution();
            execution.setLocation(workingDir);
            execution.setMvnCommand("install");
            executions.put(1, execution);
        }
        // Run POST_HANDLER
        for (Execution execution : executions.values()) {
            ExecutionParser.POST_HANDLER.handle(execution, null);
        }

        return executions.values();
    }
}
