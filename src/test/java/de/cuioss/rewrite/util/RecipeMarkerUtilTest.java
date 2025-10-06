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

import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeMarkerUtilTest {

    private final JavaParser parser = JavaParser.fromJavaVersion().build();

    @Test
    void shouldCreateTaskMessage() {
        String result = RecipeMarkerUtil.createTaskMessage("Use specific exception", "TestRecipe");

        assertThat(result).isEqualTo("TODO: Use specific exception. Suppress: // cui-rewrite:disable TestRecipe");
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
}
