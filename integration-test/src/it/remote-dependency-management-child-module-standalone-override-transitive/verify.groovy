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
final def pomFile = new File( basedir, 'pom.xml' )
final def pomChildFile = new File( basedir, 'child/pom.xml' )

println "Slurping POM: ${pomFile.getAbsolutePath()} and ${pomChildFile.getAbsolutePath()}"

final def pom = new XmlSlurper().parse( pomFile )
final def pomChild = new XmlSlurper().parse( pomChildFile )

assert pom.dependencyManagement.dependencies == pomChild.dependencyManagement.dependencies
assert pom.dependencyManagement.dependencies.dependency.size() == 5
