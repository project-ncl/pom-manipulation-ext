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
package org.commonjava.maven.ext.core.groovy;

import lombok.Getter;

/**
 * Denotes when the groovy script should be run in relation to the other manipulators.
 * <p>
 * </p>
 * Effectively the same as {@link org.jboss.pnc.mavenmanipulator.core.groovy.InvocationStage}
 */
public enum InvocationStage {
    /**
     * Run before any projects are parsed, i.e., before any manipulators.
     *
     * @since 4.6
     */
    PREPARSE(0),
    /**
     * Run before any other manipulators.
     */
    FIRST(1),
    /**
     * Run after any other manipulators.
     */
    LAST(99),
    /**
     * Run during all stages.
     *
     * @since 4.6
     */
    ALL(Integer.MAX_VALUE);

    /**
     * Gets the stage value.
     *
     * @return the stage value
     */
    @Getter
    private final int stageValue;

    InvocationStage(int stageValue) {
        this.stageValue = stageValue;
    }

    /**
     * Returns the {@code InvocationStage} representing the specified {@code int} value.
     *
     * @param stageValue an {@code int} value
     * @return the {@code InvocationStage} representing {@code stageValue}
     * @since 4.6
     */
    public static InvocationStage valueOf(int stageValue) {
        InvocationStage result = null;

        for (InvocationStage stage : values()) {
            if (stage.getStageValue() == stageValue) {
                result = stage;
                break;
            }
        }

        return result;
    }
}
