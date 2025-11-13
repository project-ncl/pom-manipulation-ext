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
package org.jboss.pnc.mavenmanipulator.core.impl;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Component that reads a version string and makes various modifications to it such as converting to a valid OSGi
 * version string and/or incrementing a version suffix. See: http://www.aqute.biz/Bnd/Versioning for an explanation of
 * OSGi versioning. Parses versions into the following format: &lt;major&gt;.&lt;minor&gt;.&lt;micro&gt;
 * .&lt;qualifierBase&gt;-&lt;buildnumber&gt;-&lt;buildnumber&gt;-&lt;snapshot&gt;
 */
@SuppressWarnings("WeakerAccess") // Public API.
public class Version {
    private static final Logger logger = LoggerFactory.getLogger(Version.class);

    private final static String EMPTY_STRING = "";

    private final static Character[] DEFAULT_DELIMITERS = { '.', '-', '_' };

    private final static String OSGI_VERSION_DELIMITER = ".";

    private final static String OSGI_QUALIFIER_DELIMITER = "-";

    private final static String DEFAULT_DELIMITER = ".";

    /**
     * The default delimiter between the different parts of the qualifier
     */
    private final static String DEFAULT_QUALIFIER_DELIMITER = "-";

    private final static List<Character> versionStringDelimiters = Arrays.asList(DEFAULT_DELIMITERS);

    /**
     * Regular expression used to match version string delimiters
     */
    private final static String DELIMITER_REGEX = "[.\\-_]";

    /**
     * Regular expression used to match version string delimiters
     */
    private final static String LEADING_DELIMITER_REGEX = "^" + DELIMITER_REGEX;

    /**
     * Regular expression used to match the major, minor, and micro versions
     */
    private final static String MMM_REGEX = "(\\d+)(" + DELIMITER_REGEX + "(\\d+)(" + DELIMITER_REGEX + "(\\d+))?)?";

    private final static Pattern mmmPattern = Pattern.compile(MMM_REGEX);

    private final static String SNAPSHOT_SUFFIX = "SNAPSHOT";

    private final static String SNAPSHOT_REGEX = "(.*?)((" + DELIMITER_REGEX + ")?((?i:" + SNAPSHOT_SUFFIX + ")))$";

    private final static Pattern snapshotPattern = Pattern.compile(SNAPSHOT_REGEX);

    /**
     * Regular expression used to match the parts of the qualifier "base-buildnum-snapshot"
     * Note : Technically within the rebuild-numeric the dash is currently optional and can be any
     * delimeter type within the regex. It could be made mandatory via '{1}'.
     */
    private final static String QUALIFIER_REGEX = "(.*?)((" + DELIMITER_REGEX + ")?(\\d+))?((" + DELIMITER_REGEX
            + ")?((?i:" + SNAPSHOT_SUFFIX + ")))?$";

    private final static Pattern qualifierPattern = Pattern.compile(QUALIFIER_REGEX);

    /**
     * Version string must start with a digit to match the regex. Otherwise we have only a
     * qualifier.
     */
    private final static String VERSION_REGEX = "(" + MMM_REGEX + ")" + "((" + DELIMITER_REGEX + ")?"
            + "(" + QUALIFIER_REGEX + "))";

    private final static Pattern versionPattern = Pattern.compile(VERSION_REGEX);

    /**
     * Used to match valid OSGi version based on section 3.2.5 of the OSGi specification
     */
    private final static String OSGI_VERSION_REGEX = "(\\d+)(\\.\\d+(\\.\\d+(\\.[\\w\\-_]+)?)?)?";

    private final static Pattern osgiPattern = Pattern.compile(OSGI_VERSION_REGEX);

    public static final String PROJECT_VERSION = "${project.version}";

    // Prevent construction.
    private Version() {
    }

    public static String getBuildNumber(String version) {
        Matcher qualifierMatcher = qualifierPattern.matcher(getQualifier(version));
        if (qualifierMatcher.matches() && !isEmpty(qualifierMatcher.group(4))) {
            return qualifierMatcher.group(4);
        }
        return EMPTY_STRING;
    }

