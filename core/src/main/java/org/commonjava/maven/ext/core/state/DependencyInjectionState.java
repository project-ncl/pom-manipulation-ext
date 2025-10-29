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
package org.commonjava.maven.ext.core.state;

import static org.apache.commons.lang3.StringUtils.isEmpty;

import java.util.List;
import java.util.Properties;

import org.apache.maven.model.Dependency;
import org.commonjava.atlas.maven.ident.ref.InvalidRefException;
import org.commonjava.maven.ext.annotation.ConfigValue;
import org.commonjava.maven.ext.core.util.RefParseUtils;

import lombok.Getter;

/**
 * Captures configuration relating to dependency injection from the POMs.
 */
public class DependencyInjectionState
        implements State {
    /**
     * The name of the property which contains a comma separated list of dependencies to inject. Each
     * dependency may be specified in one of the following formats. In all cases, the {@code groupId},
     * {@code artifactId}, and {@code version} are required fields. All other fields are optional and
     * will be treated as {@code null} when absent or blank.
     *
     * <ul>
     * <li>GAV - {@code groupId:artifactId:version}</li>
     * <li>GATV - {@code groupId:artifactId:type:version}</li>
     * <li>GATCV - {@code groupId:artifactId:type:classifier:version}</li>
     * <li>GATCVS - {@code groupId:artifactId:type:classifier:version:scope}</li>
     * </ul>
     *
     * <pre>
     * <code>-DdependencyInjection=org.foo:bar:1.0,org.baz:bar:pom::2.0:import,....</code>
     * </pre>
     */
    @ConfigValue(docIndex = "dep-manip.html#dependency-injection")
    private static final String DEPENDENCY_INJECTION_PROPERTY = "dependencyInjection";

    /**
     * This will update the
     * <a href=
     * "https://maven.apache.org/plugins/maven-dependency-plugin/analyze-mojo.html#dependency-analyze">maven-dependency-plugin</a>
     * plugin so that a section with ignoredUnusedDeclaredDependencies is added for each dependency if the
     * maven-dependency-plugin is declared in the root pom.
     */
    @ConfigValue(docIndex = "dep-manip.html#dependency-injection-assembly")
    private static final String DEPENDENCY_INJECTION_ANALYZE_PLUGIN_PROPERTY = "dependencyInjectionAnalyzeIgnoreUnused";

    /**
     * @return the dependencies we wish to inject into the POM's {@code dependencyManagement}
     *         section.
     */
    @Getter
    private List<Dependency> dependencyInjection;

    @Getter
    private boolean addIgnoreUnusedAnalzyePlugin;

    public DependencyInjectionState(final Properties userProps) {
        initialise(userProps);
    }

    public void initialise(Properties userProps) {
        dependencyInjection = parseDependencies(userProps.getProperty(DEPENDENCY_INJECTION_PROPERTY));
        addIgnoreUnusedAnalzyePlugin = Boolean.parseBoolean(
                userProps.getProperty(
                        DEPENDENCY_INJECTION_ANALYZE_PLUGIN_PROPERTY,
                        "false"));
    }

    /**
     * Splits the value on ',', then parses each element with {@link #parseDependency(String)} Returns null
     * if the input value is null or empty.
     *
     * @param value a comma separated list of dependencies to parse
     * @return a list of parsed Dependency instances.
     */
    private static List<Dependency> parseDependencies(final String value) {
        return RefParseUtils.parseRefs(value, DependencyInjectionState::parseDependency);
    }

    private static Dependency parseDependency(String dependencySpec) {
        final String[] parts = dependencySpec.split(":");
        final Dependency d = new Dependency();

        if (parts.length < 3) {
            throw invalidRefException();
        }

        d.setGroupId(nullIfEmpty(parts[0]));
        d.setArtifactId(nullIfEmpty(parts[1]));

        switch (parts.length) {
            case 3:
                d.setVersion(nullIfEmpty(parts[2]));
                break;
            case 4:
                d.setType(nullIfEmpty(parts[2]));
                d.setVersion(nullIfEmpty(parts[3]));
                break;
            case 5:
                d.setType(nullIfEmpty(parts[2]));
                d.setClassifier(nullIfEmpty(parts[3]));
                d.setVersion(nullIfEmpty(parts[4]));
                break;
            case 6:
                d.setType(nullIfEmpty(parts[2]));
                d.setClassifier(nullIfEmpty(parts[3]));
                d.setVersion(nullIfEmpty(parts[4]));
                d.setScope(nullIfEmpty(parts[5]));
                break;
            default:
                throw invalidRefException();
        }

        if (isEmpty(d.getGroupId()) || isEmpty(d.getArtifactId()) || isEmpty(d.getVersion())) {
            throw new InvalidRefException("dependency groupId, artifactId, and version are required");
        }

        return d;
    }

    private static String nullIfEmpty(String value) {
        return isEmpty(value) ? null : value;
    }

    static InvalidRefException invalidRefException() {
        return new InvalidRefException(
                "dependency must be formatted as one of: "
                        + "groupId:artifactId:version, "
                        + "groupId:artifactId:type:version, "
                        + "groupId:artifactId:type:classifier:version, or"
                        + "groupId:artifactId:type:classifier:version:scope");
    }

    /**
     * Enabled ONLY if {@code dependencyInjection} is provided in the user properties / CLI -D options.
     *
     * @see State#isEnabled()
     */
    @Override
    public boolean isEnabled() {
        return dependencyInjection != null && !dependencyInjection.isEmpty();
    }
}
