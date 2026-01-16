---
title: "Dependency Manipulation"
---

* Contents
{:toc}

### Overview

PME can override a set of dependency versions using a remote source which may be either a pom (BOM) file or a remote REST endpoint.

### Dependency Source

#### BOM and REST

There are two sources of dependencies used to align to in PME. The property `dependencySource` is used to alter the behaviour of how PME handles the multiple sources of dependency information. The `BOM` value (the default and the behaviour if this property is not specified) is that PME will use the BOM (i.e., Remote POM) source. Alternatively, the `REST` source may be specified to use only the REST Endpoint information. However, by setting the property to either `RESTBOM` or `BOMREST` it will instead merge the two sets of values. With `RESTBOM`, precedence is given to the REST information and for `BOMREST` precedence is given to the BOM information. If the setting is `NONE` no remote alignment will be performed.

#### Remote POM

By default, all dependencies listed in the remote pom will be added to the current build. This has the effect of overriding matching transitive dependencies, as well as those specified directly in the pom.

Use the `dependencyManagement` property to list your BOMs:

    mvn install -DdependencyManagement=org.foo:my-dep-pom:1.0

Multiple remote dependency-management poms can be specified using a comma separated list of GAVs (groupId,
artifactId, version). The list also supports a remote HTTP file containing a comma or newline separated list of GAVs.
The poms are specified in order of priority, so if the remote BOMs contain some of the same dependencies, the versions listed in the first bom in the list will be used.

    mvn install -DdependencyManagement=org.foo:my-dep-pom:1.0,org.bar:my-dep-pom:2.0

**Note:** If the BOM is specified in the format `-DdependencyManagement=org.foo:my-dep-pom:1.0-rebuild` i.e., a rebuild suffix is specified *but without a numeric portion* then PME will automatically replace the BOM GAV with the latest suffix via a REST call. For instance, assuming the latest suffix is rebuild-3, the above will be replaced implicitly by `-DdependencyManagement=org.foo:my-dep-pom:1.0-rebuild-3`.

#### REST Endpoint

Alternatively, rather than using a remote BOM file as a source, it is possible to instruct PME to pre-scan the project, collect up all group:artifact:version's used and call a REST endpoint using the endpoint property `restURL` (provided from the Dependency Analysis tool [here](https://github.com/project-ncl/dependency-analysis), versions >= 2.1, hereinafter referred to as DA) **and** specifying `dependencySource` of `REST`, which will then return a list of possible new versions. Note that the URL should be the subset of the endpoint e.g.,

    http://foo.bar.com/da/rest/v-1

PME will then call the following endpoints

    lookup/maven/latest
    lookup/maven

in that order. By default, PME will pass *all* the GAVs to the endpoint **automatically sizing** the data sent to DA according to the project size. Note that the initial split batches can also be configured manually via `-DrestMaxSize=<...>`. If that value is set to 0, then everything is sent without any auto-sizing. If the endpoint returns a 503 or 504 timeout the batch is automatically split into smaller chunks in an attempt to reduce load on the endpoint and the request retried. It will by default chunk down to size of 4 before aborting. This can be configured with `-DrestMinSize=<...>`.

A boolean flag `restBrewPullActive` flag switches on and off the version lookup in Brew and the default value is false. Switching it off might have positive effect on performance. Finally, the string identifier `restMode` indicates type of versions to lookup. Modes are configurable in DA, so it is needed to check the DA config/consult with DA maintainers for the list of configured modes. Usual modes might be e.g.

- `PERSISTENT`
- `TEMPORARY`
- `SERVICE`
- `SERVICE-TEMPORARY`

with more to be added in the future. The lookup REST endpoint should follow:

<table>
<tr>
   <th id="Parameters">Parameters</th>
   <th id="Returns">Returns</th>
</tr>
<tr>
<td>
   <pre style="font-size: 10px"><code class="language-json">
[
    [ "brewPullActive": true, ]
    [ "mode": "MODE-ID", ]
    {
        "groupId": "org.foo",
        "artifactId": "bar",
        "version": "1.0.0.Final"
    },
    ...
]
    </code></pre>
</td>
<td>
   <pre style="font-size: 10px"><code class="language-json">
