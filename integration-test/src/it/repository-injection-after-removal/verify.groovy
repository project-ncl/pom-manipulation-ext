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

// We should have no profile repositories after removal
def profile = pom.profiles.children().find { it.id.text() == 'extra-repositories' }
assert profile.repositories.text().size() == 0
assert profile.pluginRepositories.text().size() == 0
assert profile.reporting.text().size() == 0

// We should have two repositories after injection
assert pom.repositories.children().size() == 2
assert pom.pluginRepositories.children().size() == 2


// Check that jboss-public-repository-group repository is injected
def repository = pom.repositories.repository.find { it.id.text() == 'jboss-public-repository-group' }
assert repository != null
