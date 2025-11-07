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
package org.jboss.pnc.maven_manipulator.core.util;

import static org.apache.commons.lang3.StringUtils.contains;
import static org.apache.commons.lang3.StringUtils.countMatches;
import static org.apache.commons.lang3.StringUtils.endsWith;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.startsWith;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.model.Plugin;
import org.apache.maven.model.Profile;
import org.commonjava.atlas.maven.ident.ref.ProjectRef;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectRef;
import org.jboss.pnc.maven_manipulator.common.ManipulationException;
import org.jboss.pnc.maven_manipulator.common.model.Project;
import org.jboss.pnc.maven_manipulator.common.util.ProfileUtils;
import org.jboss.pnc.maven_manipulator.common.util.PropertyResolver;
import org.jboss.pnc.maven_manipulator.core.ManipulationSession;
import org.jboss.pnc.maven_manipulator.core.impl.Version;
import org.jboss.pnc.maven_manipulator.core.state.CommonState;
import org.jboss.pnc.maven_manipulator.core.state.VersioningState;
import org.jboss.pnc.maven_manipulator.io.ConfigIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Commonly used manipulations / extractions from project / user (CLI) properties.
 */
public final class PropertiesUtils {
    private final static Logger logger = LoggerFactory.getLogger(PropertiesUtils.class);

    private PropertiesUtils() {
    }

    /**
     * Filter Properties by accepting only properties with names that start with prefix. Trims the prefix
     * from the property names when inserting them into the returned Map.
     *
     * @param properties the properties to filter.
     * @param prefix The String that must be at the start of the property names
     * @return map of properties with matching prepend and their values
     */
    public static Map<String, String> getPropertiesByPrefix(final Properties properties, final String prefix) {
        final Map<String, String> matchedProperties = new HashMap<>();
        final int prefixLength = prefix.length();

        for (final String propertyName : properties.stringPropertyNames()) {
            if (propertyName.startsWith(prefix)) {
                final String trimmedPropertyName = propertyName.substring(prefixLength);
                String value = properties.getProperty(propertyName);
                if ("true".equals(value)) {
                    logger.warn(
                            "Work around Brew/Maven bug - removing erroneous 'true' value for {}.",
                            trimmedPropertyName);
                    value = "";
                }
                matchedProperties.put(trimmedPropertyName, value);
            }
        }

        return matchedProperties;
    }

    /**
     * Recursively update properties.
     *
     * @param session the DependencyState
     * @param project the current set of projects we are scanning.
     * @param ignoreStrict whether to ignore strict alignment.
     * @param key a key to look for.
     * @param newValue a value to look for.
     * @return {@code PropertyUpdate} enumeration showing status of any changes.
     * @throws ManipulationException if an error occurs
     */
    public static PropertyUpdate updateProperties(
            ManipulationSession session,
            Project project,
            boolean ignoreStrict,
            String key,
            String newValue) throws ManipulationException {
        final String resolvedValue = PropertyResolver
                .resolveProperties(session, project.getInheritedList(), "${" + key + '}');

        logger.debug("Fully resolvedValue is {} for {}", resolvedValue, key);

        if ("project.version".equals(key)) {
            logger.debug("Not updating key {} with {}", key, newValue);
            return PropertyUpdate.IGNORE;
        }

        for (final Project p : project.getReverseInheritedList()) {
            if (p.getModel().getProperties().containsKey(key)) {
                logger.trace("Searching properties of {}", p);
                return internalUpdateProperty(
                        session,
                        p,
                        ignoreStrict,
                        key,
                        newValue,
                        resolvedValue,
                        p.getModel().getProperties());
            } else {
                for (Profile pr : ProfileUtils.getProfiles(session, p.getModel())) {
                    logger.trace("Searching properties of profile {} within project {}", pr.getId(), p);
                    // Lets check the profiles for property updates...
                    if (pr.getProperties().containsKey(key)) {
                        return internalUpdateProperty(
                                session,
                                p,
                                ignoreStrict,
                                key,
                                newValue,
                                resolvedValue,
                                pr.getProperties());
                    }
                }
            }
        }

        return PropertyUpdate.NOTFOUND;
    }

