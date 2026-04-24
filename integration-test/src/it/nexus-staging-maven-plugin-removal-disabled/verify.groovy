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

def plugin = pom.build.plugins.plugin.find { it.artifactId.text() == "nexus-staging-maven-plugin" }
assert (plugin != null && plugin != "")

def profile = pom.profiles.children().find { it.id.text() == 'extra-plugins' }
def profileplugin = profile.build.plugins.plugin.find { it.artifactId.text() == "nexus-staging-maven-plugin" }
assert (profileplugin != null && profileplugin != "")

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


def deploydir = new File("${project.build.directory}/local-deploy", "${pom.groupId.text().replace('.', '/')}/${pom.artifactId.text()}/${pom.version.text()}" )
def deploypom = new File( deploydir, "${pom.artifactId.text()}-${pom.version.text()}.pom" )
System.out.println( "Checking for deployed pom: ${deploypom.getAbsolutePath()}")
assert !deploypom.exists()

def buildLog = new File( basedir, "build.log" )
assert buildLog.text.contains( "Deployment failed" )