    /**
     * This function will examine the set of known versions and, assuming there is no forced
     * incrementalSerialSuffixPadding override, locate the padding to apply to the new version.
     * This allows us to maintain any existing padding transparently.
     *
     * @param incrementalSerialSuffixPadding if there is an explicit padding override.
     * @param versions the candidate versions to examine.
     * @return the amount of padding (indexed from 1) to apply.
     */
    public static int getBuildNumberPadding(int incrementalSerialSuffixPadding, Set<String> versions) {
        int result = incrementalSerialSuffixPadding;

        // Serial padding has not been explicitly defined so determine current padding to preserve.
        if (result == 0) {
            result = getBuildNumber(
                    versions.stream().max(Comparator.comparing(v -> getBuildNumber(v).length())).orElse("")).length();
        }
        logger.debug("Returning padding of {}", result);
        return result;
    }

    /**
     * Returns the initial numeric portion (which may be up to 3 digits long) excluding
     * any delimeter suffix.
     *
     * @param version the version to examine
     * @return a parsed string version.
     */
    public static String getMMM(String version) {
        Matcher versionMatcher = versionPattern.matcher(version);
        if (versionMatcher.matches()) {
            return versionMatcher.group(1);
        }
        return EMPTY_STRING;
    }

    /**
     * Get the major, minor, micro version in OSGi format
     *
     * @param version The version to parse
     * @param fill Whether to fill the minor and micro versions with zeros if they are missing
     * @return OSGi formatted major, minor, micro
     */
    public static String getOsgiMMM(String version, boolean fill) {
        String mmm = getMMM(version);
        Matcher mmmMatcher = mmmPattern.matcher(mmm);
        if (mmmMatcher.matches()) {
            String osgiMMM = mmmMatcher.group(1);
            String minorVersion = mmmMatcher.group(3);
            if (!isEmpty(minorVersion)) {
                osgiMMM += OSGI_VERSION_DELIMITER + minorVersion;
            } else if (fill) {
                osgiMMM += OSGI_VERSION_DELIMITER + "0";
            }
            String microVersion = mmmMatcher.group(5);
            if (!isEmpty(microVersion)) {
                osgiMMM += OSGI_VERSION_DELIMITER + microVersion;
            } else if (fill) {
                osgiMMM += OSGI_VERSION_DELIMITER + "0";
            }
            return osgiMMM;
        }
        return EMPTY_STRING;
    }

    public static String getOsgiVersion(String version) {
        String qualifier = getQualifier(version);
        if (!isEmpty(qualifier)) {
            qualifier = OSGI_VERSION_DELIMITER + qualifier.replace(OSGI_VERSION_DELIMITER, OSGI_QUALIFIER_DELIMITER);
        }
        String mmm = getOsgiMMM(version, !isEmpty(qualifier));
        if (isEmpty(mmm)) {
            logger.warn("Unable to parse version for OSGi: {}", version);
            return version;
        }
        return mmm + qualifier;
    }

    public static String getQualifier(String version) {
        Matcher versionMatcher = versionPattern.matcher(version);
        if (versionMatcher.matches()) {
            return versionMatcher.group(9);
        }
        return removeLeadingDelimiter(version);
    }

    /**
     * This will return the OSGi qualifier portion without the numeric rebuild increment.
     * For example
     *
     * <blockquote>
     * <table>
     * <caption>Version conversion</caption>
     * <tr>
     * <th>Version</th>
     * <th>Qualifier</th>
     * </tr>
     * <tr>
     * <td>1.0.0.Beta1</td>
     * <td>Beta1</td>
     * <tr>
     * <td>1.0.0.Beta10-rebuild-1</td>
     * <td>Beta10-rebuild</td>
     * <tr>
     * <td>1.0.0.GA-rebuild1</td>
     * <td>GA-rebuild</td>
     * <tr>
     * <td>1.0.0.Final-Beta-1</td>
     * <td>Final-Beta</td>
     * </tr>
     * </table>
     * </blockquote>
     *
     * @param version a {@code String} to parse
     * @return the qualifier
     */
    public static String getQualifierBase(String version) {
        Matcher versionMatcher = versionPattern.matcher(version);
        if (versionMatcher.matches()) {
            return versionMatcher.group(10);
        }
        Matcher qualifierMatcher = qualifierPattern.matcher(version);
        if (qualifierMatcher.matches()) {
            return qualifierMatcher.group(1);
        }
        return removeLeadingDelimiter(version);
    }

