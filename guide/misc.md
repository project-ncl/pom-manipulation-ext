---
title: Miscellaneous PME Manipulations
---

# Miscellaneous PME Manipulations

* Contents
{:toc}

## Overview

In addition to the main [project-version](project-version-manip.html), [dependency](dep-manip.html), and [plugin](plugin-manip.html) manipulations, PME offers several smaller features. These feaures fall into two main categories:

* POM Cleanup
* Build Management

## Profile Handling

PME will by default **only** scan those profiles that are active in the project. To disable this set `scanActiveProfiles` to false to scan all profiles.

Note: This is **only** for blocks such as dependencies/plugins within profiles - currently PME does **NOT** support
module blocks within profiles and instead will read all available POMs

Note: This will only detect those profiles explicitly activated via -P ; property activation will not be correctly detected.

<table bgcolor="#ffff00">
<tr>
<td>
<p><b>NOTE</b>: As of PME 3.3 (December 2018) the default for <i>scanActiveProfiles</i> changed from false to true.</p>
The profile scanning will detect usage of properties, activeByDefault, environmental activation (e.g. JDK) etc. However this assumes the properties <i>are</i> passed into the PME invocation for it to detect and process them. It is still possible to explicitly activate profiles via the CLI <code>-P</code> parameters.
</td>
</tr>
</table>

<table bgcolor="#00ff00">
<tr>
<td>
<p><b>TIP</b>: It is highly recommended that if using the PME CLI, the same profiles are activated using <code>-P</code> as will be activated for the build process in order to get a consistent result.</p>
</td>
</tr>
</table>


## POM Cleanup

### Version Range Resolving

PME will automatically resolve ranges in versions in plugins and dependencies. This is active by default and may be disabled by setting `resolveRanges` to false. It will replace the range by a suitable concrete version determined by resolving the remote metadata and finding the largest version that matches the range specification.

**Note:** Currently ranges within properties are not supported.

### Repository And Reporting Removal

If the property `repoReportingRemoval` (*Deprecated property `repo-reporting-removal` for versions **3.8.1 and prior***) is set, PME will remove all reporting and repository sections (including profiles) from the POM files (default: off).

Repository declarations in the POM are considered a bad build smell, since over time they may become defunct or move.

If the property `repoRemovalIgnorelocalhost` (*Deprecated property `repo-removal-ignorelocalhost` for versions **3.8.1 and prior***) is set (default: false) PME will not remove repositories that contain the following definitions

* file://
* (http or https)://localhost
* (http or https)://127.00.1
* (http or https)://::1

Occasionally a project's more complex example/quickstart may have a local repository definition; this allows those to be preserved.

Additionally, most project rebuilders aren't interested in hosting their own copy of the project's build reports or generated website; therefore, the reporting section only adds more plugin artifacts to the list of what must be present in the environment for the build to succeed. Eliminating this section simplifies the build and reduces the risk of failed builds.

If the property `repoRemovalBackup` (*Deprecated property `repo-removal-backup` for versions **3.8.1 and prior***) (default value: off) is set to
* `settings.xml` a backup of any removed sections will be created in the top level directory.
* `<path to file>` a backup of any removed sections will be created in the specified file.

### `project.version` Expression Replacement

The extension will automatically replace occurences of the property expression `${project.version}` in POMs (of packaging type `pom`).

This avoids a subtle problem that occurs when another project with inherits from this POM. If the child POM (the one that declares the `<parent/>`) specifies its own version **and that version is different from the parent**, that child version will be used to resolve `${project.version}` instead of the intended (parent) version. Resolving these expressions when `packaging` is set to `pom` (the only type of POM that can act as a parent) prevents this from occurring.

This behavior may be configured by setting the property `enforceProjectVersion` (*Deprecated property `enforce-project-version` for versions **3.8.1 and prior***):

    -DenforceProjectVersion=on|off

As explained above, the default is `on`.

## Build Management

### Parent Injection

PME supports injection of a parent GAV declared explicitly defined by the user e.g.

    mvn install -DparentInjection=org.jboss:jboss-parent:11

### Profile Injection

PME supports injection of profiles declared in multiple remote comma separated POM files. Simply supply a remote management POM:

    mvn install -DprofileInjection=org.foo:profile-injection:1.0,org.company:pluginMgrB:2.0

The extension will, for every profile in the remote POM files, replace or add it to the local top level POM file.

**Note:** for any existing profile in the modified POM that specifies `activeByDefault`, this activation option will be removed so profiles are not accidentally disabled due to its exclusive semantics.

**Note:** If the POM is specified in the format `-DprofileInjection=org.foo:profile-injection:1.0-rebuild` i.e. a rebuild suffix is specified *but without a numeric portion* then PME will automatically replace the POM GAV with the latest suffix via a REST call. For instance, assuming the latest suffix is rebuild-3, the above will be replaced implicitly by `-DprofileInjection=org.foo:profile-injection:1.0-rebuild-3`.

### Profile Removal

PME supports removal of profiles as indicated by a comma separated list of profile IDs.

    mvn install -DprofileRemoval=profileOne,profileTwo

### Repository Injection

PME supports injection of remote repositories. Supply a remote repository management POM:

	mvn install -DrepositoryInjection=org.foo:repository-injection:1.0

The extension will resolve a remote POM file and inject remote repositories to either the local top level POM file or the POM(s) specified by the property `repositoryInjectionPoms` (which should be in the form of a comma separated list e.g. `org.myproject:mychild`). The list also supports a remote HTTP file containing a comma or newline separated list of GAs. If there is a local repository with id identical to the injected one, it is overwritten. The `repositoryInjectionPoms` property supports wildcards on the _artifactId_ e.g.

    repositoryInjectionPoms=org.commonjava.maven.ext.wildcard:*


### Property Override

PME may also be used to override properties prior to interpolating the model. Multiple property mappings can be overridden using a similar pattern to dependencies via a remote property management pom.

    mvn install -DpropertyManagement=org.foo:property-management:10

This will inject the properties at the inheritance root(s). It will also, for every injected property, find any matching property in the project and overwrite its value.

Overriding properties can be a simple, minimalist way of controlling build behavior if the appropriate properties are already defined.