[
    {
        "groupId": "org.foo",
        "artifactId": "bar",
        "version": "1.0.0.Final",
        "bestMatchVersion": "1.0.0.Final-rebuild-2",
    },
    ...
]
   </code></pre>
</td>
</tr>
</table>


**Note:** For existing dependencies that reference a property, PME will update this property with the new version. If the property can't be found (e.g., it was inherited), a new one will be injected at the top level. This update of the property's value **may** implicitly align other dependencies using the same property that were not explicitly requested to be aligned.

##### REST Timeouts and retries

In case of a 504 response from DA, by default the operations will be retried after a waiting period of 30 seconds. This value can be optionally configured with `-DrestRetryDuration=<...>`, expressed in seconds.

The underlying HTTP client library responsible for calling the REST endpoints is set by default with a socket timeout of 10 minutes, and a connection timeout of 30 seconds. The values can be optionally configured respectively with `-DrestSocketTimeout=<...>` and `-DrestConnectionTimeout=<...>`, expressed in seconds.

##### REST Headers

<table bgcolor="#ffff00">
<tr>
<td>
    <b>NOTE</b> : Available from version 4.0
</td>
</tr>
</table>

You can add custom HTTP headers to your REST calls by setting a property named `restHeaders`. HTTP headers must be comma-separated and each non-empty name-value pair must be colon-separated.

For example:

    -DrestHeaders=log-user-id:102,log-request-context:061294ff-088,log-process-context:,log-expires:,log-tmp:

##### REST PNC-Specific properties 

<table bgcolor="#ffff00">
<tr>
<td>
    <b>NOTE</b> : Available from version 4.16
</td>
</tr>
</table>

As of new versions of DA (version TBD), it makes available 3 additional ways how you can influence returned results. By
default DA returns highest version with the highest version suffix. With `-DrestDependencyRanks`,
`-DrestDependencyAllowList`, `-DrestDependencyDenyList` you can alter this behaviour if you want to highlight some PNC
specific properties of the dependencies.

The general framework about how to express these PNC-Specific qualities is done through so called Qualifiers. Examples
of such Qualifiers are `PRODUCT`(PNC Product), `VERSION` (PNC Product Version), `MILESTONE` (PNC Product Milestone) or
`QUALITY` (PNC Artifact Quality) and more... These Qualifier have their respective values like `PRODUCT:RHSSO`,
`VERSION:RHSSO 7.2`, `MILESTONE:RHSSO 7.2.3.CR2`. If an PNC-made Artifact was built by Build which was a part of RHSSO
Product. A Qualifier `PRODUCT:RHSSO` would match that artifact or dependency in PME's context. 

With `restDependencyRanks` you can express a preference which artifacts you prefer more than others. It consists of 
separate ranks where each rank is delimited by `;` (can be modified by `-DrestDependencyRankDelimiter=','`). Simple
example of ranking could be`-DrestDependencyRanks="PRODUCT:RHSSO"`. What would happen is, that DA will look at all 
versions of a dependency and sort them by the Product and version suffix at the same time. 
With some example versions and `-DrestDependencyRanks="PRODUCT:RHSSO"` best version would be picked like this.
(highlighted version is picked for that dependency)

| Version       | Product | Quality  | SORTED -->><br/><<--PRE-SORT | Version           | Product   | Quality      |
|---------------|---------|----------|------------------------------|-------------------|-----------|--------------|
| release-00006 | EAP     |          |                              | **release-00005** | **RHSSO** | **RELEASED** |
| release-00002 |         |          |                              | release-00004     | RHSSO     |              |
| release-00003 | EAP     | RELEASED |                              | release-00006     | EAP       |              |
| release-00005 | RHSSO   | RELEASED |                              | release-00002     |           |              |
| release-00001 |         |          |                              | release-00003     | EAP       | RELEASED     |
| release-00004 | RHSSO   |          |                              | release-00001     |           |              |

Order of ranks matter, with higher rank comes higher priority. Additionally, ranks 'cooperate' with each other. Versions
with multiple ranks that match will be prioritized. 

For example `-DrestDependencyRanks="PRODUCT:EAP;QUALITY:RELEASED"` would result in this order.

