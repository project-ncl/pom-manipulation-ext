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

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.model.Model;
import org.apache.maven.model.Profile;
import org.jboss.pnc.maven_manipulator.annotation.ConfigValue;
import org.jboss.pnc.maven_manipulator.common.session.MavenSessionHandler;

import lombok.experimental.UtilityClass;

/**
 * Commonly used manipulations from project profiles.
 */
@UtilityClass
public final class ProfileUtils {
    /**
     * Denotes whether we only scan active profiles. Default is true (we scan only active profiles).
     */
    @ConfigValue(docIndex = "misc.html#profile-handling")
    public static final String PROFILE_SCANNING = "scanActiveProfiles";

    public static String PROFILE_SCANNING_DEFAULT = "true";

    public static List<Profile> getProfiles(MavenSessionHandler session, Model model) {
        final List<Profile> result = new ArrayList<>();
        final List<Profile> profiles = model.getProfiles();
        final boolean scanActiveProfiles = Boolean
                .parseBoolean(session.getUserProperties().getProperty(PROFILE_SCANNING, PROFILE_SCANNING_DEFAULT));

        if (profiles != null) {
            if (scanActiveProfiles) {
                for (Profile p : profiles) {
                    if (session.getActiveProfiles().contains(p.getId())) {
                        result.add(p);
                    }
                }
            } else {
                result.addAll(profiles);
            }
        }
        return result;
    }
}
