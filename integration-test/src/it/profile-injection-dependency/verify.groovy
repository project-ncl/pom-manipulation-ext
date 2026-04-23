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

// We should have two profiles after injection
assert pom.profiles.children().size() == 2

// Check the 3.8 version of junit has been overridden
def junit = pom.depthFirst().findAll { it.name() == 'version.junit' }
assert junit[0] == '4.1'

def passed = false
pom.properties.each {
    if ( it.text().contains ("2.5") )
    {
        passed = true
    }
}
assert (passed == true)
