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
package org.jboss.pnc.mavenmanipulator.ext;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.jboss.pnc.mavenmanipulator.common.ManipulationException;
import org.jboss.pnc.mavenmanipulator.core.ManipulationSession;

@Named
@Singleton
public class ManipulatingLifeCycleParticipant
        extends AbstractMavenLifecycleParticipant {
    private ManipulationSession session;

    @Inject
    public ManipulatingLifeCycleParticipant(ManipulationSession session) {
        this.session = session;
    }

    @Override
    public void afterProjectsRead(final MavenSession mavenSession)
            throws MavenExecutionException {
        final ManipulationException error = session.getError();
        if (error != null) {
            throw new MavenExecutionException("POM Manipulation failed: " + error.getMessage(), error);
        }

        super.afterProjectsRead(mavenSession);
    }

}
