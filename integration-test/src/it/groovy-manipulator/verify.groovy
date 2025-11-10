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

assert !pom.version.text().endsWith( '.redhat-1' )

def jsonFile = new File( basedir, 'shrink.json' )
System.out.println( "Slurping JSON: ${jsonFile.getAbsolutePath()}" )
def jsonString = jsonFile.text

assert !jsonString.contains ("resolved:")

def buildLog = new File( basedir, 'build.log' )
def message = 0
buildLog.eachLine {
   if (it.contains( "groovyScripts org.jboss.pnc.maven-manipulator.integration-test:depMgmt1:groovy:1.0")) {
      message++
   }
}

assert message == 1
