/*
 * Copyright © 2022 CUI-OpenSource-Software (info@cuioss.de)
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
import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger
class RecipeMarkerUtilTest {

    /**
     * Source whose {@code throw} statement starts at line 3, column 9 (8 spaces of indentation
     * precede the {@code throw} keyword). Explicit {@code \n} and spaces are used instead of a text
     * block so the derived line/column is unambiguous.
     */
    private static final String THROW_SOURCE =
        "class A {\n    void m() {\n        throw new RuntimeException();\n    }\n}\n";

    private final JavaParser parser = JavaParser.fromJavaVersion().build();

    @Test
    void shouldCreateTaskMessage() {
        String result = RecipeMarkerUtil.createTaskMessage("Use specific exception", "TestRecipe");

        assertEquals("TODO: Use specific exception. Suppress: // cui-rewrite:disable TestRecipe", result);
    }

    @Test
    void shouldDetectSearchResultMarker() {
        String source = """
            public class Test {
                void method() {
                    System.out.println("test");
                }
            }
            """;

        J.CompilationUnit cu = (J.CompilationUnit) parser.parse(source).findFirst().orElseThrow();

        AtomicBoolean foundWithMarker = new AtomicBoolean(false);
        AtomicBoolean foundWithoutMarker = new AtomicBoolean(false);

        new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if ("println".equals(method.getSimpleName())) {
                    // Test without marker
                    foundWithoutMarker.set(!RecipeMarkerUtil.hasSearchResultMarker(method));

                    // Add marker and test with marker
                    J.MethodInvocation withMarker = method.withMarkers(
                        method.getMarkers().addIfAbsent(new SearchResult(UUID.randomUUID(), "test"))
                    );
                    foundWithMarker.set(RecipeMarkerUtil.hasSearchResultMarker(withMarker));
                }
                return super.visitMethodInvocation(method, ctx);
            }
        }.visit(cu, null);

        assertTrue(foundWithoutMarker.get());
        assertTrue(foundWithMarker.get());
    }

    @Test
    void shouldScopeSearchResultMarkerToOwningRecipe() {
        // Arrange: an element carrying a marker whose description belongs to recipe A only.
        String source = """
            public class Test {
                void method() {
                    System.out.println("test");
                }
            }
            """;
        J.CompilationUnit cu = (J.CompilationUnit) parser.parse(source).findFirst().orElseThrow();
        String recipeAMessage = "TODO: RecipeA finding. Suppress: // cui-rewrite:disable RecipeA";
        String recipeBMessage = "TODO: RecipeB finding. Suppress: // cui-rewrite:disable RecipeB";

        AtomicBoolean matchesRecipeA = new AtomicBoolean(false);
        AtomicBoolean matchesRecipeB = new AtomicBoolean(true);

        // Act: scope the check to each recipe's own message.
        new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if ("println".equals(method.getSimpleName())) {
                    J.MethodInvocation withMarker = method.withMarkers(
                        method.getMarkers().addIfAbsent(new SearchResult(UUID.randomUUID(), recipeAMessage))
                    );
                    matchesRecipeA.set(RecipeMarkerUtil.hasSearchResultMarker(withMarker, recipeAMessage));
                    matchesRecipeB.set(RecipeMarkerUtil.hasSearchResultMarker(withMarker, recipeBMessage));
                }
                return super.visitMethodInvocation(method, ctx);
            }
        }.visit(cu, null);

        // Assert: recipe A's marker is its own pre-existing finding; recipe B must not see it.
        assertTrue(matchesRecipeA.get());
        assertFalse(matchesRecipeB.get());
    }

    @Test
    void shouldMatchSearchResultMarkerBySubstring() {
        // Arrange: a full task message embedding the recipe name; a scoped check by recipe name
        // (a substring of the description) must still match its own marker.
        String source = """
            public class Test {
                void method() {
                    System.out.println("test");
                }
            }
            """;
        J.CompilationUnit cu = (J.CompilationUnit) parser.parse(source).findFirst().orElseThrow();
        String recipeName = "CuiLoggerStandardsRecipe";
        String fullMessage = "TODO: Use CuiLogger. Suppress: // cui-rewrite:disable " + recipeName;

        AtomicBoolean matchesByRecipeName = new AtomicBoolean(false);
        AtomicBoolean matchesForeignRecipeName = new AtomicBoolean(true);

        // Act
        new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if ("println".equals(method.getSimpleName())) {
                    J.MethodInvocation withMarker = method.withMarkers(
                        method.getMarkers().addIfAbsent(new SearchResult(UUID.randomUUID(), fullMessage))
                    );
                    matchesByRecipeName.set(RecipeMarkerUtil.hasSearchResultMarker(withMarker, recipeName));
                    matchesForeignRecipeName.set(
                        RecipeMarkerUtil.hasSearchResultMarker(withMarker, "InvalidExceptionUsageRecipe"));
                }
                return super.visitMethodInvocation(method, ctx);
            }
        }.visit(cu, null);

        // Assert
        assertTrue(matchesByRecipeName.get());
        assertFalse(matchesForeignRecipeName.get());
    }

    @Test
    void shouldDetectTaskCommentInMethodInvocation() {
        String source = """
            public class Test {
                void method() {
                    /*~~(TODO: Test message)~~>*/System.out.println("test");
                }
            }
            """;

        J.CompilationUnit cu = (J.CompilationUnit) parser.parse(source).findFirst().orElseThrow();

        AtomicBoolean found = new AtomicBoolean(false);

        new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if ("println".equals(method.getSimpleName())) {
                    boolean hasTask = RecipeMarkerUtil.hasTaskComment(method, "TODO: Test message", getCursor());
                    found.set(hasTask);
                }
                return super.visitMethodInvocation(method, ctx);
            }
        }.visit(cu, null);

        assertTrue(found.get());
    }

    @Test
    void shouldDetectTaskCommentInCatchBlock() {
        String source = """
            public class Test {
                void method() {
                    try {
                        throw new Exception();
                    } /*~~(TODO: Catch specific exception)~~>*/ catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            """;

        J.CompilationUnit cu = (J.CompilationUnit) parser.parse(source).findFirst().orElseThrow();

        AtomicBoolean found = new AtomicBoolean(false);

        new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Try.Catch visitCatch(J.Try.Catch catchClause, ExecutionContext ctx) {
                boolean hasTask = RecipeMarkerUtil.hasTaskComment(catchClause, "TODO: Catch specific exception", getCursor());
                found.set(hasTask);
                return super.visitCatch(catchClause, ctx);
            }
        }.visit(cu, null);

        assertTrue(found.get());
    }

    @Test
    void shouldDetectTaskCommentInCatchBlockBody() {
        String source = """
            public class Test {
                void method() {
                    try {
                        throw new Exception();
                    } catch (Exception e) /*~~(TODO: Handle properly)~~>*/ {
                        e.printStackTrace();
                    }
                }
            }
            """;

        J.CompilationUnit cu = (J.CompilationUnit) parser.parse(source).findFirst().orElseThrow();

        AtomicBoolean found = new AtomicBoolean(false);

        new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Try.Catch visitCatch(J.Try.Catch catchClause, ExecutionContext ctx) {
                boolean hasTask = RecipeMarkerUtil.hasTaskComment(catchClause, "TODO: Handle properly", getCursor());
                found.set(hasTask);
                return super.visitCatch(catchClause, ctx);
            }
        }.visit(cu, null);

        assertTrue(found.get());
    }

    @Test
    void shouldDetectTaskCommentInThrowStatement() {
        String source = """
            public class Test {
                void method() {
                    /*~~(TODO: Throw specific exception)~~>*/throw new Exception();
                }
            }
            """;

        J.CompilationUnit cu = (J.CompilationUnit) parser.parse(source).findFirst().orElseThrow();

        AtomicBoolean found = new AtomicBoolean(false);

        new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Throw visitThrow(J.Throw thrown, ExecutionContext ctx) {
                boolean hasTask = RecipeMarkerUtil.hasTaskComment(thrown, "TODO: Throw specific exception", getCursor());
                found.set(hasTask);
                return super.visitThrow(thrown, ctx);
            }
        }.visit(cu, null);

        assertTrue(found.get());
    }

    @Test
    void shouldDetectTaskCommentInThrowStatementException() {
        String source = """
            public class Test {
                void method() {
                    throw /*~~(TODO: Use specific type)~~>*/new Exception();
                }
            }
            """;

        J.CompilationUnit cu = (J.CompilationUnit) parser.parse(source).findFirst().orElseThrow();

        AtomicBoolean found = new AtomicBoolean(false);

        new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Throw visitThrow(J.Throw thrown, ExecutionContext ctx) {
                boolean hasTask = RecipeMarkerUtil.hasTaskComment(thrown, "TODO: Use specific type", getCursor());
                found.set(hasTask);
                return super.visitThrow(thrown, ctx);
            }
        }.visit(cu, null);

        assertTrue(found.get());
    }

    @Test
    void shouldDetectTaskCommentInNewClass() {
        String source = """
            public class Test {
                void method() {
                    Exception e = /*~~(TODO: Use specific exception)~~>*/new Exception();
                }
            }
            """;

        J.CompilationUnit cu = (J.CompilationUnit) parser.parse(source).findFirst().orElseThrow();

        AtomicBoolean found = new AtomicBoolean(false);

        new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                if (newClass.getType() != null && newClass.getType().toString().contains("Exception")) {
                    boolean hasTask = RecipeMarkerUtil.hasTaskComment(newClass, "TODO: Use specific exception", getCursor());
                    found.set(hasTask);
                }
                return super.visitNewClass(newClass, ctx);
            }
        }.visit(cu, null);

        assertTrue(found.get());
    }

    @Test
    void shouldHandleThrowWithNonNewClassException() {
        String source = """
            public class Test {
                void method(Exception e) {
                    throw e;
                }
            }
            """;

        J.CompilationUnit cu = (J.CompilationUnit) parser.parse(source).findFirst().orElseThrow();

        AtomicBoolean tested = new AtomicBoolean(false);

        new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Throw visitThrow(J.Throw thrown, ExecutionContext ctx) {
                // This tests the path where exception is not a NewClass (line 106)
                boolean hasTask = RecipeMarkerUtil.hasTaskComment(thrown, "nonexistent", getCursor());
                assertFalse(hasTask);
                tested.set(true);
                return super.visitThrow(thrown, ctx);
            }
        }.visit(cu, null);

        assertTrue(tested.get());
    }

    @Test
    void shouldHandleDefaultCase() {
        String source = """
            public class Test {
                /*~~(TODO: Add javadoc)~~>*/void method() {
                }
            }
            """;

        J.CompilationUnit cu = (J.CompilationUnit) parser.parse(source).findFirst().orElseThrow();

        AtomicBoolean found = new AtomicBoolean(false);

        new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                // This tests the default case (line 86) - MethodDeclaration is not one of the special cases
                boolean hasTask = RecipeMarkerUtil.hasTaskComment(method, "TODO: Add javadoc", getCursor());
                found.set(hasTask);
                return super.visitMethodDeclaration(method, ctx);
            }
        }.visit(cu, null);

        assertTrue(found.get());
    }

    @Test
    void shouldNotFindNonexistentTaskComment() {
        String source = """
            public class Test {
                void method() {
                    System.out.println("test");
                }
            }
            """;

        J.CompilationUnit cu = (J.CompilationUnit) parser.parse(source).findFirst().orElseThrow();

        AtomicBoolean found = new AtomicBoolean(false);

        new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if ("println".equals(method.getSimpleName())) {
                    boolean hasTask = RecipeMarkerUtil.hasTaskComment(method, "nonexistent message", getCursor());
                    found.set(!hasTask);
                }
                return super.visitMethodInvocation(method, ctx);
            }
        }.visit(cu, null);

        assertTrue(found.get());
    }

    @Test
    void shouldLogNewlyDetectedFindingWithPositionAndContent() {
        J.CompilationUnit cu = (J.CompilationUnit) parser.parse(THROW_SOURCE).findFirst().orElseThrow();
        String taskMessage = "TODO: Throw specific not RuntimeException";

        new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Throw visitThrow(J.Throw thrown, ExecutionContext ctx) {
                RecipeMarkerUtil.logFinding(thrown, taskMessage, "TestRecipe", getCursor(), false);
                return super.visitThrow(thrown, ctx);
            }
        }.visit(cu, null);

        String expected = "Finding detected at " + cu.getSourcePath() + ":3:9 by TestRecipe: " + taskMessage;
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN, expected);
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, "Finding pre-existing");
    }

    @Test
    void shouldLogPreExistingFindingWithDistinguishableWording() {
        J.CompilationUnit cu = (J.CompilationUnit) parser.parse(THROW_SOURCE).findFirst().orElseThrow();
        String taskMessage = "TODO: Throw specific not RuntimeException";

        new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.Throw visitThrow(J.Throw thrown, ExecutionContext ctx) {
                RecipeMarkerUtil.logFinding(thrown, taskMessage, "TestRecipe", getCursor(), true);
                return super.visitThrow(thrown, ctx);
            }
        }.visit(cu, null);

        String expected = "Finding pre-existing at " + cu.getSourcePath() + ":3:9 by TestRecipe: " + taskMessage;
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN, expected);
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, "Finding detected");
    }
}
