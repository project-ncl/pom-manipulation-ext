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
def pomFile = new File( basedir, 'pom.xml' )
System.out.println( "Slurping POM: ${pomFile.getAbsolutePath()}" )

def pom = new XmlSlurper().parse( pomFile )

System.out.println( "POM Version: ${pom.version.text()}" )
assert pom.version.text().equals( "2.7.2.3-fuse-redhat-2" )

v = pom.parent.version.text()
System.out.println( "POM Version: ${v}" )
assert v == "\${project-version}"

// Currently the AddSuffixJettyHandler doesn't do OSGi compatibility.
def dependency = pom.dependencyManagement.dependencies.dependency.find { it.artifactId.text() == "commons-lang" }
assert dependency != null
assert dependency.version.text() == "1.0-redhat-1"

dependency = pom.dependencies.dependency.find { it.artifactId.text() == "errai-common" }
assert dependency != null
assert dependency.version.text() == "1.1-Final-redhat-1"

def passed = 0
pom.properties.each {
    if ( it.text().contains ("3.1-redhat-1") )
    {
        passed++
    }
    if ( it.text().contains ("2.7.2.3-fuse-redhat-1"))
    {
        passed++
    }
}
assert (passed == 2)

def buildLog = new File( basedir, "build.log")
assert buildLog.getText().contains("Passing 6 GAVs")
assert !buildLog.getText().contains("Passing 1 Project GAVs into the REST client api [org.jboss.pnc.maven-manipulator.integration-test:\${myArtifactId}:\${project-version}]")
assert buildLog.getText().contains("Passing 1 Project GAVs into the REST client api [org.jboss.pnc.maven-manipulator.integration-test:rest-version-manip-with-properties:2.7.2_3-fuse-SNAPSHOT]")

File json = new File( basedir, "target/alignmentReport.json")
assert json.exists()
assert !json.text.contains('"version" : "${project-version}"')
assert json.text.contains('"version" : "2.7.2.3-fuse-redhat-1"')
assert json.text.contains('"artifactId" : "rest-version-manip-with-properties"')