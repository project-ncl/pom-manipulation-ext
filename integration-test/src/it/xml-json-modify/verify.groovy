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
System.out.println( "POM Version: ${pom.version.text()}" )

assert pom.version.text().endsWith( '.redhat-1' )

def jar = new File(basedir, "target/${pom.artifactId.text()}-${pom.version.text()}.jar" )
System.out.println( "Checking for jar: ${jar.getAbsolutePath()}")
assert jar.exists()

def repodir = new File('@localRepositoryUrl@', "${pom.groupId.text().replace('.', '/')}/${pom.artifactId.text()}/${pom.version.text()}" )
def repojar = new File( repodir, "${pom.artifactId.text()}-${pom.version.text()}.jar" )
System.out.println( "Checking for installed jar: ${repojar.getAbsolutePath()}")
assert repojar.exists()

def repopom = new File( repodir, "${pom.artifactId.text()}-${pom.version.text()}.pom" )
System.out.println( "Checking for installed pom: ${repopom.getAbsolutePath()}")
assert repopom.exists()

def xmlFile = new File( basedir, 'activemq-artemis-dep.xml' )
def fileCheck = false
xmlFile.eachLine {
   if (it.contains( "includeBaseDirectory")) {
      fileCheck = true
   }
   if (it.contains( "tomcat")) {
      fileCheck = true
   }
}
assert fileCheck == false

xmlFile.eachLine {
   if (it.contains( "org.jboss.spec.javax.servlet:jboss-servlet-api_3.1_spec")) {
      fileCheck = true
   }
}
assert fileCheck == true

def jsonFile = new File( basedir, 'amg-plugin-registry.json' )
fileCheck = false
jsonFile.eachLine {
   if (it.contains( "CORS;Cross-origin-resource-sharing):controlling:access_to_resources_outside_of_an_originating_domain,and_to_this_domain.")) {
      fileCheck = true
   }
}
assert fileCheck == true