    private static PropertyUpdate internalUpdateProperty(
            ManipulationSession session,
            Project p,
            boolean ignoreStrict,
            String key,
            String newValue,
            String resolvedValue,
            Properties props) throws ManipulationException {
        final CommonState state = session.getState(CommonState.class);
        final String oldValue = props.getProperty(key);

        logger.debug("Examining property {} / {} (resolved {}) with {}", key, oldValue, resolvedValue, newValue);

        PropertyUpdate found = PropertyUpdate.FOUND;

        // We'll only recursively resolve the property if its a single >${foo}<. If its one of
        // >${foo}value${foo}<
        // >${foo}${foo}<
        // >value${foo}<
        // >${foo}value<
        // it becomes hairy to verify strict compliance and to correctly split the old value and
        // update it with a portion of the new value.
        if (startsWith(oldValue, "${") && endsWith(oldValue, "}") && !(countMatches(oldValue, "${") > 1)) {
            final String newKey = oldValue.substring(2, oldValue.length() - 1);

            logger.debug("Recursively resolving {}", newKey);

            if (updateProperties(session, p, ignoreStrict, newKey, newValue) == PropertyUpdate.NOTFOUND) {
                logger.error("Recursive property not found for {} with {}", oldValue, newValue);
                return PropertyUpdate.NOTFOUND;
            }
        } else {
            if (state.isStrict() && !ignoreStrict) {
                if (!checkStrictValue(session, resolvedValue, newValue)) {
                    if (state.isFailOnStrictViolation()) {
                        throw new ManipulationException(
                                "Replacing original property version {} (fully resolved: {} ) with new version {} for {} violates the strict version-alignment rule!",
                                oldValue,
                                resolvedValue,
                                newValue,
                                key);
                    } else {
                        logger.warn(
                                "Replacing original property version {} with new version {} for {} violates the strict version-alignment rule!",
                                oldValue,
                                newValue,
                                key);
                        // Ignore the dependency override. As found has been set to true it won't inject
                        // a new property either.
                        return found;
                    }
                }
            }

            // TODO: Does not handle explicit overrides.
            if (contains(oldValue, "${") && !(startsWith(oldValue, "${") && endsWith(
                    oldValue,
                    "}")) || (countMatches(oldValue, "${") > 1)) {
                // This block handles
                // >${foo}value${foo}<
                // >${foo}${foo}<
                // >value${foo}<
                // >${foo}value<
                // We don't attempt to recursively resolve those as tracking the split of the variables, combined
                // with the update and strict version checking becomes overly fragile.
                if (resolvedValue.equals(newValue)) {
                    logger.warn("Nothing to update as original key {} value matches new value {}", key, newValue);
                    found = PropertyUpdate.IGNORE;
                }
                // The following handles scenarios where there is embedded properties. If it
                // ends with a property then the entire value is updated as it can be error prone to determine
                // where to update. In other scenarios the trailing suffix is determined and updated with
                // the new resolved information.
                if (endsWith(oldValue, "}")) {
                    logger.debug("Original value ({}) ends with variable so inlining update", oldValue);
                } else {
                    final Pattern variablePattern = Pattern.compile("(.*\\$[{].*[}])(.*)");
                    final Matcher variableMatcher = variablePattern.matcher(oldValue);
                    if (!variableMatcher.matches()) {
                        throw new ManipulationException(
                                "Property update error - unable to match against {} to update " + "for {}",
                                oldValue,
                                newValue);
                    }
                    String prefix = variableMatcher.group(1);
                    String end = variableMatcher.group(2);
                    String oldSuffix = oldValue.replace(prefix, "");
                    String common = resolvedValue.replace(end, "");
                    String newSuffix = newValue.replace(common, "");
                    newValue = oldValue.replace(oldSuffix, newSuffix);
                    logger.debug(
                            "Replacing old value {} containing suffix {} with new suffix {} giving {}",
                            oldValue,
                            oldSuffix,
                            newSuffix,
                            newValue);
                }
            }
            props.setProperty(key, newValue);
        }
        return found;
    }

