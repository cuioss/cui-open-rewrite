/*
 * Copyright © 2025 CUI-OpenSource-Software (info@cuioss.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cuioss.rewrite.util;

import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnableTestLogger @SuppressWarnings("java:S2699") // Tests use assertions via LogAsserts
class RecipeSuppressionUtilTest {

    private final JavaParser parser = JavaParser.fromJavaVersion().build();
    private final ExecutionContext ctx = new InMemoryExecutionContext();

    @Test
    void shouldDetectClassSuppressionWithGeneralComment() {
        String source = """
            // cui-rewrite:disable
            public class TestClass {
                public void method() {}
            }
            """;

        J.CompilationUnit cu = (J.CompilationUnit) parser.parse(source).findFirst().orElseThrow();

        TestVisitor visitor = new TestVisitor();
        visitor.visit(cu, ctx);

        assertTrue(visitor.classWasSuppressed);
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
            "Skipping class 'TestClass' due to cui-rewrite:disable comment");
    }

    @Test
    void shouldDetectMethodSuppressionWithGeneralComment() {
        String source = """
            public class TestClass {
                // cui-rewrite:disable
                public void testMethod() {}
            }
            """;

        J.CompilationUnit cu = (J.CompilationUnit) parser.parse(source).findFirst().orElseThrow();

        TestVisitor visitor = new TestVisitor();
        visitor.visit(cu, ctx);

        assertTrue(visitor.methodWasSuppressed);
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
            "Skipping method 'testMethod' due to cui-rewrite:disable comment");
    }

    @Test
    void shouldNotSuppressWithoutComment() {
        String source = """
            public class TestClass {
                public void method() {}
                private String field;
            }
            """;

        J.CompilationUnit cu = (J.CompilationUnit) parser.parse(source).findFirst().orElseThrow();

        TestVisitor visitor = new TestVisitor();
        visitor.visit(cu, ctx);

        assertFalse(visitor.classWasSuppressed);
        assertFalse(visitor.methodWasSuppressed);
        assertFalse(visitor.fieldWasSuppressed);
    }

    @Test
    void shouldSuppressSpecificRecipeBySimpleName() {
        String source = """
            // cui-rewrite:disable TestRecipe
            public class TestClass {}
            """;

        J.CompilationUnit cu = (J.CompilationUnit) parser.parse(source).findFirst().orElseThrow();

        TestVisitor visitor = new TestVisitor("TestRecipe");
        visitor.visit(cu, ctx);

        assertTrue(visitor.classWasSuppressed);
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
            "Skipping class 'TestClass' for recipe 'TestRecipe'");
    }

    @Test
    void shouldSuppressSpecificRecipeByFullyQualifiedName() {
        String source = """
            // cui-rewrite:disable de.cuioss.rewrite.TestRecipe
            public class TestClass {}
            """;

        J.CompilationUnit cu = (J.CompilationUnit) parser.parse(source).findFirst().orElseThrow();

        TestVisitor visitor = new TestVisitor("de.cuioss.rewrite.TestRecipe");
        visitor.visit(cu, ctx);

        assertTrue(visitor.classWasSuppressed);
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
            "Skipping class 'TestClass' for recipe 'de.cuioss.rewrite.TestRecipe'");
    }

    @Test
    void shouldNotSuppressDifferentRecipe() {
        String source = """
            // cui-rewrite:disable OtherRecipe
            public class TestClass {}
            """;

        J.CompilationUnit cu = (J.CompilationUnit) parser.parse(source).findFirst().orElseThrow();

        TestVisitor visitor = new TestVisitor("TestRecipe");
        visitor.visit(cu, ctx);

        assertFalse(visitor.classWasSuppressed);
    }

    @Test
    void shouldSuppressWithAnnotationPrefix() {
        String source = """
            // cui-rewrite:disable
            @Deprecated
            public class TestClass {}
            """;

        J.CompilationUnit cu = (J.CompilationUnit) parser.parse(source).findFirst().orElseThrow();

        TestVisitor visitor = new TestVisitor();
        visitor.visit(cu, ctx);

        assertTrue(visitor.classWasSuppressed);
    }

    private static class TestVisitor extends JavaIsoVisitor<ExecutionContext> {
        boolean classWasSuppressed;
        boolean methodWasSuppressed;
        boolean fieldWasSuppressed;
        @Nullable final String recipeName;

        TestVisitor() {
            this(null);
        }

        TestVisitor(@Nullable String recipeName) {
            this.recipeName = recipeName;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            if (RecipeSuppressionUtil.isSuppressed(classDecl, getCursor(), recipeName)) {
                classWasSuppressed = true;
                return classDecl;
            }
            return super.visitClassDeclaration(classDecl, ctx);
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            if (RecipeSuppressionUtil.isSuppressed(method, getCursor(), recipeName)) {
                methodWasSuppressed = true;
                return method;
            }
            return super.visitMethodDeclaration(method, ctx);
        }

        @Override
        public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
            if (isFieldDeclaration() && RecipeSuppressionUtil.isSuppressed(multiVariable, getCursor(), recipeName)) {
                fieldWasSuppressed = true;
                return multiVariable;
            }
            return super.visitVariableDeclarations(multiVariable, ctx);
        }

        private boolean isFieldDeclaration() {
            Object parent = getCursor().getParentOrThrow().getValue();
            if (parent instanceof J.Block) {
                Object grandParent = getCursor().getParentOrThrow().getParentOrThrow().getValue();
                return grandParent instanceof J.ClassDeclaration;
            }
            return parent instanceof J.ClassDeclaration;
        }
    }
}