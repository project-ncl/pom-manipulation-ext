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
package org.jboss.pnc.mavenmanipulator.core.state;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.commonjava.atlas.maven.ident.ref.ProjectRef;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.jboss.pnc.mavenmanipulator.annotation.ConfigValue;
import org.jboss.pnc.mavenmanipulator.core.impl.ProjectVersioningManipulator;

import lombok.Getter;

/**
 * Captures configuration and changes relating to the projects' versions. Used by {@link ProjectVersioningManipulator}.
 *
 * @author jdcasey
 */
@Getter
public class VersioningState
        implements State {
    @ConfigValue(docIndex = "project-version-manip.html#manual-version-suffix")
    public static final String VERSION_SUFFIX_SYSPROP = "versionSuffix";

    @ConfigValue(docIndex = "project-version-manip.html#automatic-version-increment")
    public static final String INCREMENT_SERIAL_SUFFIX_SYSPROP = "versionIncrementalSuffix";

    @ConfigValue(docIndex = "project-version-manip.html#version-increment-padding")
    public static final String INCREMENT_SERIAL_SUFFIX_PADDING_SYSPROP = "versionIncrementalSuffixPadding";

    @ConfigValue(docIndex = "project-version-manip.html#snapshot-detection")
    public static final String VERSION_SUFFIX_SNAPSHOT_SYSPROP = "versionSuffixSnapshot";

    @ConfigValue(docIndex = "project-version-manip.html#osgi-compliance")
    public static final String VERSION_OSGI_SYSPROP = "versionOsgi";

    @ConfigValue(docIndex = "project-version-manip.html#version-override")
    public static final String VERSION_OVERRIDE_SYSPROP = "versionOverride";

    @ConfigValue(docIndex = "project-version-manip.html#alternate-suffix-handling")
    public static final String VERSION_SUFFIX_ALT = "versionSuffixAlternatives";

    @ConfigValue(docIndex = "project-version-manip.html#version-modification")
    public static final String VERSION_MODIFICATION = "versionModification";

    /**
     * @return the version suffix to be appended to the project version.
     */
    private String suffix;

    /**
     * @return the incremental suffix that will be appended to the project version.
     */
    private String incrementalSerialSuffix;

    /**
     * @return true if we should preserve the snapshot
     */
    private boolean preserveSnapshot;

    /**
     * @return true if we should make the versions OSGi compliant
     */
    private boolean osgi;

    /**
     * Forcibly override the version to a new one.
     *
     * @return the new version
     */
    private String override;

    /**
     * @return the incremental suffix padding that will be appended to the project version i.e. whether to append 001 or
     *         1.
     */
    private int incrementalSerialSuffixPadding;

    private List<String> suffixAlternatives;

    private List<String> allSuffixes;

    /**
     * Record the versions to change. Essentially this contains a mapping of original
     * project GAV to new version to change.
     *
     * @return the mapping of original project GAV to new version to change
     */
    private final Map<ProjectVersionRef, String> versionsByGAV = new HashMap<>();

    /**
     * Store preprocessed metadata from the REST call in order to use for incremental lookup.
     */
    private Map<ProjectRef, Set<String>> restMetaData;

    /**
     * @return true if version modification is enabled
     */
    private boolean versionModification;

    public VersioningState(final Properties userProps) {
        initialise(userProps);
    }

    public void initialise(Properties userProps) {
        suffix = userProps.getProperty(VERSION_SUFFIX_SYSPROP);
        incrementalSerialSuffix = userProps.getProperty(INCREMENT_SERIAL_SUFFIX_SYSPROP);
        incrementalSerialSuffixPadding = Integer
                .parseInt(userProps.getProperty(INCREMENT_SERIAL_SUFFIX_PADDING_SYSPROP, "5"));
        preserveSnapshot = Boolean.parseBoolean(userProps.getProperty(VERSION_SUFFIX_SNAPSHOT_SYSPROP));
        osgi = Boolean.parseBoolean(userProps.getProperty(VERSION_OSGI_SYSPROP, "true"));
        override = userProps.getProperty(VERSION_OVERRIDE_SYSPROP);
        versionModification = Boolean.parseBoolean(userProps.getProperty(VERSION_MODIFICATION, "true"));

        // Provide an alternative list of versionSuffixes split via a comma separator. Defaults to 'redhat' IF the current rebuild suffix is not that.
        suffixAlternatives = Arrays.asList(
                StringUtils.split(
                        userProps.getProperty(
                                VERSION_SUFFIX_ALT,
                                "redhat".equals(getRebuildSuffix()) ? "" : "redhat"),
                        ","));

        allSuffixes = new ArrayList<>();

        // If no suffix is configured then don't fill in the all suffixes array.
        if (isNotEmpty(getRebuildSuffix())) {
            allSuffixes.add(getRebuildSuffix());
            allSuffixes.addAll(getSuffixAlternatives());
        }
    }

    /**
     * Enabled ONLY if either versionIncrementalSuffix or versionSuffix is provided in the user properties / CLI -D
     * options and version modification has not been explicitly disabled.
     *
     * @see #VERSION_SUFFIX_SYSPROP
     * @see #INCREMENT_SERIAL_SUFFIX_SYSPROP
     * @see State#isEnabled()
     */
    @Override
    public boolean isEnabled() {
        return versionModification && (incrementalSerialSuffix != null || suffix != null || override != null);
    }

    public void setRESTMetadata(Map<ProjectRef, Set<String>> versionStates) {
        restMetaData = versionStates;
    }

    public Map<ProjectRef, Set<String>> getRESTMetadata() {
        return restMetaData;
    }

    public void setVersionsByGAVMap(Map<ProjectVersionRef, String> versionsByGAV) {
        this.versionsByGAV.putAll(versionsByGAV);
    }

    public boolean hasVersionsByGAV() {
        return !versionsByGAV.isEmpty();
    }

    public String getRebuildSuffix() {
        String suffix = "";

        // Same precedence as VersionCalculator::calculate
        if (!isEmpty(getSuffix())) {
            int dashIndex = getSuffix().lastIndexOf('-');
            suffix = getSuffix().substring(0, dashIndex > 0 ? dashIndex : getSuffix().length());
        } else if (!isEmpty(getIncrementalSerialSuffix())) {
            suffix = getIncrementalSerialSuffix();
        }
        return suffix;
    }
}