    /**
     * Retrieve any configured rebuild suffix.
     *
     * @param session Current ManipulationSession
     * @return non-null string suffix.
     */
    public static String getSuffix(ManipulationSession session) {
        return session.getState(VersioningState.class).getRebuildSuffix();
    }

    /**
     * Check the version change is valid in strict mode.
     *
     * @param session the manipulation session
     * @param oldValue the original version
     * @param newValue the new version
     * @return true if the version can be changed to the new version
     */
    public static boolean checkStrictValue(ManipulationSession session, String oldValue, String newValue) {
        if (oldValue == null || newValue == null) {
            return false;
        } else if (oldValue.equals(newValue)) {
            // The old version and new version matches. So technically it can be changed (even if its a bit pointless).
            return true;
        }

        final CommonState cState = session.getState(CommonState.class);
        final VersioningState vState = session.getState(VersioningState.class);
        final boolean ignoreSuffix = cState.isStrictIgnoreSuffix();

        /*
         *
         * This needs to be able to handle a number of different format conversions e.g.
         * 1.0.0 -> 1.0.0.temporary-redhat-n
         * 1.0.0 -> 1.0.0.redhat-n
         * 1.0.0.redhat-n -> 1.0.0.redhat-nn
         * 1.0.0.temporary-redhat-n -> 1.0.0.temporary-redhat-nn
         *
         * If the original value is different to requested alignment then it also needs to
         * support:
         * 1.0.0.redhat-n -> 1.0.0.temporary-redhat-n
         *
         * However currently it will NOT support this conversion.
         * 1.0.0.temporary-redhat-n -> 1.0.0.redhat-n
         *
         * Example:
         *
         * suffix orig new
         * tmp-rh-1 1.1.1.Final-redhat-2 --> 1.1.1.Final-temporary-redhat-1 YES
         * tmp-rh-1 1.1.1.Final-redhat-2 --> 1.1.2.Final-temporary-redhat-1 NO
         *
         * tmp-rh-1 1.1.1.Final-temporary-redhat-2 --> 1.1.1.Final-temporary-redhat-3 YES
         * tmp-rh-1 1.1.redhat-2 --> 1.1.0.temporary-redhat-3 YES
         * tmp-rh-1 1.1 --> 1.1.0.redhat-3 YES
         * tmp-rh-1 1.1 --> 1.1.0.temporary-redhat-3 YES
         *
         * rh-1 1.1.1.Final-temporary-redhat-1 --> 1.1.1.Final-redhat-2 NO
         * rh-1 1.1.1.Final-temporary-redhat-2 --> 1.1.1.Final-redhat-1 NO
         *
         * rh-1 1.1.1.Final-redhat-1 --> 1.1.1.Final-redhat-2 YES
         * rh-1 1.1.1.Final --> 1.1.1.Final-redhat-2 YES
         * rh-1 1.1 --> 1.1.0-redhat-2 YES
         *
         */

        final Set<String> oldValueOptions = buildOldValueSet(vState, oldValue);

        boolean result = false;

        if (vState.getAllSuffixes().isEmpty()) {
            logger.warn("No version suffixes found ; unable to determine strict mapping");
        }

        loop: for (String origValue : oldValueOptions) {
            for (String suffix : vState.getAllSuffixes()) {
                String v = origValue;
                if (!vState.isPreserveSnapshot()) {
                    v = Version.removeSnapshot(v);
                }

                String osgiVersion = Version.getOsgiVersion(v);

                // New value might be e.g. 3.1-rebuild-1 or 3.1.0.rebuild-1 (i.e. it *might* be OSGi compliant).
                String newVersion = newValue;

                if (isNotEmpty(suffix)) {
                    // If we have been configured to ignore the suffix (e.g. rebuild-n) then, assuming that
                    // the oldValue actually contains the suffix process it.
                    if (ignoreSuffix && origValue.contains(suffix)) {
                        HashSet<String> s = new HashSet<>();
                        s.add(origValue);
                        s.add(newValue);

                        String x = String.valueOf(Version.findHighestMatchingBuildNumber(v, s));

                        // If the new value has the higher matching build number strip the old suffix to allow for strict
                        // matching.
                        if (newValue.endsWith(x)) {
                            String oldValueCache = origValue;
                            origValue = origValue.substring(0, origValue.indexOf(suffix) - 1);
                            v = origValue;
                            osgiVersion = Version.getOsgiVersion(v);
                            logger.debug(
                                    "Updating version to {} and for oldValue {} with newValue {}",
                                    v,
                                    oldValueCache,
                                    newValue);

                        } else if (origValue.endsWith(x)) {
                            // Might happen if the value was a resolved property to the main project version that has already been updated. The
                            // new 'override' might have come from e.g. a BOM or from DA which would make it 'less' due to the version increment.
                            logger.warn(
                                    "strictValueChecking with strictIgnoreSuffix found older value ({}) was newer ({}) ",
                                    origValue,
                                    newValue);
                        } else {
                            logger.warn(
                                    "strictIgnoreSuffix set but unable to align from {} to {}",
                                    origValue,
                                    newValue);
                        }
                    }

                    // We only need to dummy up and add a suffix if there is no qualifier. This allows us
                    // to work out the OSGi version.
                    if (!Version.hasQualifier(v)) {
                        v = Version.appendQualifierSuffix(v, suffix);
                        osgiVersion = Version.getOsgiVersion(v);
                        osgiVersion = osgiVersion.substring(0, osgiVersion.indexOf(suffix) - 1);
                    }
                    // Don't attempt to match if we have suffix that is a substring of another suffix or we're matching
                    // against such a substring
                    boolean suffixSubstring = vState.getAllSuffixes()
                            .stream()
                            .filter(as -> !as.equals(suffix))
                            .noneMatch(s -> newValue.contains(s) && !suffix.contains(s));
                    if (suffixSubstring && newValue.matches("(.*)([.|-])" + suffix + ".*")) {
                        newVersion = newValue.substring(0, newValue.indexOf(suffix) - 1);
                    }
                }

                // We compare both an OSGi'ied oldVersion and the non-OSGi version against the possible new version (which has
                // had its suffix stripped) in order to check whether its a valid change.
                boolean success = (origValue.equals(newVersion) || osgiVersion.equals(newVersion));

                logger.debug(
                        "When validating original {} / new value {} comparing {} to {} and OSGi variant {} to {} (utilising suffix {}) is {}",
                        origValue,
                        newValue,
                        origValue,
                        newVersion,
                        osgiVersion,
                        newVersion,
                        suffix,
                        success ? "allowed" : "not allowed");

                if (success) {
                    result = true;
                    break loop;
                }
            }
        }
        return result;
    }

