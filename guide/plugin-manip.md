---
title: "Plugin Manipulation"
---

* Contents
{:toc}

### Overview

PME can align plugin versions and configuration using a similar pattern to [dependencies](dep-manip.html). It also has the ability to standardize the use of `skip` flags that determine whether the `maven-install-plugin` and `maven-deploy-plugin` execute. Finally, PME can inject plugin executions for the `project-sources-maven-plugin` and `buildmetadata-maven-plugin`.

<table bgcolor="#00ff99">
<tr>
<td>
    <b>NOTE</b> : Several configuration flags are shared with  <a href="dep-manip.html">Dependency Manipulator</a>.
</td>
</tr>
</table>

### Plugin Source

#### BOM and REST

There are two sources of plugins used to align to in PME. The property `pluginSource` is used to alter the behaviour of how PME handles the multiple sources of plugin information. The `BOM` value is that PME will use the BOM (i.e. Remote POM) source. Alternatively the `REST` source may be specified to use only the REST Endpoint information. However by setting the property to either `RESTBOM` or `BOMREST` it will instead merge the two sets of values. With `RESTBOM` precendence is given to the REST information and for `BOMREST` precendence is given to the BOM information. If the setting is `NONE` no remote alignment will be performned.

**Note**: If this is not specified the default value for `pluginSource` will match the value for `dependencySource`. Therefore it is only necessary to set `pluginSource` if a *different* value to `dependencySource` is needed.


#### Remote POM

A remote plugin management POM is used to specify the plugin versions (and configuration) to inject:

    mvn install -DpluginManagement=org.jboss:jboss-parent:10

This will inject all `<pluginManagement/>` versions, executions and configuration from the remote POM into the local POM. As with [dependency management](dep-manip.html), multiple remote plugin management POMs can be specified on the command line using a comma separated list of GAVs. The list also supports a remote HTTP file containing a comma or newline separated list of GAVs. The first POM specified will be given the highest priority if conflicts occur.

    mvn install -DpluginManagement=org.company:pluginMgrA:1.0,org.company:pluginMgrB:2.0

**Note:** If the BOM is specified in the format `-DpluginManagement=org.foo:my-dep-pom:1.0-rebuild` i.e. a rebuild suffix is specified *but without a numeric portion* then PME will automatically replace the BOM GAV with the latest suffix via a REST call. For instance, assuming the latest suffix is rebuild-3, the above will be replaced implicitly by `-DpluginManagement=org.foo:my-dep-pom:1.0-rebuild-3`.

#### REST Endpoint

