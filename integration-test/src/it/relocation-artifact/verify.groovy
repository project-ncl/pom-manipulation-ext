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

def dependency = pom.dependencies.dependency.find { it.groupId.text() == "junit" }
assert dependency != null
assert dependency.version.text() == "4.1"
assert dependency.artifactId.text() == "newlibrary"

dependency = pom.dependencies.dependency.find { it.groupId.text() == "com.junit" }
assert dependency != null
assert dependency.version.text() == "3.8.2"
assert dependency.artifactId.text() == "another-library"

dependency = pom.dependencies.dependency.find { it.groupId.text() == "org.foobar" }
assert dependency != null
assert dependency.version.text() == "1.1"

def passed = false
pom.properties.each {
    if ( it.text().contains ("slf4j-api") )
    {
        passed = true
    }
}
assert (passed == true)

dependency = pom.dependencies.dependency.find { it.groupId.text() == "org.goots.maven.extensions" }
assert dependency != null
assert dependency.version.text() == "1.8"
assert dependency.artifactId.text() == "alt-deploy-maven-extension"
assert dependency.exclusions.exclusion.groupId == "javax.inject"
assert dependency.exclusions.exclusion.artifactId == "javax.noinject"

dependency = pom.dependencies.dependency.find { it.groupId.text() == "org.goots.maven" }
assert dependency != null
assert dependency.version.text() == "1.0"
exclusion = dependency.exclusions.exclusion.find { it.groupId.text() == "javax.inject" }
assert exclusion.artifactId == "javax.inject"
exclusion = dependency.exclusions.exclusion.find { it.groupId.text() == "org.foobar" }
assert exclusion.artifactId == "xxx"
