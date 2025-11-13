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
package org.jboss.pnc.mavenmanipulator.annotation;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;

@SupportedAnnotationTypes("org.jboss.pnc.mavenmanipulator.annotation.ConfigValue")
@SupportedOptions({ "generationDirectory", "packageName", "rootDirectory" })
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class ConfigValueProcessor extends AbstractProcessor {
    private static final String GENERATION_DIR = "generationDirectory";
    private static final String PACKAGE_NAME = "packageName";
    private static final String ROOT_DIR = "rootDirectory";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Map<String, Boolean> varResults = new TreeMap<>();
    private final Map<String, String> indexResults = new TreeMap<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Messager messager = processingEnv.getMessager();

        if (annotations.size() == 0) {
            return false;
        } else if (processingEnv.getOptions().size() == 0) {
            messager.printMessage(
                    Diagnostic.Kind.OTHER,
                    "No options supplied ; use maven-annotation-plugin to pass options through");
            return false;
        }

        for (TypeElement typeElement : annotations) {
            Set<VariableElement> vElements = ElementFilter.fieldsIn(roundEnv.getElementsAnnotatedWith(typeElement));

            for (VariableElement vElement : vElements) {
                ConfigValue annotation = vElement.getAnnotation(ConfigValue.class);

                messager.printMessage(
                        Diagnostic.Kind.NOTE,
                        "Found " + vElement.toString() + " & " + annotation.docIndex() + " & "
                                + vElement.getConstantValue());

                varResults.put(vElement.getConstantValue().toString(), annotation.deprecated());
                indexResults.put(vElement.getConstantValue().toString(), annotation.docIndex());
            }
        }

        if (!varResults.isEmpty()) {
            try {
                generateCode();
            } catch (IOException e) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Unable to write file: " + e.toString());
                throw new RuntimeException("Unable to write file", e);
            }
        }

        return true;
    }

    void generateCode() throws IOException {
        logger.info("Generating code and index");

        ClassName hashMap = ClassName.get("java.util", "HashMap");
        ParameterizedTypeName mainType = ParameterizedTypeName.get(
                hashMap,
                ClassName.get(String.class),
                ClassName.get(Boolean.class));

        List<CodeBlock> contents = new ArrayList<>();
        varResults.forEach(
                (key, value) -> contents
                        .add(CodeBlock.builder().addStatement("allConfigValues.put($S, $L)", key, value).build()));

        TypeSpec ConfigList = TypeSpec.classBuilder("ConfigList")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addField(
                        FieldSpec.builder(mainType, "allConfigValues", Modifier.STATIC, Modifier.FINAL, Modifier.PUBLIC)
                                .initializer("new HashMap<>(" + indexResults.size() + ')')
                                .build())
                .addStaticBlock(CodeBlock.join(contents, " "))
                .build();

        JavaFile javaFile = JavaFile.builder(processingEnv.getOptions().get(PACKAGE_NAME), ConfigList).build();
        javaFile.writeTo(new File(processingEnv.getOptions().get(GENERATION_DIR)));

        StringBuilder propertyIndex = new StringBuilder();

        propertyIndex.append("---\ntitle: \"Index of Properties\"\n---\n\n");
        indexResults.forEach(
                (key, value) -> propertyIndex
                        .append("  * [")
                        .append(key)
                        .append("](")
                        .append(value)
                        .append(")")
                        .append(varResults.get(key) ? "\t(deprecated)" : "")
                        .append("\n"));
        FileUtils.writeStringToFile(
                new File(
                        processingEnv.getOptions().get(ROOT_DIR) +
                                File.separator + "target" + File.separator + "property-index.md"),
                propertyIndex.toString(),
                StandardCharsets.UTF_8);
    }
}
