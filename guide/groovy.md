---
title: Groovy Script Injection
---

* Contents
{:toc}

### Overview

PME offers the ability to run arbitrary groovy scripts on the sources prior to running the build. This allows PME to be extensible by the user and to process other files not just Maven POMs.

### Configuration

If the property `-DgroovyScripts=<value>,....` is set, PME will load the remote Groovy script file.

The argument should a comma separated list of either:

* group:artifact:version (with optional type and classifiers).
* A http:// or https:// URL.
* A file:// URL

If using a GAVTC, the remote groovy file can be deployed by e.g.

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.codehaus.mojo</groupId>
      <artifactId>build-helper-maven-plugin</artifactId>
      <version>1.5</version>
      <inherited>false</inherited>
      <executions>
        <execution>
          <id>attach-artifacts</id>
          <phase>package</phase>
          <goals>
            <goal>attach-artifact</goal>
          </goals>
          <configuration>
            <artifacts>
              <artifact>
                <file>Sample.groovy</file>
                <type>groovy</type>
              </artifact>
            </artifacts>
          </configuration>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

The deployed file can then be used with e.g.

    mvn -DgroovyScripts=org.commonjava.maven.ext:depMgmt1:groovy:1.0 clean install

### Groovy Scripts

Each groovy script will be run on the execution root (i.e. where Maven is invoked).

<table bgcolor="#ffff00">
<tr>
<td>
    <b>NOTE</b> : As of PME 5.0 the API has changed: <i>org.jboss.pnc.mavenmanipulator.core.groovy.BaseScript</i> instead of <i>org.commonjava.maven.ext.core.groovy.BaseScript</i> and <i>org.jboss.pnc.mavenmanipulator.common.model.Project</i> instead of <i>org.commonjava.maven.ext.common.model.Project</i>. The Atlas dependency has also changed its groupId from <i>org.commonjava.maven.atlas.ident.ref.SimpleProjectRef</i> to <i>org.commonjava.atlas.maven.ident.ref.SimpleProjectRef</i>
</td>
</tr>
<tr>
<td>
    <b>NOTE</b> : As of PME 5.0 the groupId and java packaging has changed: <i>org.jboss.pnc.mavenmanipulator</i> instead of <i>org.commonjava.maven.ext</i> and <i>org/jboss/pnc/mavenmanipulator</i> instead of <i>org/commonjava/maven/ext</i>
</td>
</tr>
<tr>
<td>
    <b>NOTE</b> : Prior to PME 3.5 groovy manipulator precedence was controlled via a flag ; by setting <i>groovyManipulatorPrecedence</i> to <i>FIRST</i> instead of the default <i>LAST</i> value. Further, annotation was different; the scripts used the <a href="http://docs.groovy-lang.org/latest/html/gapi/groovy/transform/BaseScript.html">BaseScript</a> annotation e.g.
<br/>
<i>@BaseScript org.commonjava.maven.ext.core.groovy.BaseScript pme</i>
</td>
</tr>
<tr>
<td>
    <b>NOTE</b> : As of PME 3.0 the API has changed: <i>org.commonjava.maven.ext.core.groovy.BaseScript</i> instead of <i>org.commonjava.maven.ext.manip.groovy.BaseScript</i> and <i>org.commonjava.maven.ext.common.model.Project</i> instead of <i>org.commonjava.maven.ext.manip.model.Project</i>
</td>
</tr>
</table>

Each script <b>must</b> use the following annotations:

<table bgcolor="#ffff00">
<tr>
<td>
    <b>NOTE</b> : For PME versions <b>from</b> 3.8.1, the <code>PMEInvocationPoint</code> annotation has been deprecated and developers should use <code>InvocationPoint</code> in preference. From version 4.3, it is <b>not</b> supported.
</td>
</tr>
</table>

```groovy
import org.jboss.pnc.mavenmanipulator.core.groovy.BaseScript
import org.jboss.pnc.mavenmanipulator.core.groovy.InvocationStage
import org.jboss.pnc.mavenmanipulator.core.groovy.PMEBaseScript
import org.jboss.pnc.mavenmanipulator.core.groovy.InvocationPoint
...

@InvocationPoint(invocationPoint = InvocationStage.FIRST)
@PMEBaseScript BaseScript pme
```

The API can then be invoked by e.g.

```groovy
pme.getBaseDir()
```

