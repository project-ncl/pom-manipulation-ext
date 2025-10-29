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
package org.commonjava.maven.ext.common.json;

/*
 * Created by JacksonGenerator on 23/07/2019.
 */

import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectVersionRef;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GAV {
    /**
     * A string representing the Maven groupId
     */
    private String groupId;
    /**
     * A string representing the Maven artifactId
     */
    private String artifactId;
    /**
     * A string representing the Maven version
     */
    private String version;
    /**
     * A string representing the original groupId:artifactId:version
     */
    private String originalGAV;

    /**
     * Convenience function to update the contents of this object using a {@link ProjectVersionRef}.
     * 
     * @param p a ProjectVersionRef
     */
    @JsonIgnore
    public void setPVR(ProjectVersionRef p) {
        setGroupId(p.getGroupId());
        setArtifactId(p.getArtifactId());
        setVersion(p.getVersionString());
    }

    /**
     * Convenience function to convert this object to a {@link ProjectVersionRef}.
     * 
     * @return a ProjectVersionRef
     */
    @JsonIgnore
    public ProjectVersionRef getPVR() {
        return new SimpleProjectVersionRef(groupId, artifactId, version);
    }
}