    public static String getQualifierWithDelim(String version) {
        Matcher versionMatcher = versionPattern.matcher(version);
        if (versionMatcher.matches()) {
            return versionMatcher.group(7);
        }
        return version;
    }

    public static String getSnapshot(String version) {
        Matcher snapshotMatcher = snapshotPattern.matcher(version);
        if (snapshotMatcher.matches()) {
            return snapshotMatcher.group(4);
        }
        return EMPTY_STRING;
    }

    public static String getSnapshotWithDelim(String version) {
        Matcher snapshotMatcher = snapshotPattern.matcher(version);
        if (snapshotMatcher.matches()) {
            return snapshotMatcher.group(2);
        }
        return EMPTY_STRING;
    }

    public static boolean hasBuildNumber(String version) {
        return !isEmpty(getBuildNumber(version));
    }

    public static boolean hasQualifier(String version) {
        return !isEmpty(getQualifier(version));
    }

    public static boolean isEmpty(String string) {
        return (string == null || EMPTY_STRING.equals(string.trim()));
    }

    public static boolean isSnapshot(String version) {
        Matcher snapshotMatcher = snapshotPattern.matcher(version);
        return snapshotMatcher.matches();
    }

    /**
     * Checks if the string is a valid version according to section 3.2.5 of the OSGi specification
     *
     * @param version the version
     * @return true if the version is valid
     */
    public static boolean isValidOSGi(String version) {
        return osgiPattern.matcher(version).matches();
    }

    /**
     * Remove the build number (and associated delimiter) portion of the version string.
     *
     * @param version the version
     * @return the version with the build number portion removed
     */
    public static String removeBuildNumber(String version) {
        Matcher qualifierMatcher = qualifierPattern.matcher(version);
        if (qualifierMatcher.matches()) {
            return qualifierMatcher.replaceFirst("$1$5");
        }
        return version;
    }

    /**
     * Remove the snapshot (and associated delimiter) portion of the version string.
     * Converts something like "1.0-SNAPSHOT" to "1.0".
     *
     * @param version the version
     * @return the version with the snapshot portion removed
     */
    public static String removeSnapshot(String version) {
        Matcher snapshotMatcher = snapshotPattern.matcher(version);
        if (snapshotMatcher.matches()) {
            return snapshotMatcher.group(1);
        }
        return version;
    }

    private static boolean hasLeadingDelimiter(String versionPart) {
        if (versionPart.length() < 1) {
            return false;
        }
        return versionStringDelimiters.contains(versionPart.charAt(0));
    }

    /**
     * Remove any leading delimiters from the partial version string.
     *
     * @param versionPart the partial version
     * @return the partial version with any leading delimiters removed
     */
    static String removeLeadingDelimiter(String versionPart) {
        return versionPart.replaceAll(LEADING_DELIMITER_REGEX, "");
    }

    /**
     * Prepends the given delimiter only if the versionPart does not already start with a delimiter.
     *
     * @param versionPart the partial version
     * @param delimiter the delimiter
     * @return the partial version with the delimiter prepended (if necessary)
     */
    private static String prependDelimiter(String versionPart, String delimiter) {
        if (hasLeadingDelimiter(versionPart)) {
            return versionPart;
        }
        return delimiter + versionPart;
    }

