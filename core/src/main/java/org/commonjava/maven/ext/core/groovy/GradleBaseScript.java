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
package org.commonjava.maven.ext.core.groovy;

import lombok.extern.slf4j.Slf4j;

/**
 * Common API for developers wishing to implement groovy scripts for GME.
 * Abstract class that is used as a marker and extended by the Gradle Manipulation Tooling.
 */
@Slf4j
public abstract class GradleBaseScript extends org.jboss.pnc.maven_manipulator.core.groovy.GradleBaseScript {
    {
        log.warn("Deprecated Groovy API - switch to importing org.jboss.pnc.maven_manipulator");
    }
}
