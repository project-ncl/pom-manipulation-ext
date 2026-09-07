---
title: "Project Version Manipulation"
---

* Contents
{:toc}

### Overview

When rebuilding a Maven project's sources from a release tag (or really for any version that has already been released), it's important **NOT** to republish the original GAV (groupId, artifactId, version) coordinate. If you change anything during your rebuild (even a plugin version), you could produce a result that is not a binary equivalent of the original release. To help avoid this, PME supports automatically updating the project version to append a serial rebuild suffix. PME's re-versioning feature may also make other changes to the project version, in order to make the resulting version OSGi-compliant where possible.

### Disabling Version Manipulation

<table bgcolor="#ffff00">
<tr>
<td>
    <b>NOTE</b> : Available from version 4.15
</td>
</tr>
</table>

If `versionModification` is set to false (default: true) then no version change will happen.

### Automatic version increment

The extension can be used to append a version suffix/qualifier to the current project, and then apply an incremented index to the version to provide a unique release version.  For example, if the current project version is 1.0.0.GA, the extension can automatically set the version to 1.0.0.GA-rebuild-1, 1.0.0.GA-rebuild-2, etc.

The extension is configured using the property `versionIncrementalSuffix` (*Deprecated property `version.incremental.suffix` for versions **3.8.1 and prior***).

    mvn install -DversionIncrementalSuffix=rebuild

#### Version increment padding

<table bgcolor="#ffff00">
<tr>
<td>
    <b>NOTE</b> : For PME versions <b>after</b> 3.8, the default for <i>versionIncrementalSuffixPadding</i> has changed from 0 to 5.
</td>
</tr>
</table>

When using the automatic increment it is also possible to configure padding for the increment. For instance, by setting `versionIncrementalSuffixPadding` (*Deprecated property `version.incremental.suffix.padding` for versions **3.8.1 and prior***)to `3` the version will be `rebuild-003`. Default for PME &le; 3.8 is 0 and for PME after 3.8 is 5.

#### Version Increment Metadata

The metadata to work out what the correct version of the increment should be can be sourced the one of two different locations.

1. By default the Maven repository metadata will be checked to locate the latest released version of the project artifacts, and the next version is selected by the extension.

2. Alternatively if the property `-DrestURL` has been configured the the REST client service will be used as a source. For more details on this see Dependency Manipulation. See [here](./dep-manip.html) for more details on the REST endpoint.

### Manual version suffix

The version suffix to be appended to the current project can be manually selected using the property `versionSuffix` (*Deprecated property `version.suffix` for versions **3.8.1 and prior***)

    mvn install -DversionSuffix=release-1

If the current version of the project is "1.2.0.GA", the new version set during the build will be "1.2.0.GA-release-1".

**Note** `versionSuffix` takes precedence over `versionIncrementalSuffix`.

### Version override

The version can be forcibly overridden by using the property `versionOverride` (*Deprecated property `version.override`
for versions **3.8.1 and prior***)

    mvn install -DversionOverride=6.1.0.Final

If the current version of the project is "6.2.0", the new version set during the build will be "6.1.0.Final".

Note that the `versionOverride` property is meant to be used to override the version only, not the suffix. If the
version already contains a suffix, the result may not be as expected due to automatic version increment. For example,
suppose you wish to force the version to "6.1.0.Final-rebuild-1". Running

    mvn install -DversionOverride=6.1.0.Final-rebuild-1

would result in the final version being "6.1.0.Final-rebuild-2", not "6.1.0.Final-rebuild-1" as intended.

If you wish to override both the version and the suffix, you need to combine the `versionOverride` and `versionSuffix`
properties. For example, running

    mvn install -DversionOverride=6.1.0.Final -DversionSuffix=rebuild-1

would result in the final version being "6.1.0.Final-rebuild-1" as intended.

### Snapshot Detection

The extension can detect snapshot versions and either preserve the snapshot or replace it with a real version. This is controlled by the property `versionSuffixSnapshot` (*Deprecated property `version.suffix.snapshot` for versions **3.8.1 and prior***). The default is false (i.e. remove SNAPSHOT and replace by the suffix).

    mvn install -DversionSuffixSnapshot=true

This means that the SNAPSHOT suffix will be kept.

### Suffix Stripping

Normally the tool will manipulate the version as given within the POM. However in certain scenarios it is desired that a known suffix is stripped from the version _before_ any further manipulators (e.g. REST, Version etc) are run. To activate this pass:

    -DversionSuffixStrip=

This will utilise the default suffix strip configuration (in regular expression form) of `(.*)(.jbossorg-\d+)$`. To configure this to be something different simply pass e.g.:

    -DversionSuffixStrip='(.*)(.MYSUFFIX)$'

<table bgcolor="#ffff00">
<tr>
<td>
    <b>NOTE</b> : From version 4.0 disabling is possible as follows:
</td>
</tr>
</table>

If the special keyword of `NONE` is used this will also disable the suffix (after it has been enabled) i.e.

    -DversionSuffixStrip= -DversionSuffixStrip=NONE


### OSGi Compliance

If version manipulation is enabled the extension will also attempt to format the version to be OSGi compliant. For example if the versions are:

    1
    1.3
    1.3-GA
    1.3.0-GA

it will change to

    1.0.0
    1.3.0
    1.3.0.GA
    1.3.0.GA

This is controlled by the property `versionOsgi` (*Deprecated property `version.osgi` for versions **3.8.1 and prior***). The default is true (i.e. make the versions OSGi compliant).

### Enforce Version Prefix

<table bgcolor="#ffff00">
<tr>
<td>
    <b>NOTE</b> : Available from version 5.8
</td>
</tr>
</table>

When rebuilding projects with a vendor-specific suffix scheme it is sometimes necessary to guarantee that every project version carries a known reference string (e.g. `acme`) before the incremental suffix is appended. The property `enforceVersionPrefix` addresses this: when set, any version that does **not** already contain the configured reference (preceded by `.` or `-` and followed by `-` and one or more digits) has the reference appended before the REST look-up and version calculation take place.

    mvn install -DenforceVersionPrefix=acme -DversionIncrementalSuffix=rebuild -DversionIncrementalSuffixPadding=5

For example, if the current project version is `1.0.0.Final` and the REST service has never seen it before, the result will be:

    1.0.0.Final-acme-00000-rebuild-00001

If the version already contains the reference — for instance `1.0.0.Final-acme-00001` — it is left unchanged and normal incremental-suffix processing continues.

**Note** - <code>versionOverride</code> takes precedence; when it is set, <code>enforceVersionPrefix</code> is skipped.

### Alternate Suffix Handling
It is possible to pass in a comma separated list of alternate suffixes via the property `versionSuffixAlternatives`. The default value is `redhat` which will be applied _if_ the current suffix does not match that. This is used during dependency alignment to validate strict alignment between differing suffix types (from the input REST or BOM data).
