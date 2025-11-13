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
// This grab annotation ( http://docs.groovy-lang.org/latest/html/api/groovy/lang/Grab.html )
// is not actually needed at runtime (as PME will automatically provide the dependencies) but
// its useful during development as IntelliJ can then correctly locate the dependencies for
// the project and provide completion assist.
// @Grab('org.jboss.pnc.maven-manipulator:pom-manipulation-core:${project.version}')
// @Grab('org.commonjava.atlas.maven:atlas-identities:0.17.1')

import org.commonjava.atlas.maven.ident.ref.SimpleProjectRef
import org.jboss.pnc.mavenmanipulator.core.groovy.BaseScript
import org.jboss.pnc.mavenmanipulator.core.groovy.InvocationStage
import org.jboss.pnc.mavenmanipulator.core.groovy.PMEBaseScript
import org.jboss.pnc.mavenmanipulator.core.groovy.InvocationPoint

@InvocationPoint(invocationPoint = InvocationStage.FIRST)
@PMEBaseScript BaseScript pme


println "Altering project " + pme.getGAV()
pme.inlineProperty(pme.getProject(), SimpleProjectRef.parse("org.apache.maven:maven-core"))
