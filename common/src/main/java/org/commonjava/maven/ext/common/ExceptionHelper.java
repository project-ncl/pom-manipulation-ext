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
 * Copyright (c) 2004-2011 QOS.ch
 * All rights reserved.
 *
 * Permission is hereby granted, free  of charge, to any person obtaining
 * a  copy  of this  software  and  associated  documentation files  (the
 * "Software"), to  deal in  the Software without  restriction, including
 * without limitation  the rights to  use, copy, modify,  merge, publish,
 * distribute,  sublicense, and/or sell  copies of  the Software,  and to
 * permit persons to whom the Software  is furnished to do so, subject to
 * the following conditions:
 *
 * The  above  copyright  notice  and  this permission  notice  shall  be
 * included in all copies or substantial portions of the Software.
 *
 * THE  SOFTWARE IS  PROVIDED  "AS  IS", WITHOUT  WARRANTY  OF ANY  KIND,
 * EXPRESS OR  IMPLIED, INCLUDING  BUT NOT LIMITED  TO THE  WARRANTIES OF
 * MERCHANTABILITY,    FITNESS    FOR    A   PARTICULAR    PURPOSE    AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE,  ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */
package org.commonjava.maven.ext.common;

/**
 * This class should not really be required but when the tooling is embedded in Maven as an extension
 * or inside Gradle then both of those have earlier versions of SLF4J which causes problems without shading
 * and relocating the classes. Therefore the affected method has been copied from the SLF4J source below.
 *
 * The {@link #getThrowableCandidate}
 * was only made public in {@link org.slf4j.helpers.MessageFormatter} version 1.7.29 and later.
 * <br>
 * <br>
 * See the <a href=
 * "https://github.com/qos-ch/slf4j/commit/a7562cb4b928c225192405b4d849246db60107d4#diff-f99407dbe36d6d3e23534b6e5701b231">SLF4J
 * source</a>
 */
public final class ExceptionHelper {
    private ExceptionHelper() {
    }

    /**
     * Helper method to determine if an {@link Object} array contains a {@link Throwable} as last element
     *
     * @param argArray
     *        The arguments off which we want to know if it contains a {@link Throwable} as last element
     * @return if the last {@link Object} in argArray is a {@link Throwable} this method will return it,
     *         otherwise it returns null
     */
    public static Throwable getThrowableCandidate(final Object[] argArray) {
        if (argArray == null || argArray.length == 0) {
            return null;
        }

        final Object lastEntry = argArray[argArray.length - 1];
        if (lastEntry instanceof Throwable) {
            return (Throwable) lastEntry;
        }

        return null;
    }
}