| Version       | Product | Quality  | SORTED -->><br/><<--PRE-SORT | Version           | Product | Quality      |
|---------------|---------|----------|------------------------------|-------------------|---------|--------------|
| release-00006 | EAP     |          |                              | **release-00003** | **EAP** | **RELEASED** |
| release-00002 |         |          |                              | release-00006     | EAP     |              |
| release-00003 | EAP     | RELEASED |                              | release-00005     | RHSSO   | RELEASED     |
| release-00005 | RHSSO   | RELEASED |                              | release-00004     | RHSSO   |              |
| release-00001 |         |          |                              | release-00002     |         |              |
| release-00004 | RHSSO   |          |                              | release-00001     |         |              |


`restDependencyAllowList` and `restDependencyDenyList` are fairly straightforward. They are comma-seperated 
(non-configurable) list of Qualifiers that will, in case of allowList, be **exclusively** allowed or in case of
denyList, **not allowed at all**.

An example:

    -DrestDependencyAllowList="PRODUCT:EAP, PRODUCT:RHSSO"

    -DrestDependencyDenyList="QUALITY:BLACKLISTED"

Furthermore, you can change the scope of affected dependencies for specific groupIDs or groupID:artifactIDs. The
`-DrestDependencyRanks` is global, while `-DrestDependencyRanks.artifactID:groupID` is applied on a subset.
For example`-DrestDependencyRanks.org.slf4j='PRODUCT:EAP'` would apply only to artifacts with `org.slf4j` groupID. This 
is the case also for `-DrestDependencyAllowList.org.slf4j:slf4j-api` and `-DrestDependencyAllowList.org.slf4j:slf4j-api`. 

Full list of available Qualifiers and much more in-depth guide how ranking system works will be available in other
document (TBD).

### Direct/Transitive Dependencies

<table bgcolor="#ffff00">
<tr>
<td>
    <b>NOTE</b> : As of PME 2.13 (October 2017) the default for <i>overrideTransitive</i> has changed from true to false.
</td>
</tr>
</table>

The extension can inject all dependencies declared in the remote BOM. This will also override dependencies that are not directly specified in the project. If these transitive dependencies should not be overridden, the option `overrideTransitive` can be set to `false` to disable this feature.

    mvn install -DdependencyManagement=org.foo:my-dep-pom:1.0 -DoverrideTransitive=false

### Parent Version Override

PME will also change any parent reference it finds that matches an entry in the remote BOM.

For example:

```xml
<parent>
  <groupId>org.switchyard</groupId>
  <artifactId>switchyard-parent</artifactId>
  <version>2.0.0.Alpha1</version>
</parent>
```

will change to:

```xml
<parent>
  <groupId>org.switchyard</groupId>
  <artifactId>switchyard-parent</artifactId>
  <version>2.0.0.Alpha1-rebuild-1</version>
</parent>
```

### Exclusions and Overrides

In a multi-module build it is considered good practice to coordinate dependency version among the modules using dependency management.  In other words, if module A and B both use dependency X, both modules should use the same version of dependency X.  Therefore, the default behaviour of this extension is to use a single set of dependency versions applied to all modules.

It is possible to flexibly override or exclude a dependency globally or on a per-module basis. The property starts with `dependencyOverride.` and has the following format:

    mvn install -DdependencyOverride.[groupId]:[artifactId]@[moduleGroupId]:[moduleArtifactId]=[version] | ,+[group:artifact]...


<table bgcolor="#ffff00">
<tr>
<td>
    <b>NOTE</b> : Previously <i>dependencyExclusion</i> operated as an alias of <i>dependencyOverride</i> (and functioned _exactly the same_). As of PME 4.0 this has now been deprecated and must be explicitly enabled. If enabled and both are set then they will be merged and an error thrown if they clash.
</td>
</tr>
</table>

**Note:** Multiple overrides/exclusions may be added using multiple instances of `-DdependencyOverride...`.

#### Global Version Override

Sometimes it is more convenient to use the command line rather than a BOM. Therefore, extending the above it is possible to set the version of a dependency via:

    mvn install -DdependencyOverride.junit:junit@*=4.10-rebuild-10

This will, throughout the entire project (due to the wildcard), apply the explicit 4.10-rebuild-10 version to the junit:junit dependency.

**Note:** Explicit overrides like this will take precedence over strict alignment and the BOM.


#### Per-Module Version Override