    /**
     * Check if all the characters in the string are digits
     *
     * @param str a string to check
     * @return whether all the characters in the string are digits
     */
    private static boolean isNumeric(String str) {
        for (char c : str.toCharArray()) {
            if (!Character.isDigit(c))
                return false;
        }
        return true;
    }

    /**
     * Appends the given qualifier suffix. Attempts to match the given qualifier suffix to the current suffix
     * to avoid duplicates like "1.0-beta-beta. If the suffix matches the existing one, does nothing.
     *
     * @param version the version
     * @param suffix The qualifier suffix to append. This can be a simple string like "foo", or it can optionally
     *        include a build number, for example "foo-1", which will automatically be set as the build number for
     *        this version.
     * @return the version with the qualifier suffix appended (if necessary)
     */
    public static String appendQualifierSuffix(final String version, final String suffix) {
        logger.debug("Applying suffix: {} to version {}", suffix, version);

        if (isEmpty(suffix)) {
            return version;
        }

        if (isEmpty(getQualifier(version))) {
            return version + prependDelimiter(suffix, DEFAULT_DELIMITER);
        }

        Matcher suffixMatcher = createSuffixMatcher(version, suffix);
        if (suffixMatcher.matches()) {
            return version;
        }

        final String suffixWoSnapshot = removeSnapshot(suffix);
        final String suffixBuildNumber = getBuildNumber(suffix);
        final String suffixWoBuildNumber = removeBuildNumber(suffixWoSnapshot);
        final String suffixWoBuildNumDelim = removeLeadingDelimiter(suffixWoBuildNumber);

        Matcher suffixWoDelimMatcher = createSuffixMatcher(version, suffixWoBuildNumDelim);
        if (suffixWoDelimMatcher.matches()) {
            String newVersion = suffixWoDelimMatcher.replaceFirst("$1$2" + suffixWoBuildNumber + "$4$7");
            if (hasLeadingDelimiter(suffix)) {
                newVersion = suffixWoDelimMatcher.replaceFirst("$1" + suffixWoBuildNumber + "$4$7");
            }
            if (!isEmpty(suffixBuildNumber)) {
                newVersion = setBuildNumber(newVersion, suffixBuildNumber);
            }
            if (isSnapshot(suffix)) {
                newVersion = setSnapshot(newVersion, true);
            }
            return newVersion;
        }

        Matcher qualifierMatcher = qualifierPattern.matcher(version);
        if (qualifierMatcher.matches()) {
            if (hasLeadingDelimiter(suffixWoSnapshot)) {
                String newVersion = qualifierMatcher.replaceFirst("$1$3$4" + suffixWoSnapshot + "$5");
                if (isSnapshot(suffix)) {
                    newVersion = Version.setSnapshot(newVersion, true);
                }
                return newVersion;
            }
            String delimiter = DEFAULT_QUALIFIER_DELIMITER;
            if (isEmpty(getQualifierBase(version))) {
                delimiter = DEFAULT_DELIMITER;
            }
            String newVersion = qualifierMatcher.replaceFirst("$1$3$4" + delimiter + suffixWoSnapshot + "$5");
            if (isSnapshot(suffix)) {
                newVersion = Version.setSnapshot(newVersion, true);
            }
            return newVersion;
        }
        return version;
    }

    private static Matcher createSuffixMatcher(String version, String suffix) {
        final String SUFFIX_REGEX = "(.*?)(" + DELIMITER_REGEX + ")?(" + suffix + ")(("
                + DELIMITER_REGEX + ")?(\\d+))?((" + DELIMITER_REGEX + ")?((?i:" + SNAPSHOT_SUFFIX + ")))?$";
        return Pattern.compile(SUFFIX_REGEX).matcher(version);
    }

