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

def plugin = pom.build.pluginManagement.plugins.plugin.find { it.artifactId.text() == "maven-compiler-plugin" }
assert plugin != null
assert plugin.version.text() == "3.8.1"

def message = 0
pomFile.eachLine {
   if (it.contains( "<debug>true</debug>")) {
      message++
   }
   if (it.contains( "<id>get-build-timestamp</id>")) {
      message++
   }
   if (it.contains( "<id>get-scm-revision</id>")) {
      message++
   }
}

def buildLog = new File( basedir, 'build.log' )
def bcheck = false
buildLog.eachLine {
   if (it.contains( "Unable to inject execution get-build-timestamp as it clashes with an existing execution")) {
      bcheck = true
   }
}
assert bcheck == true
