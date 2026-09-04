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

// The input POM has version 1.0.0.Final (no rhlw reference).
// With enforceVersionPrefix=rhlw, versionIncrementalSuffix=n, versionIncrementalSuffixPadding=5:
//   1. Enforcement normalises the project version to 1.0.0.Final-rhlw-00000 before the REST lookup.
//   2. The mock server receives 1.0.0.Final-rhlw-00000 and returns a best-match with the n suffix.
//   3. The calculator appends n to the enforced base and produces 1.0.0.Final-rhlw-00000-n-00001.

def pomFile = new File( basedir, 'pom.xml' )
System.out.println( "Slurping POM: ${pomFile.getAbsolutePath()}" )

def pom = new XmlSlurper().parse( pomFile )

System.out.println( "POM Version: ${pom.version.text()}" )
assert pom.version.text().equals( '1.0.0.Final-rhlw-00000-n-00001' )

// Verify the build log shows the REST request carried the enforced version, not the original.
def buildLog = new File( basedir, 'build.log' )
def logText = buildLog.getText()
assert logText.contains( '1.0.0.Final-rhlw-00000' ) : "Build log did not contain enforced version 1.0.0.Final-rhlw-00000"
