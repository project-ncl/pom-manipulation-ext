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

def passed = false
pom.properties.each {
    if ( it.text().contains ("1.0") )
    {
        passed = true
    }
}
assert (passed == true)

def buildLog = new File( basedir, 'build.log' )
assert buildLog.getText().contains( "Replacing property 'myprop' with a new version would clash with existing version which does not match. Old value is 4.1 and new is 2.5. Purging update of existing property." )
assert buildLog.getText().contains( 'Context was replacing commons-lang:commons-lang:jar:2.5 and clashed with [junit:junit]' )
