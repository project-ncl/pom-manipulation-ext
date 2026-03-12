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
package org.jboss.pnc.mavenmanipulator.common.util;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import org.jboss.pnc.mavenmanipulator.common.json.PME;
import org.junit.Test;

public class JSONUtilsTest {
    @Test
    public void fileToJSON() throws IOException {
        PME pme = JSONUtils.fileToJSON(resolveFileResource());

        assertEquals("org.kie:kie-parent:7.24.0.Final", pme.getGav().getOriginalGAV());
        assertEquals(11, pme.getModules().size());
        assertEquals(pme.getModules().get(0).getGav().getPVR(), pme.getGav().getPVR());
    }

    private static File resolveFileResource()
            throws IOException {
        final URL resource = Thread.currentThread()
                .getContextClassLoader()
                .getResource("sample.json");

        if (resource == null) {
            throw new IOException("Unable to locate resource for " + "sample.json");
        }
        return new File(resource.getPath());
    }
}
