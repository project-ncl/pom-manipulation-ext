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
package org.jboss.pnc.mavenmanipulator.depMgmt3

// TODO: TESTING - ensure both imports
// import org.commonjava.maven.ext.core.groovy.BaseScript
// import org.commonjava.maven.ext.core.groovy.InvocationPoint
// import org.commonjava.maven.ext.core.groovy.InvocationStage
// import org.commonjava.maven.ext.core.groovy.PMEBaseScript
import org.jboss.pnc.mavenmanipulator.core.groovy.BaseScript
import org.jboss.pnc.mavenmanipulator.core.groovy.InvocationStage
import org.jboss.pnc.mavenmanipulator.core.groovy.PMEBaseScript
import org.jboss.pnc.mavenmanipulator.core.groovy.InvocationPoint

@InvocationPoint( invocationPoint = InvocationStage.ALL )
@PMEBaseScript BaseScript pme

// These are both debug AND test statements - do NOT remove. If the injection (in GroovyManipulator)
// fails these prints will cause the test to fail.
println "#### BASESCRIPT:"
println pme.getBaseDir()
println pme.getBaseDir().getClass().getName()
println pme.getGAV()
println pme.getGAV().getClass().getName()
println pme.getProjects()
println pme.getProject().getClass().getName()
println pme.getProjects()
println pme.getProject().getClass().getName()
println "#### BASESCRIPT END"
// End...

if ( pme.getProject() != null )
{
    pme.getProject().getModel().setVersion( "1.0.0" )
}
