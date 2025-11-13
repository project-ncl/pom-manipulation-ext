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

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.maven.artifact.ArtifactScopeEnum;
import org.jboss.pnc.mavenmanipulator.annotation.ConfigValue;
import org.jboss.pnc.mavenmanipulator.common.ManipulationException;
import org.jboss.pnc.mavenmanipulator.core.impl.DependencyManipulator;
import org.jboss.pnc.mavenmanipulator.core.impl.PluginManipulator;

import lombok.Getter;
import lombok.Setter;

/**
 * Captures configuration relating to plugin/dependency alignment from the POMs.
 * Used by {@link PluginManipulator} and {@link DependencyManipulator}
 */
@Getter
public class CommonState
        implements State {
    /**
     * Whether to override dependencies/plugins that are not directly specified in the project
     */
    @ConfigValue(docIndex = "dep-manip.html#directtransitive-dependencies")
    public static final String TRANSITIVE_OVERRIDE_PROPERTY = "overrideTransitive";

    /**
     * When true, clashes with cached properties will throw an exception in PropertyResolver. Setting this to false will
     * prevent
     * that. Default value is true.
     */
    @ConfigValue(docIndex = "dep-manip.html#property-clash-replacement")
    public static final String PROPERTY_CLASH_FAILS = "propertyClashFails";

    /**
     * Enables strict checking of non-exclusion dependency versions before aligning to the given BOM dependencies.
     * For example, <code>1.1</code> will match <code>1.1-rebuild-1</code> in strict mode, but <code>1.2</code> will
     * not.
     */
    @ConfigValue(docIndex = "dep-manip.html#strict-mode-version-alignment")
    public static final String STRICT_ALIGNMENT = "strictAlignment";

    /**
     * When false, strict version-alignment violations will be reported in the warning log-level, but WILL NOT FAIL THE
     * BUILD. When true, the build
     * will fail if such a violation is detected. Default value is false.
     */
    @ConfigValue(docIndex = "dep-manip.html#strict-mode-version-alignment")
    public static final String STRICT_VIOLATION_FAILS = "strictViolationFails";

    /**
     * When true, it will ignore any suffix ( e.g. rebuild-2 ) on the source version during comparisons. Further, it
     * will
     * only allow alignment to a higher incrementing suffix (e.g. rebuild-3 ).
     */
    @ConfigValue(docIndex = "dep-manip.html#strict-mode-version-alignment")
    public static final String STRICT_ALIGNMENT_IGNORE_SUFFIX = "strictAlignmentIgnoreSuffix";

    /**
     * Comma separated list of scopes to exclude and ignore when operating.
     */
    @ConfigValue(docIndex = "dep-manip.html#scope-exclusion")
    public static final String EXCLUDED_SCOPES = "excludedScopes";

    /**
     * This aggressively checks whether, for a set of dependencies or plugins that have a common property, every
     * dependency
     * or plugin attempted to update the property. If one didn't this will throw an exception and fail fast. If it
     * doesn't fail
     * then its possible that one of the newly aligned dependencies/plugins aren't found and therefore the build will
     * fail.
     *
     * If set to false (the default) this is not active.
     * If set to true, then this will throw an exception.
     * If set to the string 'revert' then it will revert any changes, emitting warnings.
     */
    @ConfigValue(docIndex = "dep-manip.html#strict-property-validation")
    public static final String DEPENDENCY_PROPERTY_VALIDATION = "strictPropertyValidation";

    /**
     * Whether to override transitive as well. This is common between {@link DependencyState} and
     * {@link DependencyState}.
     *
     * @return whether to override transitive
     */
    private boolean overrideTransitive;

    private boolean propertyClashFails;

    private boolean strict;

    private boolean failOnStrictViolation;

    private boolean strictIgnoreSuffix;

    /**
     * This is a beta option for overriding strict property validation. If 0, then strict dependency plugin property
     * validation is disabled. If 1, then strict dependency plugin property validation is enabled. If 2, then assume we
     * are reverting.
     *
     * @return 0 if strict dependency plugin property validation is disabled, 1 if enabled, or 2 if we are reverting
     */
    @Setter
    private Integer strictDependencyPluginPropertyValidation;

    private List<String> excludedScopes;

    public CommonState(final Properties userProps) throws ManipulationException {
        initialise(userProps);
    }

    @Override
    public void initialise(Properties userProps) throws ManipulationException {
        overrideTransitive = Boolean.parseBoolean(userProps.getProperty(TRANSITIVE_OVERRIDE_PROPERTY, "false"));
        propertyClashFails = Boolean.parseBoolean(userProps.getProperty(PROPERTY_CLASH_FAILS, "true"));
        strict = Boolean.parseBoolean(userProps.getProperty(STRICT_ALIGNMENT, "true"));
        strictIgnoreSuffix = Boolean.parseBoolean(userProps.getProperty(STRICT_ALIGNMENT_IGNORE_SUFFIX, "true"));
        failOnStrictViolation = Boolean.parseBoolean(userProps.getProperty(STRICT_VIOLATION_FAILS, "false"));
        excludedScopes = Arrays.asList(
                userProps.getProperty(EXCLUDED_SCOPES, "").length() > 0
                        ? userProps.getProperty(EXCLUDED_SCOPES).split(",")
                        : new String[0]);
        for (String s : excludedScopes) {
            try {
                ArtifactScopeEnum.valueOf(s);
            } catch (IllegalArgumentException e) {
                throw new ManipulationException("Illegal scope value {}", s);
            }
        }

        switch (userProps.getProperty(DEPENDENCY_PROPERTY_VALIDATION, "false").toUpperCase()) {
            case "TRUE": {
                strictDependencyPluginPropertyValidation = 1;
                break;
            }
            case "FALSE": {
                strictDependencyPluginPropertyValidation = 0;
                break;
            }
            case "REVERT": {
                strictDependencyPluginPropertyValidation = 2;
                break;
            }
            default: {
                strictDependencyPluginPropertyValidation = 0;
                break;
            }
        }
    }

    /**
     * Normally isEnabled is tied to the State of the Manipulator. However because these are common properties that do
     * not activate PME but dependent upon other Manipulator (specifically Dependency and Plugin) properties to activate
     * this should always return false so not to confuse PME whether its active or not.
     */
    @Override
    public boolean isEnabled() {
        return false;
    }
}
