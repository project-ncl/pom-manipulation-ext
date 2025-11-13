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
package org.jboss.pnc.mavenmanipulator.common;

import org.slf4j.helpers.MessageFormatter;

public class ManipulationUncheckedException
        extends RuntimeException {
    private static final long serialVersionUID = 1L;

    protected Object[] params;

    private String formattedMessage;

    public ManipulationUncheckedException(final String string, final Object... params) {
        super(string, ExceptionHelper.getThrowableCandidate(params));
        this.params = params;
    }

    public ManipulationUncheckedException(final Throwable cause) {
        super(cause);
    }

    @Override
    public synchronized String getMessage() {
        if (formattedMessage == null) {
            formattedMessage = MessageFormatter.arrayFormat(super.getMessage(), params).getMessage();
        }
        return formattedMessage;
    }
}
