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

def junitDependency = pom.dependencyManagement.dependencies.dependency.find { it.artifactId.text() == "junit" }
assert junitDependency != null
assert junitDependency.version.text() == "3.8.2"

def ossDependency = pom.dependencyManagement.dependencies.dependency.find { it.artifactId.text() == "oss-parent" }
assert ossDependency != null
assert ossDependency.version.text() == "9"

def ossParentVersion = pom.parent.version.text()
assert ossParentVersion != null
assert ossParentVersion == "9"

def buildLog = new File( basedir, 'build.log' )
assert buildLog.text.contains("Located deprecated property")