However, there are certain cases where it is useful to use different versions of the same dependency in different modules.  For example, if the project includes integration code for multiple versions of a particular API. In that case it is possible to apply a version override to a specific module of a multi-module build. For example to apply an explicit dependency override only to module B of project foo.

    mvn install -DdependencyOverride.junit:junit@org.foo:moduleB=4.10

**Note:** Explicit overrides like this will take precedence over strict alignment and the BOM.


#### Per-Module Prevention of Override

It is also possible to **prevent overriding dependency versions** on a per-module basis:

    mvn install -DdependencyOverride.[groupId]:[artifactId]@[moduleGroupId]:[moduleArtifactId]=

For example:

    mvn install -DdependencyOverride.junit:junit@org.foo:moduleB=

#### Override Prevention with Wildcards

Likewise, you can prevent overriding a dependency version across the entire project using a wildcard:

    mvn install -DdependencyOverride.[groupId]:[artifactId]@*=

For example:

    mvn install -DdependencyOverride.junit:junit@*=

Or, you can prevent overriding a dependency version across the entire project where the groupId matches, using multiple wildcards:

    mvn install -DdependencyOverride.[groupId]:*@*=

For example:

    mvn install -DdependencyOverride.junit:*@*=

#### Per Module Override Prevention with Wildcards

Linking the two prior concepts it is also possible to prevent overriding using wildcards on a per-module basis e.g.,
    mvn install -DdependencyOverride.*:*@org.foo:moduleB=

This will prevent any alignment within the org.foo:moduleB.

    mvn install -DdependencyOverride.*:*@org.foo:*=

This will prevent any alignment within `org.foo` and all submodules within that.

#### Dependency Exclusion Addition

It is also possible to inject specific exclusions into a dependency. For instance

    mvn install -DdependencyOverride.junit:junit@*=4.10-rebuild-1,+slf4j:slf4j

will, as per above, apply the explicit 4.10-rebuild-10 version to the junit:junit dependency but also add an exclusion e.g.,
```xml
<dependency>
  <groupId>junit</groupId>
  <artifactId>junit</artifactId>
  <version>4.1</version>
  <exclusions>
    <exclusion>
      <artifactId>slf4j</artifactId>
      <groupId>slf4j</groupId>
    </exclusion>
  </exclusions>
</dependency>
```

Multiple exclusions to a dependency may be added using comma separators e.g.,
    mvn install -DdependencyOverride.junit:junit@*=+slf4j:slf4j,+commons-lang:commons-lang

#### Using a version from a Remote POM

While the `dependencyManagement` property does accept multiple remote POMs on its own, sometimes you might want a more limited effect from one of these extra remote POMs. For example, to align just one module of your project, or to align only certain artifacts to the versions defined in that POM.

1. Set a property with the prefix `dependencyManagement.`, suffixed by a unique ID string of your choice. The value of the property follows the same format as a standard `dependencyManagement` property, but only accepts one POM per ID string. You can define multiple POMs by repeating this `dependencyManagement.` property, but with a different ID suffix for each instance.
2. Reference this ID string as the version of your `dependencyOverride` properties.

For example:

    mvn install -DdependencyManagement=org.foo:my-dep-pom:1.0 -DdependencyManagement.xyzzy=org.foo:my-dep-pom:2.0 -DdependencyOverride.commons-lang:commons-lang:*@*=xyzzy

An error will be produced when the remote POM does not define a version for the group and artifact defined in the `dependencyOverride`.

These extra BOMs have no affect unless referenced by one or more `dependencyOverride`s.

### Strict Mode Version Alignment

<table bgcolor="#ffff00">
<tr>
<td>
    <b>NOTE</b> : As of PME 2.13 (October 2017) the default for <i>strictAlignment</i> has changed from false to true.
</td>
</tr>
<tr style="background-color:green" >
<td>
    It is <b>highly recommended</b> to keep this value set to true.
</td>
</tr>
</table>

When aligning dependency versions to some shared standard, it's possible to introduce incompatibilities that stem from changing the version. This may be due to unexpected API changes. While _in general_ it might be safe to revise a dependency's version from 1.5 to 1.5.1, it may not be safe to revise it to 2.0, or even 1.6.

