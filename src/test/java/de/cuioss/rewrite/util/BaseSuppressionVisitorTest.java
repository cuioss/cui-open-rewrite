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
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.test.RewriteTest;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("java:S2699") // OpenRewrite tests use implicit assertions via the RewriteTest framework
class BaseSuppressionVisitorTest implements RewriteTest {

    @Test void classLevelSuppressionSkipsEntireClass() {
        rewriteRun(
            spec -> spec.recipe(new TestRecipe()),
            java(
                """
                // cui-rewrite:disable TestRecipe
                public class Example {
                    public void method() {
                        System.out.println("This should not be marked");
                    }
                }
                """
            )
        );
    }

    @Test void methodLevelSuppressionSkipsMethod() {
        rewriteRun(
            spec -> spec.recipe(new TestRecipe()),
            java(
                """
                public class Example {
                    // cui-rewrite:disable TestRecipe
                    public void suppressedMethod() {
                        System.out.println("This should not be marked");
                    }

                    public void normalMethod() {
                        System.out.println("This should be marked");
                    }
                }
                """,
                """
                public class Example {
                    // cui-rewrite:disable TestRecipe
                    public void suppressedMethod() {
                        System.out.println("This should not be marked");
                    }

                    public void normalMethod() {
                        /*~~(TEST: Found println)~~>*/System.out.println("This should be marked");
                    }
                }
                """
            )
        );
    }

    @Test void elementLevelSuppressionWorksWhenHelperMethodUsed() {
        rewriteRun(
            spec -> spec.recipe(new TestRecipe()),
            java(
                """
                public class Example {
                    public void method() {
                        // cui-rewrite:disable TestRecipe
                        System.out.println("This should not be marked");
                        System.out.println("This should be marked");
                    }
                }
                """,
                """
                public class Example {
                    public void method() {
                        // cui-rewrite:disable TestRecipe
                        System.out.println("This should not be marked");
                        /*~~(TEST: Found println)~~>*/System.out.println("This should be marked");
                    }
                }
                """
            )
        );
    }

    @Test void helperMethodWorksCorrectly() {
        rewriteRun(
            spec -> spec.recipe(new TestRecipe()),
            java(
                """
                public class Example {
                    public void method() {
                        System.out.println("This should be marked");
                    }
                }
                """,
                """
                public class Example {
                    public void method() {
                        /*~~(TEST: Found println)~~>*/System.out.println("This should be marked");
                    }
                }
                """
            )
        );
    }

    /**
     * Test recipe that extends BaseSuppressionVisitor to verify its behavior
     */
    public static class TestRecipe extends Recipe {
        static final String RECIPE_NAME = "TestRecipe";

        @Override public String getDisplayName() {
            return "Test Recipe for BaseSuppressionVisitor";
        }

        @Override public String getDescription() {
            return "Marks System.out.println calls for testing suppression behavior.";
        }

        @Override public Set<String> getTags() {
            return Set.of("test");
        }

        @Override public Duration getEstimatedEffortPerOccurrence() {
            return Duration.ofMinutes(1);
        }

        @Override public TreeVisitor<?, ExecutionContext> getVisitor() {
            return new TestVisitor();
        }

        @Override public List<Recipe> getRecipeList() {
            return List.of();
        }

        private static class TestVisitor extends BaseSuppressionVisitor {

            public TestVisitor() {
                super(RECIPE_NAME);
            }

            @Override public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

                // Check element-level suppression using the convenient helper
                if (isSuppressed()) {
                    return mi;
                }

                // Mark System.out.println calls
                if (isSystemOutPrintln(mi)) {
                    return mi.withMarkers(mi.getMarkers().addIfAbsent(
                        new SearchResult(UUID.randomUUID(), "TEST: Found println")));
                }

                return mi;
            }

            private boolean isSystemOutPrintln(J.MethodInvocation mi) {
                if (mi.getSelect() instanceof J.FieldAccess fieldAccess
                    && fieldAccess.getTarget() instanceof J.Identifier target) {
                    return "System".equals(target.getSimpleName()) &&
                        "out".equals(fieldAccess.getSimpleName()) &&
                        "println".equals(mi.getSimpleName());
                }
                return false;
            }
        }
    }
}