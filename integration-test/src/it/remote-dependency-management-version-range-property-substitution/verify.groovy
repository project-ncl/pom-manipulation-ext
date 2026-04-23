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
println "Slurping POM: ${pomFile.getAbsolutePath()}"

def pom = new XmlSlurper().parse( pomFile )

println "POM Version: ${pom.version.text()}"
assert pom.version.text().endsWith( '.redhat-6' )

dependency = pom.dependencyManagement.dependencies.dependency.find { it.artifactId.text() == 'commons-lang' }
assert dependency != null
assert dependency.version.text() == '1.0.1'

dependency = pom.dependencyManagement.dependencies.dependency.find { it.artifactId.text() == 'jacorb' }
assert dependency != null
assert dependency.version.text() == '2.0.redhat-1'

assert pom.properties.find { !it.text().endsWith( 'redhat-1' ) } != null

def plugin = pom.build.pluginManagement.plugins.plugin.find { it.artifactId.text() == 'maven-dependency-plugin' }
assert plugin != null
assert plugin.version.text() == '3.1.2'
