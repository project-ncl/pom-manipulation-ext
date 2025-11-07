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

package org.jboss.pnc.maven_manipulator.core.groovy;

import static org.apache.groovy.ast.tools.ClassNodeUtils.addGeneratedMethod;

import java.util.List;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ConstructorNode;
import org.codehaus.groovy.ast.ImportNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.PackageNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.DeclarationExpression;
import org.codehaus.groovy.ast.expr.EmptyExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.AbstractASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;
import org.jboss.pnc.maven_manipulator.common.ManipulationUncheckedException;

/**
 * Ensures that Groovy scripts annotated with {@link GMEBaseScript} and {@link PMEBaseScript} are transformed into a
 * class that
 * extends {@link BaseScript}.
 * This class performs the same transformations as {@link org.codehaus.groovy.transform.BaseScriptASTTransformation},
 * and in addition moves the custom annotations to the generated script class.
 *
 * This uses code from
 * <a href=
 * "https://github.com/groovy/groovy-core/blob/master/src/main/org/codehaus/groovy/transform/BaseScriptASTTransformation.java">BaseScriptASTTransformation</a>
 * and
 * <a href=
 * "https://github.com/remkop/picocli/blob/master/picocli-groovy/src/main/java/picocli/groovy/PicocliScriptASTTransformation.java">PicocliScriptASTTransformation</a>.
 */
@SuppressWarnings("unused")
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
public class ASTTransformer extends AbstractASTTransformation {

    private static final ClassNode MAVEN_TYPE = ClassHelper.make(PMEBaseScript.class);
    private static final ClassNode GRADLE_TYPE = ClassHelper.make(GMEBaseScript.class);
    private static final ClassNode SBT_TYPE = ClassHelper.make(SMEGBaseScript.class);
    private static final ClassNode COMMAND_TYPE = ClassHelper.make(InvocationPoint.class);
    private static final ClassNode MAVEN_BASE_SCRIPT_TYPE = ClassHelper.make(BaseScript.class);
    private static final String MAVEN_TYPE_NAME = "@" + MAVEN_TYPE.getNameWithoutPackage();
    private static final ClassNode GRADLE_BASE_SCRIPT_TYPE = ClassHelper.make(GradleBaseScript.class);
    private static final ClassNode SBT_BASE_SCRIPT_TYPE = ClassHelper.make(SBTBaseScript.class);
    private static final String GRADLE_TYPE_NAME = "@" + GRADLE_TYPE.getNameWithoutPackage();
    private static final String SBT_TYPE_NAME = "@" + SBT_TYPE.getNameWithoutPackage();

    enum Type {
        GRADLE, MAVEN, SBT
    }

    private Type type;

    private static final Parameter[] CONTEXT_CTOR_PARAMETERS = { new Parameter(ClassHelper.BINDING_TYPE, "context") };

    public void visit(ASTNode[] nodes, SourceUnit source) {
        init(nodes, source);
        AnnotatedNode parent = (AnnotatedNode) nodes[1];
        AnnotationNode node = (AnnotationNode) nodes[0];
        if (MAVEN_TYPE.equals(node.getClassNode())
                || GRADLE_TYPE.equals(node.getClassNode())
                || SBT_TYPE.equals(node.getClassNode())) {
            if (MAVEN_TYPE.equals(node.getClassNode())) {
                type = Type.MAVEN;
            } else if (GRADLE_TYPE.equals(node.getClassNode())) {
                type = Type.GRADLE;
            } else if (SBT_TYPE.equals(node.getClassNode())) {
                type = Type.SBT;
            }

            if (parent instanceof DeclarationExpression) {
                changeBaseScriptTypeFromDeclaration((DeclarationExpression) parent, node);
            } else if (parent instanceof ImportNode || parent instanceof PackageNode) {
                changeBaseScriptTypeFromPackageOrImport(source, parent, node);
            } else if (parent instanceof ClassNode) {
                changeBaseScriptTypeFromClass((ClassNode) parent);
            }
        }
    }

    private String getType() {
        switch (type) {
            case MAVEN:
                return MAVEN_TYPE_NAME;
            case GRADLE:
                return GRADLE_TYPE_NAME;
            case SBT:
                return SBT_TYPE_NAME;
            default:
                throw new ManipulationUncheckedException("Unknown type " + type);
        }
    }

    private ClassNode getType(Type type) {
        switch (type) {
            case MAVEN:
                return MAVEN_BASE_SCRIPT_TYPE;
            case GRADLE:
                return GRADLE_BASE_SCRIPT_TYPE;
            case SBT:
                return SBT_BASE_SCRIPT_TYPE;
            default:
                throw new ManipulationUncheckedException("Unknown type " + type);
        }
    }

