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
package org.jboss.pnc.maven_manipulator.depMgmt1

@Grab('org.zeroturnaround:zt-exec:1.10')

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.jboss.pnc.maven_manipulator.core.groovy.BaseScript
import org.jboss.pnc.maven_manipulator.core.groovy.InvocationPoint
import org.jboss.pnc.maven_manipulator.core.groovy.InvocationStage
import org.jboss.pnc.maven_manipulator.core.groovy.PMEBaseScript
import org.yaml.snakeyaml.Yaml
import org.zeroturnaround.exec.ProcessExecutor

@InvocationPoint(invocationPoint = InvocationStage.FIRST)
@PMEBaseScript BaseScript pme

@Slf4j
class Processor {
    File basedir
    Properties props

    private void parseDeps(Map n) {
        def iterator = n.iterator()
        while (iterator.hasNext()) {
            def entry = iterator.next()

            if (entry != null && entry.getKey() == "resolved") {
                iterator.remove()
            }

            if (entry != null && entry.getValue() instanceof Map) {
                parseDeps(entry.getValue())
            }
        }
    }

    def execute() {
        log.info("Running ShrinkwrapProcessor..." )
        log.info("groovyScripts " + props.get("groovyScripts"))

        def shrinkwrap = new File (basedir.toString() + File.separator + "shrink.json")

        log.info("shrinkwrap json is " + shrinkwrap)

        if (shrinkwrap.exists()) {
            log.info ("Found file {}", shrinkwrap)

            // Annoyingly the JsonSlurper reads them and does NOT maintain the order. Therefore
            // the diff is not overly helpful
            LinkedHashMap json = new JsonSlurper().parseText(shrinkwrap.text)

            parseDeps(json)

            shrinkwrap.write(JsonOutput.prettyPrint(JsonOutput.toJson(json)))
        }
    }
}

def parser = new Yaml()
def ProcessExecutor pe = new ProcessExecutor()

// These are both debug AND test statements - do NOT remove. If the injection (in InitialGroovyManipulator)
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
println pme.getSession().getPom()
println "Parser is " + parser.toString()
println "ProcessExecutor is " + pe.getCommand()
println "#### BASESCRIPT END"



// End...

Processor sp = new Processor(basedir:pme.getBaseDir(), props:pme.getUserProperties())
sp.execute()
