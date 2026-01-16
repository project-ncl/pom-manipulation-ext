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

def plugin = null
pom.build.plugins.children().each{
    if (it.artifactId == "project-sources-maven-plugin" ){
        assert it.version == '2.0'
    }
}

assert new File( basedir, 'target/project-sources-injection-basic-1-project-sources.tar.gz').exists()


// Test to ensure final line is output
def buildLog = new File( basedir, 'build.log' )
def finishedLine = false
buildLog.eachLine {
   if (it.contains( "Maven-Manipulation-Extension: Finished")) {
      finishedLine = true
   }
}
assert finishedLine == true


return true
