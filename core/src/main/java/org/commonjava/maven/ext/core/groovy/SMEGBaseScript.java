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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.codehaus.groovy.transform.GroovyASTTransformationClass;
import org.jboss.pnc.mavenmanipulator.core.groovy.GradleBaseScript;

/**
 * Custom annotation that replicates {@link groovy.transform.BaseScript} but also handles extra annotations.
 */
@SuppressWarnings("unused")
@Retention(RetentionPolicy.RUNTIME)
@Target({
        ElementType.FIELD,
        ElementType.ANNOTATION_TYPE,
        ElementType.LOCAL_VARIABLE,
        ElementType.PACKAGE,
        ElementType.TYPE })
@GroovyASTTransformationClass("org.jboss.pnc.mavenmanipulator.core.groovy.ASTTransformer")

public @interface SMEGBaseScript {
    Class value() default GradleBaseScript.class;
}
