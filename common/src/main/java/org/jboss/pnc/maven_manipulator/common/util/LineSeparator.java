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

package org.jboss.pnc.maven_manipulator.common.util;

/**
 * Partially taken from org.jdom2.output.LineSeparator as the Maven Release Plugin uses JDOM1 not 2.
 */
public enum LineSeparator {
    /**
     * The Separator sequence CRNL which is '\r\n'.
     * This is the default sequence.
     */
    CRNL("\r\n"),

    /**
     * The Separator sequence NL which is '\n'.
     */
    NL("\n"),
    /**
     * The Separator sequence CR which is '\r'.
     */
    CR("\r"),

    /** The 'DOS' Separator sequence CRLF (CRNL) which is '\r\n'. */
    DOS("\r\n"),

    /** The 'UNIX' Separator sequence NL which is '\n'. */
    UNIX("\n");

    private final String value;

    LineSeparator(String value) {
        this.value = value;
    }

    /**
     * The String sequence used for this Separator
     *
     * @return an End-Of-Line String
     */
    public String value() {
        return value;
    }
}