    /**
     * This will check if the old version (e.g. in a plugin or dependency) is a property and if so
     * store the mapping in a map.
     *
     * @param session the manipulation session
     * @param project the current project the needs to cache the value.
     * @param versionPropertyUpdateMap the map to store any updates in
     * @param oldVersion original property name
     * @param newVersion new property value
     * @param originalType that this property is used in (i.e. a plugin or a dependency)
     * @param force Whether to check for an existing property or force the insertion
     * @return true if a property was found and cached.
     * @throws ManipulationException if an error occurs.
     */
    public static boolean cacheProperty(
            ManipulationSession session,
            Project project,
            Map<Project, Map<String, PropertyMapper>> versionPropertyUpdateMap,
            String oldVersion,
            String newVersion,
            Object originalType,
            boolean force)
            throws ManipulationException {
        final CommonState state = session.getState(CommonState.class);
        final Map<String, PropertyMapper> projectProps = versionPropertyUpdateMap
                .computeIfAbsent(project, k -> new HashMap<>());
        boolean result = false;

        if (contains(oldVersion, "${")) {
            final int endIndex = oldVersion.indexOf('}');
            final String oldProperty = oldVersion.substring(2, endIndex);

            // We don't attempt to cache any value that contains more than one property or contains a property
            // combined with a hardcoded value.
            if (!(startsWith(oldVersion, "${") && endsWith(oldVersion, "}")) || (countMatches(oldVersion, "${") > 1)) {
                logger.debug(
                        "For {} ; original version contains hardcoded value or multiple embedded properties. Not caching value ( {} -> {} )",
                        originalType,
                        oldVersion,
                        newVersion);
            } else if ("project.version".equals(oldProperty)) {
                logger.debug(
                        "For {} ; original version was a property mapping. Not caching value as property is built-in ( {} -> {} )",
                        originalType,
                        oldProperty,
                        newVersion);
            } else {
                logger.debug(
                        "For {} ; original version was a property mapping; caching new value for update {} -> {} for project {}",
                        originalType,
                        oldProperty,
                        newVersion,
                        project);

                final String oldVersionProp = oldVersion.substring(2, oldVersion.length() - 1);
                final PropertyMapper container = projectProps
                        .computeIfAbsent(oldVersionProp, k -> new PropertyMapper());
                // We check if we are replacing a property and there is already a mapping. While we don't allow
                // a property to be updated to two different versions, if a dependencyExclusion (i.e. a force override)
                // has been specified this will bypass the check.
                final String existingPropertyMapping = container.getNewVersion();
                final ProjectRef originalReference;
                if (originalType instanceof ProjectVersionRef) {
                    // Don't need to call resolveProperties as these are already resolved.
                    originalReference = ((ProjectVersionRef) originalType).asProjectRef();
                } else if (originalType instanceof Plugin) {
                    originalReference = new SimpleProjectRef(
                            PropertyResolver.resolveProperties(
                                    session,
                                    project.getInheritedList(),
                                    ((Plugin) originalType).getGroupId()),
                            PropertyResolver.resolveProperties(
                                    session,
                                    project.getInheritedList(),
                                    ((Plugin) originalType).getArtifactId()));
                } else {
                    throw new ManipulationException(
                            "Unknown type for {}",
                            (originalType == null ? null : originalType.getClass()));
                }

                if (existingPropertyMapping != null && !existingPropertyMapping.equals(newVersion)) {
                    if (force) {
                        logger.debug(
                                "Override property replacement of {} with force version override {}",
                                existingPropertyMapping,
                                newVersion);
                    } else {
                        if (state.isPropertyClashFails()) {
                            logger.error(
                                    "Replacing property '{}' with a new version but the existing version does not match. "
                                            +
                                            "Old value is {} and new is {}. Context was replacing {} and clashed with {}",
                                    oldVersionProp,
                                    existingPropertyMapping,
                                    newVersion,
                                    originalType,
                                    container.getDependencies());
                            throw new ManipulationException(
                                    "Property replacement clash - updating property '{}' to both {} and {}",
                                    oldVersionProp,
                                    existingPropertyMapping,
                                    newVersion);
                        } else {
                            logger.warn(
                                    "Replacing property '{}' with a new version would clash with existing version which does not match. "
                                            +
                                            "Old value is {} and new is {}. Purging update of existing property. Context was replacing {} and clashed with {}",
                                    oldVersionProp,
                                    existingPropertyMapping,
                                    newVersion,
                                    originalType,
                                    container.getDependencies());
                            projectProps.remove(oldVersionProp);
                            return false;
                        }
                    }
                }

                container.getDependencies().add(originalReference);
                container.setOriginalVersion(findProperty(project, oldVersionProp));
                container.setNewVersion(newVersion);

                logger.debug("Container is {}", container);
                result = true;
            }
        }
        return result;
    }

