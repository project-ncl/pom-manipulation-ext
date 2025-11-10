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
final def pomFile = new File( basedir, 'pom.xml' )
println "Slurping POM: ${pomFile.getAbsolutePath()}"

final def pom = new XmlSlurper().parse( pomFile )

final def dependencyManagement = pom.dependencyManagement.dependencies.dependency
print 'Verifying that org.jboss.pnc.maven-manipulator.integration-test:parent-children-interdep-child1 is still there and has updated version: '
final def dependency1 = dependencyManagement.find { it.groupId.text() == 'org.jboss.pnc.maven-manipulator.integration-test' && it.artifactId.text() == 'parent-children-interdep-child1' && it.version.text() == '1.0.0.redhat-00002' }
println dependency1
assert dependency1 != null

print 'Verifying that org.jboss.pnc.maven-manipulator.integration-test:parent-children-interdep-child1 is still there and has updated version: '
final def dependency2 = dependencyManagement.find { it.groupId.text() == 'org.jboss.pnc.maven-manipulator.integration-test' && it.artifactId.text() == 'parent-children-interdep-child2' && it.version.text() == '1.0.0.redhat-00002' }
println dependency2
assert dependency2 != null

print 'Verifying that junit:junit was not added (from pom.xml): '
final def dependency3 = dependencyManagement.find { it.groupId.text() == 'junit' && it.artifactId.text() == 'junit' }
println dependency3
assert dependency3.isEmpty()

print 'Verifying that com.yammer.metrics:metrics-core was not added (from child1/pom.xml): '
final def dependency4 = dependencyManagement.find { it.groupId.text() == 'com.yammer.metrics' && it.artifactId.text() == 'metrics-core' }
println dependency4
assert dependency4.isEmpty()

print 'Verifying that there are no other dependencies (and duplicates were removed): '
final def size = dependencyManagement.size()
println size
assert size == 2
