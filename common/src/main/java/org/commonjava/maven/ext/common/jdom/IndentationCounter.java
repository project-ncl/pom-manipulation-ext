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
/*
 * Copyright (C) 2012 Apache Software Foundation
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
package org.commonjava.maven.ext.common.jdom;

/**
 * Class Counter.
 *
 * @version $Revision$ $Date$
 */
class IndentationCounter {

    // --------------------------/
    // - Class/Member Variables -/
    // --------------------------/

    /**
     * Field currentIndex.
     */
    private int currentIndex = 0;

    /**
     * Field level.
     */
    private final int level;

    // ----------------/
    // - Constructors -/
    // ----------------/

    public IndentationCounter(final int depthLevel) {
        level = depthLevel;
    } // -- org.apache.maven.model.io.jdom.Counter(int)

    // -----------/
    // - Methods -/
    // -----------/

    /**
     * Method getCurrentIndex.
     *
     * @return int
     */
    public int getCurrentIndex() {
        return currentIndex;
    } // -- int getCurrentIndex()

    /**
     * Method getDepth.
     *
     * @return int
     */
    public int getDepth() {
        return level;
    } // -- int getDepth()

    /**
     * Method increaseCount.
     */
    public void increaseCount() {
        currentIndex = currentIndex + 1;
    } // -- void increaseCount()

}
