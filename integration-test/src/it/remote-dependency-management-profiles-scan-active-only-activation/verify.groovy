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

def profile = pom.profiles.children().find { it.id.text() == 'zero' }
def dependency = profile.dependencyManagement.dependencies.dependency.find { it.artifactId.text() == "commons-lang" }
assert dependency != null
assert dependency.version.text() == "2.5"

profile = pom.profiles.children().find { it.id.text() == 'one' }
dependency = profile.dependencyManagement.dependencies.dependency.find { it.artifactId.text() == "commons-lang" }
assert dependency != null
assert dependency.version.text() == "1.0"

profile = pom.profiles.children().find { it.id.text() == 'two' }
dependency = profile.dependencyManagement.dependencies.dependency.find { it.artifactId.text() == "commons-lang" }
assert dependency != null
assert dependency.version.text() == "2.5"

// Test overrideTransitive=false
def junitDependency = profile.dependencyManagement.dependencies.dependency.find { it.artifactId.text() == "junit" }
assert junitDependency.size() == 0

def buildLog = new File( basedir, 'build.log' )
assert buildLog.getText().contains( 'Will not scan all profiles and returning active profiles of [zero, two]' )