<b>NOTE</b> : Be careful not to use <code>pme.getProperties()</code> or <code>pme.getProject().getProperties()</code> as that actually calls the [Groovy language API](http://docs.groovy-lang.org/latest/html/api/org/codehaus/groovy/runtime/DefaultGroovyMethods.html#getProperties(java.lang.Object))

#### Invocation stages

In the example script, we saw the use of the `@InvocationPoint` annotation which controls when the script is run. It
takes a single argument, `invocationPoint`, with the type `InvocationStage`. The possible values for `InvocationStage`
are `PREPARSE`, `FIRST`, `LAST`, and `ALL`. These values are relative to when the manipulations to the POM files are
made. The table below provides a description of the invocation stages available for running a script.

| Stage      | Description                                                                                                                                                                              | Since      |
|------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------|
| `PREPARSE` | Runs the script _prior to parsing any POM files_ and before any manipulations have been performed, thus allowing the modification of POM files on disk before they are read into memory. | 4.6        |
| `FIRST`    | Runs the script _first_ after all POM files have been read into memory, but before any manipulations have been performed.                                                                | 3.5        |
| `LAST`     | Runs the script _last_ after all modifications to the in-memory POM files have been performed.                                                                                           | 3.5        |
| ~~`BOTH`~~ | Runs the script during stages `FIRST` and `LAST`. _Note that as of version 4.6, `BOTH` has been replaced by `ALL`_.                                                                      | [3.5, 4.5] |
| `ALL`      | Runs the script during _all_ possible stages: `PREPARSE`, `FIRST`, and `LAST`.  The `getInvocationStage()` API can be used to determine in which stage the script is currently running.  | 4.6        |

<table bgcolor="#ffff00">
<tr>
<td>
    <b>NOTE</b> : It is safe to modify POM files on disk during the <code>PREPARSE</code> stage. However, if you modify
a POM file on disk during any other stage, the modifications will be overwritten when the in-memory POM file is written
back out to disk. To alter a POM file in memory, call <code>Project.getModel()</code> to retrieve the
<code>org.apache.maven.model.Model</code> instance and modify that instead, .e.g.,
<code>pme.getProject().getModel().setVersion( "1.0.0" )</code>.
</td>
</tr>
</table>

### API

The following API is available:

| Method | Description | Since      | Valid in Stage      |
| -------|:------------|:-----------|:--------------------|
| ~~[Map](https://docs.oracle.com/javase/7/docs/api/java/util/Map.html)<[ProjectVersionRef](https://github.com/Commonjava/atlas/blob/master/identities/src/main/java/org/commonjava/atlas/maven/ident/ref/ProjectVersionRef.java), [String](https://docs.oracle.com/javase/7/docs/api/java/lang/String.html)> translateVersions([List](https://docs.oracle.com/javase/7/docs/api/java/util/List.html)<[Project](https://github.com/project-ncl/pom-manipulation-ext/blob/main/common/src/main/java/org/commonjava/maven/ext/common/model/Project.java)>)~~ | Translate the versions. _Removed in 4.4; use `lookupProjectVersions` or `lookupVersions` instead_. | [1.7, 4.3] | ~~`FIRST`, `LAST`~~ |
| [File](https://docs.oracle.com/javase/7/docs/api/java/io/File.html) getBaseDir() | Get the working directory (the execution root). | 1.10 | `FIRST`, `LAST`     |
| [ProjectVersionRef](https://github.com/Commonjava/atlas/blob/master/identities/src/main/java/org/commonjava/atlas/maven/ident/ref/ProjectVersionRef.java) getGAV() | Get the GAV of the current project. | 1.10 | `FIRST`, `LAST`     |
| [Project](https://github.com/project-ncl/pom-manipulation-ext/blob/main/common/src/main/java/org/jboss/pnc/mavenmanipulator/common/model/Project.java) getProject() | Return the current Project. | 1.10 | `FIRST`, `LAST`     |
| [List](https://docs.oracle.com/javase/7/docs/api/java/util/List.html)<[Project](https://github.com/project-ncl/pom-manipulation-ext/blob/main/common/src/main/java/org/jboss/pnc/mavenmanipulator/common/model/Project.java)> getProjects() | Returns the entire collection of Projects. | 1.10 | `FIRST`, `LAST`     |
| [Properties](https://docs.oracle.com/javase/7/docs/api/java/util/Properties.html) getUserProperties() | Get the user properties. | 2.0 | `ALL`               |
| [ModelIO](https://github.com/project-ncl/pom-manipulation-ext/blob/main/io/src/main/java/org/jboss/pnc/mavenmanipulator/io/ModelIO.java) getModelIO() | Return a ModelIO instance for artifact resolving. | 3.2 | `ALL`               |
| [MavenSessionHandler](https://github.com/project-ncl/pom-manipulation-ext/blob/main/common/src/main/java/org/jboss/pnc/mavenmanipulator/common/session/MavenSessionHandler.java) getSession() | Return the current session handler. | 3.2 | `ALL`               |
| [InvocationStage](https://github.com/project-ncl/pom-manipulation-ext/blob/main/core/src/main/java/org/jboss/pnc/mavenmanipulator/core/groovy/InvocationStage.java) getInvocationStage() | Return the current stage of the groovy manipulation. | 3.5 | `ALL`               |
| void inlineProperty([Project](https://github.com/project-ncl/pom-manipulation-ext/blob/main/common/src/main/java/org/jboss/pnc/mavenmanipulator/common/model/Project.java), [String](https://docs.oracle.com/javase/7/docs/api/java/lang/String.html)) | Allows the specified property to be inlined in any dependencies/dependencyManagement. This is useful to split up properties that cover multiple separate projects. _Note that as of version 3.8, the `String` argument changed from `group:artifact` to `propertyKey`_. | 3.5 | `FIRST`, `LAST`     |
| void inlineProperty([Project](https://github.com/project-ncl/pom-manipulation-ext/blob/main/common/src/main/java/org/jboss/pnc/mavenmanipulator/common/model/Project.java), [ProjectRef](https://github.com/Commonjava/atlas/blob/master/identities/src/main/java/org/commonjava/atlas/maven/ident/ref/ProjectRef.java)) | Allows the specified group:artifact property to be inlined in any dependencies/dependencyManagement. This is useful to split up properties that cover multiple separate projects. Supports `*` as a wildcard for artifactId. | 3.8 | `FIRST`, `LAST`     |
| void overrideProjectVersion([ProjectVersionRef](https://github.com/Commonjava/atlas/blob/master/identities/src/main/java/org/commonjava/atlas/maven/ident/ref/ProjectVersionRef.java)) | The specified GAV will be queried from DA for its current suffix and that suffix be used in [versionSuffix](project-version-manip.html#manual-version-suffix) instead of any [versionIncrementalSuffix](project-version-manip.html#automatic-version-increment). | 3.8 | `FIRST`, `LAST`     |
| void reinitialiseSessionStates() | This will re-initialise any State linked to this session. This is useful if the groovy scripts have altered the user properties. | 3.8 | `ALL`               |
| [Logger](https://www.javadoc.io/doc/org.slf4j/slf4j-api/1.7.30/org/slf4j/Logger.html) getLogger() | This will return the current SLF4J Logger instance. | 3.8.2 | `ALL`               |
| [FileIO](https://github.com/project-ncl/pom-manipulation-ext/blob/main/io/src/main/java/org/jboss/pnc/mavenmanipulator/io/FileIO.java) getFileIO() | This will return a FileIO instance for remote File resolving. | 4.0 | `ALL`               |
| [PomIO](https://github.com/project-ncl/pom-manipulation-ext/blob/main/io/src/main/java/org/jboss/pnc/mavenmanipulator/io/PomIO.java) getPomIO() | This will return a PomIO instance for parsing POM models. | 4.0 | `ALL`               |
| [Translator](https://github.com/project-ncl/pom-manipulation-ext/blob/main/io/src/main/java/org/jboss/pnc/mavenmanipulator/io/rest/Translator.java) getRESTAPI() throws [ManipulationException](https://github.com/project-ncl/pom-manipulation-ext/blob/main/common/src/main/java/org/jboss/pnc/mavenmanipulator/common/ManipulationException.java) | Gets a configured VersionTranslator to make REST calls to DA. | 4.0 | `ALL`               |
| [Map](https://docs.oracle.com/javase/7/docs/api/java/util/Map.html)<[ProjectVersionRef](https://github.com/Commonjava/atlas/blob/master/identities/src/main/java/org/commonjava/atlas/maven/ident/ref/ProjectVersionRef.java), [String](https://docs.oracle.com/javase/7/docs/api/java/lang/String.html)> lookupProjectVersions([List](https://docs.oracle.com/javase/7/docs/api/java/util/List.html)<[Project](https://github.com/project-ncl/pom-manipulation-ext/blob/main/common/src/main/java/org/jboss/pnc/mavenmanipulator/common/model/Project.java)>) throws [RestException](https://github.com/project-ncl/pom-manipulation-ext/blob/main/io/src/main/java/org/jboss/pnc/mavenmanipulator/io/rest/RestException.java) | Lookup versions (e.g. for a project) ignoring DA suffix priority schemes returning the latest version. | 4.4 | `FIRST`, `LAST`     |
| [Map](https://docs.oracle.com/javase/7/docs/api/java/util/Map.html)<[ProjectVersionRef](https://github.com/Commonjava/atlas/blob/master/identities/src/main/java/org/commonjava/atlas/maven/ident/ref/ProjectVersionRef.java), [String](https://docs.oracle.com/javase/7/docs/api/java/lang/String.html)> lookupVersions([List](https://docs.oracle.com/javase/7/docs/api/java/util/List.html)<[Project](https://github.com/project-ncl/pom-manipulation-ext/blob/main/common/src/main/java/org/jboss/pnc/mavenmanipulator/common/model/Project.java)>) throws [RestException](https://github.com/project-ncl/pom-manipulation-ext/blob/main/io/src/main/java/org/jboss/pnc/mavenmanipulator/io/rest/RestException.java) | Lookup versions respecting DA suffix priority schemes which will return the best matched version. | 4.4 | `FIRST`, `LAST`     |

### Utility Functions

Currently, two main utility functions are provided:

#### inlineProperty

This function is typically used to deal with the problem where an upstream may use a single property for several distinct SCM Builds. This can lead to issues, so by 'inlining' the property it can avoid them.

<table bgcolor="#ffff00">
<tr>
<td>
    <b>NOTE</b> : For PME versions <b>after</b> 3.8, the inlineProperty function that takes a <code>ProjectRef</code> will allow a wildcard (<code>*</code>) for artifactId.
</td>
</tr>
</table>

#### overrideProjectVersion

Occasionally upstream projects have circular dependencies in their build. This can typically happen if the first build creates a BOM that is used by multiple subsequent builds. If rebuilds (primarily with a suffix) occur then the versions can get out of sync and build errors arise. This function effectively 'locks' the subsequent builds to the suffix value of the first.

![circular example][circular]

The above diagram shows two different SCM builds. The first has a sub-module that is a BOM that references the following SCM builds. This BOM is imported into the parent's `dependencyManagement`. The second SCM build inherits this parent. If the first has been rebuilt twice (hence the `rebuild-2` suffix) then the BOM will refer to the incorrect version of SCM Build 2 (`1.0.rebuild-2` instead of `1.0.rebuild-1`). By using `overrideProjectVersion` it is possible to force SCM Build 2 to use suffix `rebuild-2` thereby making it have the correct version.

### Example

A typical groovy script that alters a JSON file on disk might be:

```groovy
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.jboss.pnc.mavenmanipulator.core.groovy.BaseScript
import org.jboss.pnc.mavenmanipulator.core.groovy.InvocationStage
import org.jboss.pnc.mavenmanipulator.core.groovy.PMEBaseScript
import org.jboss.pnc.mavenmanipulator.core.groovy.InvocationPoint

@InvocationPoint(invocationPoint = InvocationStage.FIRST)
@PMEBaseScript BaseScript pme

@Slf4j
public class Processor
{
    File basedir

    private void processJson(Map n) {
        ...
    }

    def execute() {
        log.info("Running ShrinkwrapProcessor...")

        def shrinkwrap = new File(basedir.toString() + File.separator + "shrink.json")

        log.info("shrinkwrap json is " + shrinkwrap)

        if (shrinkwrap.exists()) {
            log.info ("Found file {}", shrinkwrap)

            LinkedHashMap json = new JsonSlurper().parseText(shrinkwrap.text)

            processJson(json)

            shrinkwrap.write(JsonOutput.prettyPrint(JsonOutput.toJson(json)))
        }
    }
}

def Processor sp = new Processor(basedir:pme.getBaseDir()))
sp.execute()
```

### Developing Groovy Scripts

To make it easier to develop scripts for both PME (this project) and [GME](https://github.com/project-ncl/gradle-manipulator) an example project has been set up. The [manipulator-groovy-examples](https://github.com/project-ncl/manipulator-groovy-examples) provides a framework to develop and test such scripts.

[circular]: ../images/circular.png "Circular Example"