By default, strict-mode version alignment checks are enabled. This is **highly recommended** to prevent API conflicts and when dependencies are aligned via a BOM or REST source (not via explicit overrides, as explained above).

Strict-mode alignment will detect cases where the adjusted version, once OSGi and suffix are handled, does not match with the old version, **not** do the alignment and report the mismatch as a warning in the build's console output. For example, if the incremental or manual suffix is configured to be `rebuild` then these are valid changes.

| Original Version | Potential New Versions          |
|------------------|---------------------------------|
| 2.6              | 2.6-rebuild-2 ; 2.6.0.rebuild-4 |
| 3                | 3.0.0.rebuild-1 ; 3-rebuild-1   |
| 3.1              | 3.1.0.rebuild-1 ; 3.1-rebuild-1 |

Note that it will not consider 3 -> 3.1 as a valid transition.

If required, strict-mode version alignment checks can be disabled using:

    mvn install -DstrictAlignment=false

If the build should fail when strict-mode checks are violated set `strictViolationFails` property to true (default: false):

    mvn install -DstrictAlignment=true -DstrictViolationFails=true

This will cause the build to fail with a ManipulationException, and prevent the extension from rewriting any POM files.

**Note:** dependency exclusions they will not work if the dependency uses a version property that has been changed by another dependency modification. Explicit version override will overwrite the property value though.

#### Strict Alignment and Suffixes

<table bgcolor="#ffff00">
<tr>
<td>
    <b>NOTE</b> : As of PME 2.13 (October 2017) the default for <i>strictAlignmentIgnoreSuffix</i> has changed from false to true.
</td>
</tr>
</table>

The property `strictAlignmentIgnoreSuffix` will mean that the comparison will ignore the suffix depicted by `version.incremental.suffix` or `version.suffix` during version comparisons. It will also only allow alignment to a higher incrementing suffix e.g.,
    3.1.0.Final-rebuild-1 --> 3.1.0.Final-rebuild-3

#### Strict Property Validation

The property `strictPropertyValidation` is similar to the property clash replacement below. However, while the below case detects two dependencies/plugins that update the property to different values, this validation ensures that *every* dependency (or plugin) that uses a matching property attempted to update it. This validates that the source of the information (e.g., BOM or REST) for the following example passed in both `org.foo:bar1` and `org.foo:bar2`. If `bar2` has not been passed in then the validation fails and, depending upon the configuration, either an Exception can be thrown (if set to `true`) or it will attempt to revert the changes (if set to `revert`). The default is `false` (i.e., off). Enabling this can avoid a potential later failure in a build where the bar2 alignment doesn't exist but has been implicitly updated through the `bar1` alignment. Note that this does **not** validate for `dependencyManagement` blocks but only for dependencies.

    propertyX = 1.0

    org.foo:bar1:$propertyX
    org.foo:bar2:$propertyX

### Property Clash Replacement

When replacing a dependency PME will trace back and update the property value. However, there are scenarios where the dependency versions returned from either the BOM or the REST source can be different even though they refer to the same property e.g.,
    propertyX = 1.0

    org.foo:bar1:$propertyX
    org.foo:bar2:$propertyX

And the REST source returns 1.0.rebuild-2 for bar1 and rebuild-4 for bar2. In this case PME will detect the clash and throw an error. It is possible to configure PME so that it will not update the property and continue by setting `propertyClashFails` to false (default: true).

### Dependency Relocations

In order to handle the situation where one GAV is changed to another (e.g., from community to product) the relocation manipulator can be used. An optional version component may be added; the version will override any prior version used in the dependency. The artifact override is optional. The relocated GAV is subject to alignment ; if the developer wishes to force a particular version (i.e., one with an existing suffix) they may use `dependencyOverride`.

<table bgcolor="#ffff00">
<tr>
<td>
    <b>NOTE</b> : As of PME 4.1 dependency relocations <b>are</b> subject to alignment. The optional version is passed to DA for dependency alignment.
</td>
</tr>
</table>

    -DdependencyRelocations.oldGroupId:[oldArtifact]@newGroupId:[newArtifactId]=[version],...

**Note:** Multiple relocations may be added using multiple instances of `-DdependencyRelocation...`.

#### Dependency Exclusion Addition

