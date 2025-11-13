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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

public class SuffixStateTest {
    @Test
    public void defaultSuffixIsSet() {
        Properties p = new Properties();
        p.setProperty(SuffixState.SUFFIX_STRIP_PROPERTY, "");
        SuffixState s = new SuffixState(p);

        assertTrue(StringUtils.isNotEmpty(s.getSuffixStrip()));
        assertTrue(s.isEnabled());
    }

    @Test
    public void disabledSuffixIsSet() {
        Properties p = new Properties();
        p.setProperty(SuffixState.SUFFIX_STRIP_PROPERTY, "NONE");
        SuffixState s = new SuffixState(p);

        assertTrue(StringUtils.isEmpty(s.getSuffixStrip()));
        assertFalse(s.isEnabled());
    }

    @Test
    public void testDefaultSuffix() {
        Properties props = new Properties();
        props.setProperty(SuffixState.SUFFIX_STRIP_PROPERTY, "");
        SuffixState s = new SuffixState(props);

        Pattern p = Pattern.compile(s.getSuffixStrip());

        Matcher m = p.matcher("1.0.jbossorg-101909");
        assertTrue(m.matches());
        assertEquals("1.0", m.group(1));
        assertEquals(".jbossorg-101909", m.group(2));

        m = p.matcher("1.0.Final-jbossorg-1");
        assertTrue(m.matches());
        assertEquals("1.0.Final", m.group(1));
        assertEquals("-jbossorg-1", m.group(2));
    }

    @Test
    public void testCustomSuffix() {
        Properties props = new Properties();
        props.setProperty(SuffixState.SUFFIX_STRIP_PROPERTY, "(.*)(.MYSUFFIX)$");
        SuffixState s = new SuffixState(props);

        Pattern p = Pattern.compile(s.getSuffixStrip());

        Matcher m = p.matcher("1.0.jbossorg-101909");
        assertFalse(m.matches());

        m = p.matcher("1.0.MYSUFFIX");
        assertTrue(m.matches());
        assertEquals("1.0", m.group(1));
        assertEquals(".MYSUFFIX", m.group(2));
    }
}