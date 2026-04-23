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

def dependency = pom.dependencyManagement.dependencies.dependency.find { it.artifactId.text() == "junit-dep" }
assert dependency != null
assert dependency.version.text() == "4.1"

pom.dependencies.dependency.find { it.artifactId.text() == "junit" }
assert dependency != null
assert dependency.version.text() == "4.1"

dependency = pom.dependencies.dependency.find { it.scope.text() == "test" }
assert dependency != null
assert dependency.groupId.text() == "junit"

dependency = pom.dependencies.dependency.find { it.artifactId.text() == "junit-dep" && it.scope.text() != "test" }
assert dependency != null
assert dependency.groupId.text() == "junit"

dependency = pom.dependencies.dependency.find { it.artifactId.text() == "commons-lang" }
assert dependency != null
assert dependency.version.text() == "1.5"

def buildLog = new File( basedir, 'build.log' )
assert buildLog.getText().contains( 'No version alignment to perform for relocation commons-lang' )

def profile1 = pom.profiles.profile.find { it.id.text() == "one" }
assert profile1.size() != 0
dependency = pom.dependencyManagement.dependencies.dependency.find { it.artifactId.text() == "junit-dep" }
assert dependency != null
assert dependency.version.text() == "4.1"


def passed = false
pom.properties.each {
    if ( it.text().contains ("org.slf4j") )
    {
        passed = true
    }
}
assert (passed == true)
