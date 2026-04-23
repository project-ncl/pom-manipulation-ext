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

def buildLog = new File( basedir, 'build.log' )
assert ! buildLog.getText().contains( 'NullPointerException' )
assert ! buildLog.getText().contains( 'ManipulationException' )

def pom = new XmlSlurper().parse( pomFile )

def dependency = pom.dependencies.dependency.find { it.artifactId.text() == "junit" }
assert dependency != null
assert dependency.version.text().contains("version.junit")

def failed = false

pom.properties.each {
    if ( it.text().contains ("4.1") )
    {
        failed = true
    }
}
assert (failed == false)
