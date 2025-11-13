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
import groovy.xml.XmlSlurper
import groovy.xml.XmlUtil
import org.jboss.pnc.mavenmanipulator.core.groovy.BaseScript
import org.jboss.pnc.mavenmanipulator.core.groovy.InvocationStage
import org.jboss.pnc.mavenmanipulator.core.groovy.PMEBaseScript
import org.jboss.pnc.mavenmanipulator.core.groovy.InvocationPoint

@InvocationPoint( invocationPoint = InvocationStage.PREPARSE )
@PMEBaseScript BaseScript pme

assert pme.getBaseDir() != null
assert pme.getGAV() == null
assert pme.getProjects().isEmpty()

def pomFile = new File( pme.getBaseDir(), 'interpolated-pom.xml' )

if ( !pomFile.exists() )
{
    pomFile = new File( pme.getBaseDir(), 'pom.xml' )
}

println "Slurping POM: ${pomFile.getAbsolutePath()}"

final def pom = new XmlSlurper( false, false ).parse( pomFile )

println 'Original version: ' + pom.version
assert pom.version == '1.0-SNAPSHOT'
pom.version = '2.0'
println 'New Version: ' + pom.version
assert pom.version == '2.0'

final def profileToRemove = pom.profiles.'**'.find
{
    final profile -> profile.id.text() == 'remove'
}

assert profileToRemove != null

profileToRemove.replaceNode
{

}

pomFile.write XmlUtil.serialize( pom )
