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
System.out.println( "Checking parent output..." )

def pomFile = new File( basedir, 'pom.xml' )
System.out.println( "Slurping POM: ${pomFile.getAbsolutePath()}" )
assert pomFile.text.contains ("junit")

def pom = new XmlSlurper().parse( pomFile )
def v = pom.version.text()
def g = pom.groupId.text()
def a = pom.artifactId.text()
def overallartifactid = pom.artifactId.text()

System.out.println( "POM Version: ${v}" )
assert v.endsWith( '.redhat-1' )

def pomBomFile = new File( basedir, 'target/pme-bom.xml' )
System.out.println( "Slurping POM: ${pomBomFile.getAbsolutePath()}" )
def pommodule = new XmlSlurper().parse( pomBomFile )
def vm = pommodule.version.text()
def gm = pommodule.groupId.text()
def am = pommodule.artifactId.text()
assert pomBomFile.text.contains ("PME Generated BOM")

System.out.println( "POM GAV ${gm} : ${am} : ${vm}" )
assert vm.endsWith( '.redhat-1' )

// Check the install pom
def repodir = new File('@localRepositoryUrl@', "${g.replace('.', '/')}/${a}/${v}" )
def repopom = new File( repodir, "${a}-${v}.pom" )
System.out.println( "Using repodir: ${repodir}")
System.out.println( "Checking for installed pom: ${repopom.getAbsolutePath()}")
assert repopom.exists()
assert repopom.text.contains ("junit")

// Now check the BOM
repopom = new File( repodir, "../${am}/${v}/${am}-${v}.pom" )
System.out.println( "Checking for installed bom: ${repopom.getAbsolutePath()}")
assert repopom.exists()
def repopomslurped = new XmlSlurper().parse( repopom )
assert repopomslurped.artifactId.text().equals ("pme-bom")
assert repopomslurped.artifactId.text().endsWith ("${am}")
assert repopomslurped.groupId.text().endsWith ("${gm}")
assert repopom.text.contains ("PME Generated BOM")

def dependency = repopomslurped.dependencyManagement.dependencies.dependency.find { it.artifactId.text() == "single-module-bom-generation" }
assert dependency != null
assert dependency.version.text() == "1.0.0.redhat-1"
assert repopomslurped

def bomdeploy = new File('@localRepositoryUrl@', "../local-deploy/${gm.replace('.', '/')}/pme-bom/${vm}/${am}-${v}.pom" )
System.out.println( "Checking for deployed bom: ${bomdeploy}")
assert bomdeploy.exists()
def bomdeployslurped = new XmlSlurper().parse( bomdeploy )
assert bomdeployslurped.artifactId.text().equals ("pme-bom")
assert bomdeployslurped.artifactId.text().endsWith ("${am}")
assert bomdeployslurped.groupId.text().endsWith ("${gm}")
assert bomdeploy.text.contains ("PME Generated BOM")
