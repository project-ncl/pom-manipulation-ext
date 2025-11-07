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
System.out.println( "Checking parent output..." )

def pomFile = new File( basedir, 'pom.xml' )
System.out.println( "Slurping POM: ${pomFile.getAbsolutePath()}" )

def pom = new XmlSlurper().parse( pomFile )
def v = pom.version.text()
def g = pom.groupId.text()
def a = pom.artifactId.text()

System.out.println( "POM Version: ${v}" )
assert v.endsWith( '.redhat-1' )

def repodir = new File('@localRepositoryUrl@', "${g.replace('.', '/')}/${a}/${v}" )
def repopom = new File( repodir, "${a}-${v}.pom" )
System.out.println( "Checking for installed pom: ${repopom.getAbsolutePath()}")
assert repopom.exists()

System.out.println( "Checking child output..." )

pomFile = new File( basedir, '../child/pom.xml' )
System.out.println( "Slurping POM: ${pomFile.getAbsolutePath()}" )

pom = new XmlSlurper().parse( pomFile )
assert !pom.groupId.text()
assert !pom.version.text()

v = pom.parent.version.text()
g = pom.parent.groupId.text()
a = pom.artifactId.text()

System.out.println( "POM Version: ${v}" )
assert v.endsWith( '.redhat-1' )

def jar = new File(basedir, "../child/target/${a}-${v}.jar" )
System.out.println( "Checking for jar: ${jar.getAbsolutePath()}")
assert jar.exists()

repodir = new File('@localRepositoryUrl@', "${g.replace('.', '/')}/${a}/${v}" )

repopom = new File( repodir, "${a}-${v}.pom" )
System.out.println( "Checking for installed pom: ${repopom.getAbsolutePath()}")
assert repopom.exists()

def repojar = new File( repodir, "${a}-${v}.jar" )
System.out.println( "Checking for installed jar: ${repojar.getAbsolutePath()}")
assert repojar.exists()

def buildLog = new File( basedir, 'build.log' )
def executionRoot = false
def inheritanceRoot = false
buildLog.eachLine {
   if (it.contains( "Setting execution root to org.jboss.pnc.maven_manipulator.integration-test:simple-numeric-directory-path:")) {
      executionRoot = true
   }
   if (it.contains( "and is the inheritance root")) {
      inheritanceRoot = true
   }
}
assert executionRoot == true
assert inheritanceRoot == true