    private void changeBaseScriptTypeFromPackageOrImport(
            final SourceUnit source,
            final AnnotatedNode parent,
            final AnnotationNode node) {
        Expression value = node.getMember("value");
        ClassNode scriptType;

        if (value == null) {
            scriptType = getType(type);
        } else {
            if (!(value instanceof ClassExpression)) {
                addError("Annotation " + getType() + " member 'value' should be a class literal.", value);
                return;
            }
            scriptType = value.getType();
        }
        List<ClassNode> classes = source.getAST().getClasses();
        for (ClassNode classNode : classes) {
            if (classNode.isScriptBody()) {
                changeBaseScriptType(parent, classNode, scriptType);
            }
        }
    }

    private void changeBaseScriptTypeFromClass(final ClassNode parent) {
        changeBaseScriptType(parent, parent, parent.getSuperClass());
    }

    private void changeBaseScriptTypeFromDeclaration(final DeclarationExpression de, final AnnotationNode node) {
        if (de.isMultipleAssignmentDeclaration()) {
            addError("Annotation " + getType() + " not supported with multiple assignment notation.", de);
            return;
        }

        if (!(de.getRightExpression() instanceof EmptyExpression)) {
            addError("Annotation " + getType() + " not supported with variable assignment.", de);
            return;
        }
        Expression value = node.getMember("value");
        if (value != null) {
            addError("Annotation " + getType() + " cannot have member 'value' if used on a declaration.", value);
            return;
        }

        ClassNode cNode = de.getDeclaringClass();
        ClassNode baseScriptType = de.getVariableExpression().getType().getPlainNodeReference();
        if (baseScriptType.isScript()) {
            if (!(de.getRightExpression() instanceof EmptyExpression)) {
                addError("Annotation " + getType() + " not supported with variable assignment.", de);
                return;
            }
            de.setRightExpression(new VariableExpression("this"));
        } else {
            baseScriptType = getType(type);
        }

        changeBaseScriptType(de, cNode, baseScriptType);
    }

    private void changeBaseScriptType(
            final AnnotatedNode parent,
            final ClassNode cNode,
            final ClassNode baseScriptType) {
        if (!cNode.isScriptBody()) {
            addError("Annotation " + getType() + " can only be used within a Script.", parent);
            return;
        }

        if (!baseScriptType.isScript()) {
            addError("Declared type " + baseScriptType + " does not extend groovy.lang.Script class!", parent);
            return;
        }

        List<AnnotationNode> annotations = parent.getAnnotations(COMMAND_TYPE);
        if (cNode.getAnnotations(COMMAND_TYPE).isEmpty()) { // #388 prevent "Duplicate annotation for class" AnnotationFormatError

            cNode.addAnnotations(annotations);
        }

        cNode.setSuperClass(baseScriptType);

        // Method in base script that will contain the script body code.
        MethodNode runScriptMethod = ClassHelper.findSAM(baseScriptType);

        // If they want to use a name other than than "run", then make the change.
        if (isCustomScriptBodyMethod(runScriptMethod)) {
            MethodNode defaultMethod = cNode.getDeclaredMethod("run", Parameter.EMPTY_ARRAY);
            // GROOVY-6706: Sometimes an NPE is thrown here.
            // The reason is that our transform is getting called more than once sometimes.
            if (defaultMethod != null) {
                cNode.removeMethod(defaultMethod);
                MethodNode methodNode = new MethodNode(
                        runScriptMethod.getName(),
                        runScriptMethod.getModifiers() & ~ACC_ABSTRACT,
                        runScriptMethod.getReturnType(),
                        runScriptMethod.getParameters(),
                        runScriptMethod.getExceptions(),
                        defaultMethod.getCode());
                // The AST node metadata has the flag that indicates that this method is a script body.
                // It may also be carrying data for other AST transforms.
                methodNode.copyNodeMetaData(defaultMethod);
                addGeneratedMethod(cNode, methodNode);
            }
        }

        // If the new script base class does not have a contextual constructor (g.l.Binding), then we won't either.
        // We have to do things this way (and rely on just default constructors) because the logic that generates
        // the constructors for our script class have already run.
        if (cNode.getSuperClass().getDeclaredConstructor(CONTEXT_CTOR_PARAMETERS) == null) {
            ConstructorNode orphanedConstructor = cNode.getDeclaredConstructor(CONTEXT_CTOR_PARAMETERS);
            cNode.removeConstructor(orphanedConstructor);
        }
    }

    private static boolean isCustomScriptBodyMethod(MethodNode node) {
        return node != null
                && !(node.getDeclaringClass().equals(ClassHelper.SCRIPT_TYPE)
                        && "run".equals(node.getName())
                        && node.getParameters().length == 0);
    }
}