    private static String findProperty(Project project, String prop) {
        return project.getReverseInheritedList()
                .stream()
                .filter(p -> p.getModel().getProperties().containsKey(prop))
                .map(p -> p.getModel().getProperties().getProperty(prop))
                .findAny()
                .orElse(null);
    }

    public static String extractPropertyName(String version) throws ManipulationException {
        // TODO: Handle the scenario where the version might be ${....}${....}

        final int endIndex = version.indexOf('}');

        if (version.indexOf("${") != 0 || endIndex != version.length() - 1) {
            throw new ManipulationException(
                    "NYI : handling for versions ({}) with either multiple embedded properties or embedded property and hardcoded string is NYI. ",
                    version);
        }
        return version.substring(2, endIndex);
    }

    public static void verifyPropertyMapping(
            CommonState cState,
            Project project,
            Map<Project, Map<String, PropertyMapper>> versionPropertyMap,
            ProjectVersionRef pvr,
            String version) throws ManipulationException {
        final Map<String, PropertyMapper> mapping = versionPropertyMap.get(project);

        // Its possible some dependencies that have versions were not updated at all and therefore are
        // not stored here.
        if (mapping.containsKey(version)) {
            final PropertyMapper currentProjectVersionMapper = mapping.get(version);

            if (!currentProjectVersionMapper.getDependencies().contains(pvr.asProjectRef())) {
                logger.debug(
                        "Scanning project {} with version {} and original value {}",
                        project,
                        version,
                        currentProjectVersionMapper.getOriginalVersion());

                if (cState.getStrictDependencyPluginPropertyValidation() == 2) {
                    for (final Entry<Project, Map<String, PropertyMapper>> entry : versionPropertyMap.entrySet()) {
                        final Project p = entry.getKey();
                        final Map<String, PropertyMapper> allProjectMapper = entry.getValue();
                        if (allProjectMapper.containsKey(version) &&
                        // Ignore when we have already caught this scenario and original / new versions match
                                !currentProjectVersionMapper.getOriginalVersion()
                                        .equals(allProjectMapper.get(version).getNewVersion())
                                &&
                                // Catch where same version property is used for different values
                                currentProjectVersionMapper.getOriginalVersion()
                                        .equals(
                                                allProjectMapper.get(version)
                                                        .getOriginalVersion())) {
                            logger.warn(
                                    "Project {} had a property {} that failed to validate to new version {} and is reverted to {}",
                                    p,
                                    version,
                                    allProjectMapper.get(version).getNewVersion(),
                                    allProjectMapper.get(version).getOriginalVersion());
                            allProjectMapper.get(version)
                                    .setNewVersion(currentProjectVersionMapper.getOriginalVersion());
                        }
                    }
                } else {
                    throw new ManipulationException(
                            "Dependency or Plugin {} within project {}} did not update property {} but it has been updated",
                            pvr,
                            project,
                            version);
                }
            }
        }
    }