Similar to with `dependencyOverrides` it is also possible to inject specific exclusions into a dependency. For instance

    mvn install -DdependencyRelocations.junit:junit@junit:newjunit=4.10-rebuild-1,+slf4j:slf4j

will, as per above, relocate the junit artifactId and apply the explicit 4.10-rebuild-10 version to the junit:junit dependency but also add an exclusion e.g.,
```xml
<dependency>
  <groupId>junit</groupId>
  <artifactId>newjunit</artifactId>
  <version>4.1</version>
  <exclusions>
    <exclusion>
      <artifactId>slf4j</artifactId>
      <groupId>slf4j</groupId>
    </exclusion>
  </exclusions>
</dependency>
```

Multiple exclusions to a dependency may be added using comma separators e.g.,
mvn install -DdependencyRelocations.junit:junit@junit:newjunit=+slf4j:slf4j,+commons-lang:commons-lang

<table bgcolor="#ffff00">
<tr>
<td>
    <b>NOTE</b> : Available from PME 4.19.
</td>
</tr>
</table>

### Dependency Removal

If the property `-DdependencyRemoval=group:artifact,....` is set, PME will remove the specified dependency from the
POM files. The argument should be a comma separated list of group:artifact. The list also supports a remote HTTP
file containing a comma or newline separated list of GAs.

### Dependency Injection

If the property `-DdependencyInjection=group:artifact:version,....` is set, PME will inject the
specified dependencies into the top level (inheritance root) POM file's `<dependencyManagement>` section.
The argument should be a comma separated list of dependencies using one of the following specifications.
For all of the supported specification formats, `groupId`, `artifactId`, and `version` are required.
All other fields are optional and will be treated as `null` when absent or blank.

- `groupId:artifactId:version`
- `groupId:artifactId:type:version` <span style="background-color:#ffff00">Available from PME 4.22</span>
- `groupId:artifactId:type:classifier:version` <span style="background-color:#ffff00">Available from PME 4.22</span>
- `groupId:artifactId:type:classifier:version:scope` <span style="background-color:#ffff00">Available from PME 4.22</span>

The list also supports a remote HTTP file containing a comma or newline separated list of strings having the same format.

**Note:** The injected dependencies <b>are</b> subject to alignment.

#### Maven Dependency Plugin
<table bgcolor="#ffff00">
<tr>
<td>
    <b>NOTE</b> : Available from PME 4.13.
</td>
</tr>
</table>
If the property `dependencyInjectionAnalyzeIgnoreUnused` is set to true (default: false) and if the maven-dependency-plugin
with the analyze goal exists in the root pom, then for every injected dependency, it will add each to the
`ignoreUnusedDeclaredDependencies` configuration.

### BOM Generation

If the property `-DbomBuilder=true` is set, then the PME BOM Builder will be activated. This will deploy a new POM to the repository under the GAV `<project-top-level-groupId>.<project-top-level-artifactId>:pme-bom:<version>` e.g., `org.projectOne.artifactOne:pme-bom:1.0.0`. It will contain a list of all adjusted modules and is suitable for use as a remote BOM. As it has a predictable name it may be used in a build of a subsequent project to align to this one e.g.,
    PWD=<project two> ; mvn -DdependencyManagement=org.projectOne.artifactOne:pme-bom:1.0.0 -Dversion.suffix=rebuild-1 clean install

**Note:** The new BOM will be installed and deployed via a custom plugin that is added to the top level project. The plugin is configured to only run on the top level POM and will not fail if the BOM POM does not exist.

### Scope Exclusion

If the property `-DexcludedScopes=<scope>,....` is set, PME will **ignore** any dependency with the specified scope. This means that, if for example the property was set to `test` then alignment (**and** any other relevant manipulation) would not happen for test scoped dependencies. Note that valid scopes are those specified in [Maven Scopes](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Scope) and if an invalid scope is passed in an exception will be thrown.

### REST and Suffix Alignment

<table bgcolor="#ffff00">
<tr>
<td>
    <b>NOTE</b> : Available from version 3.8
</td>
</tr>
</table>

If the property `-DrestSuffixAlign` is set to false (default: true) then PME will **not** attempt to align any dependency that has an existing suffix. This _only_ applies during REST alignment. The use-case for this is where some dependencies have already been manually aligned to specific values and the user does not wish them to be changed.