For information on the REST Endpoint see [here](dep-manip.html#rest-endpoint)

### Direct/Transitive Dependencies

By default the extension will inject _all_ plugins declared in the remote BOM. If the option `overrideTransitive` is set to `false` to then only plugins used will be overridden.

    mvn install -DpluginManagement=org.foo:my-dep-pom:1.0 -DoverrideTransitive=false

**Note**: overrideTransitive is also used by the Dependency Manipulator.



<table bgcolor="#ffff00">
<tr>
<td>
    <b>NOTE</b> : From version 4.0 plugin overrides are supported as follows:
</td>
</tr>
</table>

### Overrides

In a multi-module build it is considered good practice to coordinate plugin version among the modules using plugin management.  In other words, if module A and B both use plugin X, both modules should use the same version of plugin X.  Therefore, the default behaviour of this extension is to use a single set of plugin versions applied to all modules.

It is possible to flexibly override or exclude a plugin globally or on a per module basis. The property starts with `pluginOverride.` and has the following format:

    mvn install -DpluginOverride.[groupId]:[artifactId]@[moduleGroupId]:[moduleArtifactId]=[version]...

**Note:** Multiple overrides may be added using multiple instances of `-DpluginOverride...`.

#### Global Version Override

Sometimes it is more convenient to use the command line rather than a BOM. Therefore extending the above it is possible to set the version of a plugin via:

    mvn install -DpluginOverride.junit:junit@*=4.10-rebuild-10

This will, throughout the entire project (due to the wildcard), apply the explicit 4.10-rebuild-10 version to the junit:junit plugin.

**Note:** Explicit overrides like this will take precedence over strict alignment and the BOM.


#### Per-Module Version Override

However, there are certain cases where it is useful to use different versions of the same plugin in different modules.  For example, if the project includes integration code for multiple versions of a particular API. In that case it is possible to apply a version override to a specific module of a multi-module build. For example to apply an explicit plugin override only to module B of project foo.

    mvn install -DpluginOverride.junit:junit@org.foo:moduleB=4.10

**Note:** Explicit overrides like this will take precedence over strict alignment and the BOM.


#### Per-Module Prevention of Override

It is also possible to **prevent overriding plugin versions** on a per module basis:

    mvn install -DpluginOverride.[groupId]:[artifactId]@[moduleGroupId]:[moduleArtifactId]=

For example:

    mvn install -DpluginOverride.junit:junit@org.foo:moduleB=

#### Override Prevention with Wildcards

Likewise, you can prevent overriding a plugin version across the entire project using a wildcard:

    mvn install -DpluginOverride.[groupId]:[artifactId]@*=

For example:

    mvn install -DpluginOverride.junit:junit@*=

Or, you can prevent overriding a plugin version across the entire project where the groupId matches, using multiple wildcards:

    mvn install -DpluginOverride.[groupId]:*@*=

For example:

    mvn install -DpluginOverride.junit:*@*=

#### Per Module Override Prevention with Wildcards

Linking the two prior concepts it is also possible to prevent overriding using wildcards on a per-module basis e.g.

    mvn install -DpluginOverride.*:*@org.foo:moduleB=

This will prevent any alignment within the org.foo:moduleB.

    mvn install -DpluginOverride.*:*@org.foo:*=

This will prevent any alignment within org.foo and all sub-modules within that.
### Strict Mode Version Alignment

For information on strict mode configuration see [here](dep-manip.html#strict-mode-version-alignment)

### Plugin Configurations

If there is an existing local configuration then it will be merged with the remote. The following configuration controls the precedence:

    -DpluginManagementPrecedence=[LOCAL|REMOTE]

Default is `REMOTE` which means the remote configuration takes precedence over local.

If when attempting to merge the remote execution blocks into local, the `<id>`'s clash an exception will be thrown.

### Plugin Relocations

In order to handle the situation where one GAV is changed to another (e.g. from community to product) the relocation manipulator can be used. An optional version component may be added; the version will override any prior version used in the plugin. The artifact override is optional. The relocated GAV is subject to alignment ; if the developer wishes to force a particular version (i.e. one with an existing suffix) they may use `pluginOverride`.

    -DpluginRelocations.oldGroupId:[oldArtifact]@newGroupId:[newArtifactId]=[version],...

**Note:** Multiple relocations may be added using multiple instances of `-DpluginRelocation...`.

### Plugin Removal

If the property `pluginRemoval` (*Deprecated property `plugin-removal` for versions **3.8.1 and prior***) is set, PME will remove the specified plugins from the POM files. The argument should be a comma separated list of group:artifact. The list also supports a remote HTTP file containing a comma or newline separated list of GAs. For example:

    -DpluginRemoval=group:artifact,....

#### Removal of central-publishing-maven-plugin and nexus-staging-maven-plugin

<table bgcolor="#ffff00">
<tr>
<td>
    <b>NOTE</b> : central-publishing-maven-plugin removal available from PME 4.21.
</td>
</tr>
</table>

As nexus-staging-maven-plugin prevents `mvn -DaltDeploymentRepository=... deploy` from working, it is removed by
default starting from version 4.1. If you wish to prevent nexus-staging-maven-plugin from being removed, set the
property `nexusStagingMavenPluginRemoval` to `false`.

From version 4.21 the same policy applies to central-publishing-maven-plugin. To disable it, set the property `centralPublishingMavenPluginRemoval` to `false`.

### Miscellaneous

#### Install and Deploy Skip Flag Alignment

By default, this extension will disable the skip flag on the install and deploy plugins. This is useful for build environments that compare the results of install with those from deploy as a validation step. More generally, suppressing installation or deployment tends to be an aesthetic decision that can have subtle functional consequences. It's usually not really worth the hassle.

This feature does support four modes for alignment, controlled via the `enforceSkip` (*Deprecated property `enforce-skip` for versions **3.8.1 and prior***) command-line property:

1. `none` - (**default**) don't do any alignment
2. `on` - (aliased to `true`) enforce that the skip flag is **enabled**, suppressing install and deploy functions of the build (useful mainly for module-specific overrides. See below)
3. `off` - (aliased to `false`) enforce that the skip flag is **disabled** and that install/deploy functions will execute normally
4. `detect` - detect the flag state of the install plugin in the main pom, and adjust *any* other install- or deploy-plugin references to make their skip flag values consistent. Install plugin declarations in profiles are not considered during detection.

Additionally, the feature supports per-module overrides, which can be specified as:

    -DenforceSkip.org.group.id:artifact-id=(none|on|true|off|false|detect)

#### Project Sources Injection

<table bgcolor="#ffff00">
<tr>
<td>
    <b>NOTE</b> : As of PME 5.1 (Jan 2026) the metadata plugin injection has been removed, the project-sources-maven-plugin has been moved to the project-ncl org and the deprecated property names have been removed..
</td>
</tr>
<tr>
<td>
    <b>NOTE</b> : As of PME 3.14 (Jul 2023) these options are now deprecated and may be removed in a future release.
</td>
</tr>
<tr>
<td>
    <b>NOTE</b> : As of PME 3.0 (May 2018) the defaults for <i>projectSrcSkip</i> and <i>projectMetaSkip</i> have changed from false to true i.e. no plugins will be injected.
</td>
</tr>
</table>

The extension may optionally inject an execution of [project-sources-maven-plugin](https://github.com/project-ncl/project-sources-maven-plugin). This will result in an archive being created containing all project sources **after** this extension has made any modifications to the pom.xml's. The archive will only be created in the execution-root project, and will be attached for installation and deployment using the `project-sources` classifier and `tar.gz` packaging.

To activate injection of the sources plugin, you can use the properties `projectSrcSkip`:

    mvn install -DprojectSrcSkip=false

If unspecified no plugins will be injected. If specified, default versions of the project sources plugin will be injected (currently, version 1.0). To gain more control over this injection, you can specify the versions for project sources and metadata plugins with the properties `projectSrcVersion`.

    mvn install -DprojectSrcVersion=x.y