    static Set<String> buildOldValueSet(VersioningState versioningState, String oldValue) {
        final Set<String> result = new HashSet<>();
        result.add(oldValue);

        // If there is more than one suffix (i.e. redhat and alternatives) then process the original
        // value with the alternate suffixes in order to dummy up potential values to test the conversion
        // against.
        if (versioningState.getAllSuffixes().size() > 1) {
            versioningState.getSuffixAlternatives().forEach(s -> {
                final String suffixStripRegExp = "(.*)([.|-])(" + s + "-\\d+)";
                final Pattern suffixStripPattern = Pattern.compile(suffixStripRegExp);
                final Matcher suffixMatcher = suffixStripPattern.matcher(oldValue);

                if (suffixMatcher.matches() && !oldValue.contains(versioningState.getRebuildSuffix())) {
                    // We could just add group(1) which would equate to a version without a suffix. But this
                    // can clash when processing from/to that both contain the alt. suffix.
                    //                        result.add( suffixMatcher.group( 1 ) );
                    result.add(
                            suffixMatcher.group(1) + suffixMatcher.group(2) + versioningState.getRebuildSuffix()
                                    + "-0");
                }
            });
        }

        logger.debug("Generated original value set for matching {}", result);
        return result;
    }

    /**
     * Common code used between CLI and EXT.
     *
     * @param userProperties User properties stored internally.
     * @param config Properties to merge in.
     */
    public static void handleConfigPrecedence(Properties userProperties, Properties config) {
        String value = userProperties.getProperty(ConfigIO.CONFIG_FILE_PRECEDENCE);
        if (isNotEmpty(value) && "true".equalsIgnoreCase(value)) {
            userProperties.putAll(config);
        } else {
            for (String key : config.stringPropertyNames()) {
                if (!userProperties.containsKey(key)) {
                    userProperties.setProperty(key, config.getProperty(key));
                }
            }
        }
    }

    /**
     * Used to determine whether any property updates were successful of not. In the case of detecting that no
     * properties are
     * needed IGNORE is returned. Effectively this is a slightly more explicit tri-state.
     */
    public enum PropertyUpdate {
        FOUND,
        NOTFOUND,
        IGNORE
    }
}
