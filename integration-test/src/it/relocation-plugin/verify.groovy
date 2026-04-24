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

def plugin1 = pom.build.plugins.plugin.find { it.groupId.text() == "org.wildfly.plugins" && it.artifactId.text() == "wildfly-maven-plugin" }
assert plugin1.size() != 0
assert plugin1.executions.execution.configuration.groupId.text() == "org.wildfly"
assert plugin1.executions.execution.configuration.artifactId.text() == "wildfly-dist"
assert plugin1.executions.execution.configuration.version.text() == "18.0.1.Final"

def plugin2 = pom.build.plugins.plugin.find { it.groupId.text() == "org.apache.maven.plugins" && it.artifactId.text() == "maven-dependency-plugin" }
assert plugin2.size() != 0

def artifactItem1 = plugin2.executions.execution.configuration.artifactItems.artifactItem.find { it.groupId.text() == "junit" && it.artifactId.text() == "junit" }
assert artifactItem1.size() != 0
assert artifactItem1.version.text().contains("junitProp")

def artifactItem2 = plugin2.executions.execution.configuration.artifactItems.artifactItem.find { it.groupId.text() == "org.slf4j" && it.artifactId.text() == "slf4j-api" }
assert artifactItem2.size() != 0
assert artifactItem2.version.text() == "1.7.30"

def profile1 = pom.profiles.profile.find { it.id.text() == "one" }
assert profile1.size() != 0
def plugin3 = profile1.build.plugins.plugin.find { it.groupId.text() == "org.apache.maven.plugins" && it.artifactId.text() == "maven-dependency-plugin" }
assert plugin3.size() != 0

def artifactItem3 = plugin3.executions.execution.configuration.artifactItems.artifactItem.find { it.groupId.text() == "junit" && it.artifactId.text() == "junit" }
assert artifactItem3.size() != 0
assert artifactItem3.version.text().contains("junitProp")

def artifactItem4 = plugin3.executions.execution.configuration.artifactItems.artifactItem.find { it.groupId.text() == "org.slf4j" && it.artifactId.text() == "slf4j-api" }
assert artifactItem4.size() != 0
assert artifactItem4.version.text() == "1.7.30"

def plugin4 = pom.build.plugins.plugin.find { it.groupId.text() == "com.soebes.maven.plugins" && it.artifactId.text() == "iterator-maven-plugin" }
assert plugin4.size() != 0


int passed = 0
pom.properties.each {
    if ( it.text().contains ("4.1") )
    {
        passed++
    }
    if ( it.text().contains ("shade") )
    {
        passed++
    }
}
assert (passed == 2)

plugin1 = pom.build.plugins.plugin.find { it.groupId.text() == "com.soebes.maven.plugins" }
assert plugin1.artifactId.text() == "iterator-maven-plugin"
