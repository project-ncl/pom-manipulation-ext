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
package org.commonjava.maven.ext.common.util;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.maven.model.Dependency;
import org.commonjava.atlas.maven.ident.ref.ProjectRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom limited map implementation that handles the following format:
 * <p>
 * String(groupId) : Map (where Map contains String(artifactId):String(value) ).
 * </p>
 * artifactId may be a wildcard (*) or an explicit value.
 */
public class WildcardMap<T> {
    public static final String WILDCARD = "*";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * This map represents:
     * <p>
     * groupId : map where map is artifactId : value
     * </p>
     * artifactId may be a wildcard '*'.
     */
    private final Map<String, Map<String, T>> map = new LinkedHashMap<>();

    /**
     * Size implementation
     * 
     * @return the size of the wildcard map
     */
    public int size() {
        return map.size();
    }

    /**
     * @param key the key to look for
     * @return true if this map contains a mapping for the specified
     *         key.
     */
    public boolean containsKey(Dependency key) {
        String groupId = key.getGroupId();
        String artifactId = key.getArtifactId();

        return internalContainsKey(groupId, artifactId);
    }

    /**
     * @param key the key to look for
     * @return true if this map contains a mapping for the specified
     *         key.
     */
    public boolean containsKey(ProjectRef key) {
        String groupId = key.getGroupId();
        String artifactId = key.getArtifactId();

        return internalContainsKey(groupId, artifactId);
    }

    private boolean internalContainsKey(String groupId, String artifactId) {
        boolean result;

        Map<String, T> vMap = map.get(groupId);

        if (vMap == null || vMap.isEmpty()) {
            result = false;
        } else {
            if (vMap.get(WILDCARD) != null) {
                result = true;
            } else {
                result = vMap.containsKey(artifactId);
            }
        }
        return result;
    }

    /**
     * Associates the specified value with the specified key in this map.
     * 
     * @param key key to associate with
     * @param value value to associate with the key
     */
    public void put(ProjectRef key, T value) {
        String groupId = key.getGroupId();
        String artifactId = key.getArtifactId();

        Map<String, T> vMap = map.get(groupId);
        if (vMap == null) {
            vMap = new LinkedHashMap<>();
        }
        boolean wildcard = false;

        if (WILDCARD.equals(artifactId)) {
            // Erase any previous mappings.
            if (!vMap.isEmpty()) {
                logger.warn(
                        "Emptying map with keys {} as replacing with wildcard mapping {}",
                        vMap.keySet(),
                        key);
            }
            vMap.clear();
        } else {
            for (Object o : vMap.keySet()) {
                if (o.equals(WILDCARD)) {
                    wildcard = true;
                    break;
                }
            }
        }
        if (wildcard) {
            logger.warn(
                    "Unable to add {} with value {} as wildcard mapping for {} already exists.",
                    key,
                    value,
                    groupId);
        } else {
            vMap.put(artifactId, value);
            map.put(groupId, vMap);
        }
    }

    /**
     * @param key the groupId:artifactId key which is split to index purely
     *        by groupId.
     * @return the value to which the specified key is mapped,
     *         or {@code null} if this map contains no mapping for the key.
     */
    public T get(Dependency key) {
        String groupId = key.getGroupId();
        String artifactId = key.getArtifactId();

        return get(groupId, artifactId);
    }

    /**
     * @param key the groupId:artifactId key which is split to index purely
     *        by groupId.
     * @return the value to which the specified key is mapped,
     *         or {@code null} if this map contains no mapping for the key.
     */
    public T get(ProjectRef key) {
        String groupId = key.getGroupId();
        String artifactId = key.getArtifactId();

        return get(groupId, artifactId);
    }

    private T get(String groupId, String artifactId) {
        T result = null;

        Map<String, T> value = map.get(groupId);
        if (value != null) {
            if (value.get(WILDCARD) != null) {
                result = value.get(WILDCARD);
            } else {
                result = value.get(artifactId);
            }
        }
        return result;
    }

    /**
     * @return Returns true if the underlying map is empty.
     */
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public String toString() {
        return "WildcardMap{" +
                "map=" + map +
                '}';
    }
}
