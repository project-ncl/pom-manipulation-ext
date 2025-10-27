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
package org.commonjava.maven.ext.io.rest;


import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;

import java.util.List;
import java.util.Map;

/**
 * @author vdedik@redhat.com
 */
public interface Translator
{
    int CHUNK_SPLIT_COUNT = 4;

    int DEFAULT_CONNECTION_TIMEOUT_SEC = 30;

    int DEFAULT_SOCKET_TIMEOUT_SEC = 600;

    int RETRY_DURATION_SEC = 30;

    /**
     * Executes HTTP request to a REST service that translates versions
     *
     * @param projects List of projects (GAVs)
     * @return Map of ProjectVersionRef objects as keys and translated versions as values
     * @throws RestException if an error occurs.
     */
    Map<ProjectVersionRef, String> lookupVersions( List<ProjectVersionRef> projects ) throws RestException;

    /**
     * Executes HTTP request to a REST service that translates versions. While similar to {@link Translator#lookupVersions(List)}
     * for this version, the DependencyAnalyser will ignore suffix priority and return the latest version for the
     * configured suffix mode. This is typically used for project version lookups.
     *
     * @param projects List of projects (GAVs)
     * @return Map of ProjectVersionRef objects as keys and translated versions as values
     * @throws RestException if an error occurs.
     */
    Map<ProjectVersionRef, String>  lookupProjectVersions( List<ProjectVersionRef> projects ) throws RestException;
}
