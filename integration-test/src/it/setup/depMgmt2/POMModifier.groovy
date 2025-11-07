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
package org.jboss.pnc.maven_manipulator.depMgmt2

import groovy.util.logging.Slf4j
import org.jboss.pnc.maven_manipulator.common.model.Project
import org.jboss.pnc.maven_manipulator.core.groovy.BaseScript
import org.jboss.pnc.maven_manipulator.core.groovy.InvocationStage
import org.jboss.pnc.maven_manipulator.core.groovy.PMEBaseScript
import org.jboss.pnc.maven_manipulator.core.groovy.InvocationPoint

@InvocationPoint(invocationPoint = InvocationStage.FIRST)
@PMEBaseScript BaseScript pme

@Slf4j
class GroovyModifier {
    Project project

     def execute() {
         log.info("Running alterations... {}", project.getKey() )

         Properties p = project.getModel().getProperties();

         for ( String prop : p.stringPropertyNames() )
         {
             log.debug( "Found property {}", prop )
             if ( prop.equals("myMavenVersion"))
             {
                 // Split it to prevent it being interpolated by integration tests.
                 p.setProperty(prop, '${project' + '.version}')
                 break
             }
         }
     }
}

GroovyModifier sp = new GroovyModifier(project: pme.getProject())
sp.execute()
