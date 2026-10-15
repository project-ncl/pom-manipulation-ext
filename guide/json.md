---
title: "Json Manipulation"
---

# Json Manipulation

## Overview

PME offers the ability to modify one or many json files in the repository prior to running the build.

## Configuration

The manipulator is controlled by the `jsonUpdate` property. The format is

    -DjsonUpdate=<file>:<json-xpath-expression>:[<replacement-value>] [,....]

Multiple comma separated values may be supplied. If the replacement-value is **not** specified the operation becomes a _delete_ rather than an _update_.

The format for the _xpath-style_ expression is specified in the JSON-Path project [here](https://github.com/jayway/JsonPath).

**Note**: Any ',' or ':' in the path expression or replacement value should be escaped with '\\'.

As an example:

    -DjsonUpdate='manager/ui/war/npm-shrinkwrap.json:$..resolved:,distro/data/src/main/resources/data/basic-settings.json:$..version:1.3.1.rebuild-1'

This means:

1. For the file _manager/ui/war/npm-shrinkwrap.json_
  * Use the path expression _$..resolved_ to deep scan for resolved.
  * As there is no replacement, these are then deleted.

2. For the file _distro/data/src/main/resources/data/basic-settings.json_
  * Use the path expression _$..version_ to deep scan for version.
  * Replace the value with 1.3.1.rebuild-1
