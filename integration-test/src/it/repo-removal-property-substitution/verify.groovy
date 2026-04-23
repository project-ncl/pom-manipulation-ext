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

def childPomFile = new File( basedir, 'child/pom.xml' )
System.out.println( "Slurping POM: ${pomFile.getAbsolutePath()}" )

def childPom = new XmlSlurper().parse( pomFile )
System.out.println( "POM Version: ${pom.version.text()}" )

assert pom.repositories.text().size()  == 0
assert pom.pluginRepositories.text().size() == 0
assert pom.reporting.text().size() == 0

def profile = childPom.profiles.children().find { it.id.text() == 'extra-repositories' }
assert profile.repositories.text().size() == 0
assert profile.pluginRepositories.text().size() == 0
assert profile.reporting.text().size() == 0

def repodir = new File('@localRepositoryUrl@', "${pom.groupId.text().replace('.', '/')}/${pom.artifactId.text()}/${pom.version.text()}" )

def repopom = new File( repodir, "${pom.artifactId.text()}-${pom.version.text()}.pom" )
System.out.println( "Checking for installed pom: ${repopom.getAbsolutePath()}")
assert repopom.exists()

def settingsFile = new File ( basedir, 'settings.xml' )
assert settingsFile.exists()

def settings = new XmlSlurper().parse( settingsFile )
assert settings.profiles.children().find( { it.id.text() == 'extra-repositories' } ).size() == 1
assert settings.profiles.children().find( { it.id.text() == 'removed-by-pme' } ).size() == 1
profile = settings.profiles.children().find { it.id.text() == 'removed-by-pme' }
assert profile.repositories.text().size() != 0

assert !settings.text().contains("dummy-profile")
assert !settings.text().contains("\$")
assert settings.text().contains("https://repository.jboss.org/nexus/content/groups/public/")

int counter = 0
settingsFile.eachLine { String line ->
    if ( line.contains('jboss-public-repository-group<'))
        {
        counter++;
    }
}
assert counter==2

counter = 0
settings.activeProfiles.children().each{
    if ( it.text() == 'removed-by-pme')
        {
        counter++;
    } else if ( it.text() == 'extra-repositories')
        {
        counter++
    }
    else
        {
        fail ("Invalid value")
    }
}
assert counter==2