    /**
     * Appends a build number to the qualifier. The build number should be a string of digits, or null if the build
     * number should be removed.
     *
     * @param version the version
     * @param buildNumber the build number to append to the qualifier
     * @return the version with the build number appended (if necessary)
     */
    public static String setBuildNumber(String version, String buildNumber) {
        if (!isNumeric(buildNumber)) {
            logger.warn("Failed attempt to set non-numeric build number '{}' for version '{}'", buildNumber, version);
            return version;
        }
        if (isEmpty(buildNumber)) {
            buildNumber = EMPTY_STRING;
        }
        if (isEmpty(getQualifier(version))) {
            return version + DEFAULT_DELIMITER + buildNumber;
        }
        Matcher qualifierMatcher = qualifierPattern.matcher(version);
        if (qualifierMatcher.matches()) {
            if (isEmpty(qualifierMatcher.group(2))) {
                buildNumber = prependDelimiter(buildNumber, DEFAULT_QUALIFIER_DELIMITER);
            }
            return qualifierMatcher.replaceFirst("$1$3" + buildNumber + "$5");
        }
        return version;
    }

    /**
     * Appends the "SNAPSHOT" suffix to this version if not already present
     *
     * @param version the version to modify
     * @param snapshot set to true if the version should be a snapshot, false otherwise
     * @return the updated version string
     */
    public static String setSnapshot(String version, boolean snapshot) {
        if (isEmpty(version)) {
            return EMPTY_STRING;
        }
        if (isSnapshot(version) == snapshot) {
            return version;
        } else if (snapshot) {
            return (version + DEFAULT_QUALIFIER_DELIMITER + SNAPSHOT_SUFFIX);
        } else {
            return removeSnapshot(version);
        }
    }

    /**
     * Matches a version object to versions in a set by comparing the non build number portion of the string. Then find
     * which of the matching versions has the highest build number and is therefore the latest version.
     *
     * @param version the Version object to use
     * @param versionSet a collection of versions to compare to
     * @return the highest build number, or 0 if no matching build numbers are found.
     */
    public static int findHighestMatchingBuildNumber(String version, Set<String> versionSet) {
        int highestBuildNum = 0;

        String osgiVersion = getOsgiVersion(version);
        String qualifier = getQualifier(osgiVersion);
        Matcher qualifierMatcher = qualifierPattern.matcher(qualifier);
        if (qualifierMatcher.matches()) {
            qualifier = removeLeadingDelimiter(qualifierMatcher.group(1));
        }

        // Build version pattern regex, matches something like "<mmm>.<qualifier>.<buildnum>".
        StringBuilder versionPatternBuf = new StringBuilder();
        versionPatternBuf.append('(')
                .append(Pattern.quote(getMMM(version)))
                .append('(')
                .append(DELIMITER_REGEX)
                .append("0)*") // Match zeros appended to a major only version
                .append(")?")
                .append(DELIMITER_REGEX);

        if (!isEmpty(qualifier)) {
            versionPatternBuf.append(Pattern.quote(qualifier));
            versionPatternBuf.append(DELIMITER_REGEX);
        }
        versionPatternBuf.append("(\\d+)");
        String candidatePatternStr = versionPatternBuf.toString();

        logger.debug("Using pattern: '{}' to find compatible versions from metadata.", candidatePatternStr);
        final Pattern candidateSuffixPattern = Pattern.compile(candidatePatternStr);

        for (final String compareVersion : versionSet) {
            final Matcher candidateSuffixMatcher = candidateSuffixPattern.matcher(compareVersion);
            if (candidateSuffixMatcher.matches()) {
                String buildNumberStr = candidateSuffixMatcher.group(3);
                int compareBuildNum = Integer.parseInt(buildNumberStr);
                if (compareBuildNum > highestBuildNum) {
                    highestBuildNum = compareBuildNum;
                }
            }
        }
        logger.debug("Found highest matching build number {} from set {}", highestBuildNum, versionSet);

        return highestBuildNum;
    }

    /**
     * Get the build number as an integer instead of a string for each numeric comparison.
     *
     * @param version the version
     * @return the build number as an integer.
     */
    public static int getIntegerBuildNumber(String version) {
        String buildNumber = getBuildNumber(version);
        if (isEmpty(buildNumber)) {
            return 0;
        }
        try {
            return Integer.parseInt(buildNumber);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
