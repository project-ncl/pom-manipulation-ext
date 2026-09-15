/*
 * Copyright © 2012 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
def pomFile = new File( basedir, 'pom.xml' )
System.out.println( "Slurping POM: ${pomFile.getAbsolutePath()}" )

def pom = new XmlSlurper().parse( pomFile )

// The version must have been suffixed. The mock server returns 1.0-redhat-1 as the best
// match (i.e. already built), so PME correctly produces the next build: 1.0.0.redhat-2
// (with OSGi normalisation of 1.0 -> 1.0.0).
def v = pom.version.text()
System.out.println( "POM Version: ${v}" )
assert v == '1.0.0.redhat-2'

// The <artifactId> on disk is rewritten to the resolved value by the write path
// (JDOMModelConverter writes model.getArtifactId() which is the resolved literal).
assert pom.artifactId.text() == 'rest-version-manip-property-artifactid'

def buildLog = new File( basedir, "build.log").getText()

// PME must have logged its WARN confirming that property resolution occurred.
assert buildLog.contains("uses property expression '") && buildLog.contains("' for <artifactId>")
assert buildLog.contains('${myArtifactId}')

// The REST call must have used the resolved name, not the raw expression.
assert buildLog.contains('rest-version-manip-property-artifactid:1.0')
assert !buildLog.contains('${myArtifactId}:1.0')
