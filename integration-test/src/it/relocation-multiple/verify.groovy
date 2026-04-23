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

def dependency = pom.dependencies.dependency.find { it.artifactId.text() == "special-junit" }
assert dependency != null
assert dependency.version.text() == "4.1"
assert dependency.groupId.text() == "com.junit"

assert 2 == pom.dependencies.dependency.count { it.artifactId.text() == "special-junit" }

dependency = pom.dependencies.dependency.find { it.artifactId.text() == "junit" }
assert dependency != null
assert dependency.version.text() == "4.1"
assert dependency.groupId.text() == "com.junit"

def mockitoDep = pom.dependencies.dependency.find { it.artifactId.text() == "mockito-core" }
assert mockitoDep != null
assert mockitoDep.groupId.text() == "org.mockito"
assert mockitoDep.version.size() == 0

def mockitoMgmt = pom.dependencyManagement.dependencies.dependency.find { it.artifactId.text() == "mockito-core" }
assert mockitoMgmt != null
assert mockitoMgmt.groupId.text() == "org.mockito"
assert mockitoMgmt.version.text() == "5.19.0"

def plugin1 = pom.build.plugins.plugin.find { it.groupId.text() == "com.redhat" &&
        it.artifactId.text() == "eap-maven-plugin" &&
        it.version.text() == "1.1.Final.redhat-1" }
assert plugin1.size() != 0

def plugin2 = pom.build.plugins.plugin.find { it.groupId.text() == "com.soebes.maven.plugins" &&
        it.artifactId.text() == "iterator-maven-plugin" }
assert plugin2.size() != 0

def plugin3 = pom.build.plugins.plugin.find { it.artifactId.text() == "maven-compiler-plugin" }
assert plugin3 != null
assert plugin3.groupId.text() == "org.apache.maven.plugins"
assert plugin3.version.size() == 0

def pluginMgmt1 = pom.build.pluginManagement.plugins.plugin.find { it.artifactId.text() == "maven-compiler-plugin" }
assert pluginMgmt1 != null
assert pluginMgmt1.groupId.text() == "org.apache.maven.plugins"
assert pluginMgmt1.version.text() == "3.14.1"
