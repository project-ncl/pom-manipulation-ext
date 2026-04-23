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
System.out.println( "POM Version: ${pom.version.text()}" )

def ossDependency = pom.dependencies.dependency.find { it.artifactId.text() == "oss-parent" }
assert (ossDependency == null || ossDependency == "")

def ossDependencyInDependencyManagement = pom.dependencyManagement.dependencies.dependency.find { it.artifactId.text() == "oss-parent" }
assert (ossDependencyInDependencyManagement == null || ossDependencyInDependencyManagement == "")

assert ! pomFile.text.contains("oss-parent")
