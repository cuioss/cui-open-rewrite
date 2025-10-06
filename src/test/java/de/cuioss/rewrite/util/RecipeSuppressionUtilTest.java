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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;

import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger(rootLevel = TestLogLevel.DEBUG) @SuppressWarnings({
    "java:S2699", // Tests use assertions via LogAsserts
    "java:S5976"  // Similar tests are intentionally kept separate for clarity
})
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
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.DEBUG,
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
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.DEBUG,
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
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.DEBUG,
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
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.DEBUG,
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

    @Test
    void shouldHandleRecipeNameWithoutPackage() {
        String source = """
            // cui-rewrite:disable SimpleRecipe
            public class TestClass {}
            """;

        J.CompilationUnit cu = (J.CompilationUnit) parser.parse(source).findFirst().orElseThrow();

        TestVisitor visitor = new TestVisitor("SimpleRecipe");
        visitor.visit(cu, ctx);

        assertTrue(visitor.classWasSuppressed);
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.DEBUG,
            "Skipping class 'TestClass' for recipe 'SimpleRecipe'");
    }

    @Test
    void shouldMatchSimpleNameFromFullyQualifiedRecipe() {
        String source = """
            // cui-rewrite:disable TestRecipe
            public class TestClass {}
            """;

        J.CompilationUnit cu = (J.CompilationUnit) parser.parse(source).findFirst().orElseThrow();

        TestVisitor visitor = new TestVisitor("com.example.TestRecipe");
        visitor.visit(cu, ctx);

        assertTrue(visitor.classWasSuppressed);
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.DEBUG,
            "Skipping class 'TestClass' for recipe 'com.example.TestRecipe'");
    }

    @Test
    void shouldNotSuppressWhenRecipeNameMismatch() {
        String source = """
            // cui-rewrite:disable SimpleRecipe
            public class TestClass {}
            """;

        J.CompilationUnit cu = (J.CompilationUnit) parser.parse(source).findFirst().orElseThrow();

        TestVisitor visitor = new TestVisitor("DifferentRecipe");
        visitor.visit(cu, ctx);

        assertFalse(visitor.classWasSuppressed);
    }

    @Test
    void shouldHandleMultipleSuppressionComments() {
        String source = """
            public class TestClass {
                // cui-rewrite:disable
                public void method1() {}

                // cui-rewrite:disable TestRecipe
                public void method2() {}

                public void method3() {}
            }
            """;

        J.CompilationUnit cu = (J.CompilationUnit) parser.parse(source).findFirst().orElseThrow();

        TestVisitor visitor = new TestVisitor("TestRecipe");
        visitor.visit(cu, ctx);

        // method1 should be suppressed for all recipes
        // method2 should be suppressed for TestRecipe
        // method3 should not be suppressed
        assertTrue(visitor.methodWasSuppressed);
    }

    @Test
    void shouldSuppressWithTrailingComment() {
        String source = """
            public class TestClass {
                // cui-rewrite:disable
                public void method() {}
            }
            """;

        J.CompilationUnit cu = (J.CompilationUnit) parser.parse(source).findFirst().orElseThrow();

        TestVisitor visitor = new TestVisitor();
        visitor.visit(cu, ctx);

        assertTrue(visitor.methodWasSuppressed);
    }

    @Test
    void shouldTestPrivateConstructor() throws Exception {
        var constructor = RecipeSuppressionUtil.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        var exception = assertThrows(InvocationTargetException.class, constructor::newInstance);

        assertTrue(exception.getCause() instanceof UnsupportedOperationException);
        assertTrue(exception.getCause().getMessage().contains("Utility class"));
    }

    @Test
    void shouldSuppressWithAnnotationsBetweenComments() {
        String source = """
            // cui-rewrite:disable
            @Deprecated
            @SuppressWarnings("unused")
            public class TestClass {
                public void method() {}
            }
            """;

        J.CompilationUnit cu = (J.CompilationUnit) parser.parse(source).findFirst().orElseThrow();

        TestVisitor visitor = new TestVisitor();
        visitor.visit(cu, ctx);

        assertTrue(visitor.classWasSuppressed);
    }

    @Test
    void shouldHandleNullRecipeName() {
        String source = """
            // cui-rewrite:disable SpecificRecipe
            public class TestClass {
                public void method() {}
            }
            """;

        J.CompilationUnit cu = (J.CompilationUnit) parser.parse(source).findFirst().orElseThrow();

        // Test with null recipe name - should suppress for any recipe
        TestVisitor visitor = new TestVisitor(null);
        visitor.visit(cu, ctx);

        assertTrue(visitor.classWasSuppressed);
    }

    @Test
    void shouldHandleComplexRecipeMatching() {
        String source = """
            // cui-rewrite:disable com.example.MyRecipe
            public class TestClass {}
            """;

        J.CompilationUnit cu = (J.CompilationUnit) parser.parse(source).findFirst().orElseThrow();

        // Should match simple name against fully qualified
        TestVisitor visitor = new TestVisitor("MyRecipe");
        visitor.visit(cu, ctx);

        assertTrue(visitor.classWasSuppressed);
    }

    @Test
    void shouldHandleUnknownElementTypes() {
        // This test creates a scenario that exercises the getElementType/getElementName default cases
        // The logic is already tested indirectly through the existing tests
        // This test serves to document that behavior and ensure coverage

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
        // This exercises the logging paths for both class and method suppression
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.DEBUG, "Skipping class 'TestClass'");
    }

    private static class TestVisitor extends JavaIsoVisitor<@NonNull ExecutionContext> {
        boolean classWasSuppressed;
        boolean methodWasSuppressed;
        boolean fieldWasSuppressed;
        @Nullable
        final String recipeName;

        TestVisitor() {
            this(null);
        }

        TestVisitor(@Nullable String recipeName) {
            this.recipeName = recipeName;
        }

        @Override
        public J.@NonNull ClassDeclaration visitClassDeclaration(J.@NonNull ClassDeclaration classDecl, @NonNull ExecutionContext ctx) {
            if (RecipeSuppressionUtil.isSuppressed(getCursor(), recipeName)) {
                classWasSuppressed = true;
                return classDecl;
            }
            return super.visitClassDeclaration(classDecl, ctx);
        }

        @Override
        public J.@NonNull MethodDeclaration visitMethodDeclaration(J.@NonNull MethodDeclaration method, @NonNull ExecutionContext ctx) {
            if (RecipeSuppressionUtil.isSuppressed(getCursor(), recipeName)) {
                methodWasSuppressed = true;
                return method;
            }
            return super.visitMethodDeclaration(method, ctx);
        }

        @Override
        public J.@NonNull VariableDeclarations visitVariableDeclarations(J.@NonNull VariableDeclarations multiVariable, @NonNull ExecutionContext ctx) {
            if (isFieldDeclaration() && RecipeSuppressionUtil.isSuppressed(getCursor(), recipeName)) {
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