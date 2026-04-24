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
def pomChildFile = new File( basedir, 'child/pom.xml' )
System.out.println( "Slurping POM: ${pomFile.getAbsolutePath()} and ${pomChildFile.getAbsolutePath()}" )

def pom = new XmlSlurper().parse( pomFile )
def pomChild = new XmlSlurper().parse( pomChildFile )

System.out.println( "POM Version: ${pom.version.text()}" )
assert pom.version.text().endsWith( '.redhat-3' )
System.out.println( "POM Child Version: ${pomChild.version.text()}" )
assert pomChild.parent.version.text().endsWith( '.redhat-3' )

System.out.println( "POM parent Version: ${pom.parent.version.text()}" )
assert pom.parent.version.text().endsWith( '.redhat-00001' )

// Currently the AddSuffixJettyHandler doesn't do OSGi compatibility.
def dependency = pom.dependencyManagement.dependencies.dependency.find { it.artifactId.text() == "commons-lang" }
assert dependency != null
assert dependency.version.text() == "1.0-redhat-1"

dependency = pom.dependencies.dependency.find { it.artifactId.text() == "errai-common" }
assert dependency != null
assert dependency.version.text() == "1.1-Final-redhat-1"

dependency = pom.dependencyManagement.dependencies.dependency.find { it.artifactId.text() == "rest-dependency-version-manip-child-module-child" }
assert dependency != null
assert dependency.version.text() == "1.0.0.redhat-3"

def passed = false
pom.properties.each {
    if ( it.text().contains ("3.1-redhat-1") )
    {
        passed = true
    }
}
assert (passed == true)

dependency = pomChild.dependencyManagement.dependencies.dependency.find { it.artifactId.text() == "junit" }
assert dependency != null
assert dependency.version.text() == "4.1-redhat-1"

dependency = pomChild.dependencyManagement.dependencies.dependency.find { it.artifactId.text() == "commons-lang" }
assert dependency != null
assert dependency.version.text() == "2.6-redhat-1"
