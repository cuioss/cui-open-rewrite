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
package de.cuioss.rewrite.logging;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("java:S2699") // OpenRewrite tests use implicit assertions via the RewriteTest framework
class InvalidExceptionUsageRecipeSuppressionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new InvalidExceptionUsageRecipe());
    }

    @Test
    void shouldSuppressCatchBlockWithCommentBeforeCatch() {
        rewriteRun(
            spec -> spec.expectedCyclesThatMakeChanges(0),
            java(
                """
                class Test {
                    void method() {
                        try {
                            doSomething();
                        // cui-rewrite:disable InvalidExceptionUsageRecipe
                        } catch (RuntimeException e) {
                            handleException(e);
                        }
                    }

                    void doSomething() {}
                    void handleException(Exception e) {}
                }
                """
            )
        );
    }

    @Test
    void shouldSuppressCatchBlockWithCommentDirectlyBeforeCatch() {
        rewriteRun(
            java(
                """
                class Test {
                    void method() {
                        try {
                            doSomething();
                            // cui-rewrite:disable InvalidExceptionUsageRecipe
                        } catch (RuntimeException e) {
                            handleException(e);
                        }
                    }

                    void doSomething() {}
                    void handleException(Exception e) {}
                }
                """
            )
        );
    }

    @Test
    void shouldFlagUnsuppressedCatchBlock() {
        rewriteRun(
            java(
                """
                class Test {
                    void method() {
                        try {
                            doSomething();
                        } catch (RuntimeException e) {
                            handleException(e);
                        }
                    }

                    void doSomething() {}
                    void handleException(Exception e) {}
                }
                """,
                """
                class Test {
                    void method() {
                        try {
                            doSomething();
                        } /*~~(Catch specific not RuntimeException)~~>*/catch (RuntimeException e) {
                            handleException(e);
                        }
                    }

                    void doSomething() {}
                    void handleException(Exception e) {}
                }
                """
            )
        );
    }

    @Test
    void shouldSuppressCatchBlockFromMoreStringsExample() {
        rewriteRun(
            java(
                """
                import java.util.logging.Level;

                class MoreStrings {
                    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger("Test");

                    static String lenientToString(Object o) {
                        try {
                            return String.valueOf(o);
                            // cui-rewrite:disable InvalidExceptionUsageRecipe
                        } catch (RuntimeException e) {
                            final var objectToString = o == null ? "null" :
                                    o.getClass().getName() + '@' + Integer.toHexString(System.identityHashCode(o));
                            LOGGER.log(Level.WARNING, e, () -> "Exception during lenientFormat for " + objectToString);
                            return "<" + objectToString + " threw " + e.getClass().getName() + ">";
                        }
                    }
                }
                """
            )
        );
    }

    @Test
    void shouldSuppressWithGeneralDisableComment() {
        rewriteRun(
            java(
                """
                class Test {
                    void method() {
                        try {
                            doSomething();
                            // cui-rewrite:disable
                        } catch (Exception e) {
                            handleException(e);
                        }
                    }

                    void doSomething() {}
                    void handleException(Exception e) {}
                }
                """
            )
        );
    }

    @Test
    void shouldHandleEmptyTryBlockSuppression() {
        rewriteRun(
            java(
                """
                class Test {
                    void method() {
                        try {
                            // Empty try block
                            // cui-rewrite:disable InvalidExceptionUsageRecipe
                        } catch (Exception e) {
                            handleException(e);
                        }
                    }

                    void handleException(Exception e) {}
                }
                """
            )
        );
    }

    @Test
    void shouldHandleTryBlockWithOnlyComments() {
        rewriteRun(
            java(
                """
                class Test {
                    void method() {
                        try {
                            // Just comments
                            // cui-rewrite:disable
                        } catch (RuntimeException e) {
                            handleException(e);
                        }
                    }

                    void handleException(Exception e) {}
                }
                """
            )
        );
    }

    @Test
    void shouldNotSuppressWithWrongRecipeName() {
        rewriteRun(
            java(
                """
                class Test {
                    void method() {
                        try {
                            doSomething();
                            // cui-rewrite:disable SomeOtherRecipe
                        } catch (Exception e) {
                            handleException(e);
                        }
                    }

                    void doSomething() {}
                    void handleException(Exception e) {}
                }
                """,
                """
                class Test {
                    void method() {
                        try {
                            doSomething();
                            // cui-rewrite:disable SomeOtherRecipe
                        } /*~~(Catch specific not Exception)~~>*/catch (Exception e) {
                            handleException(e);
                        }
                    }

                    void doSomething() {}
                    void handleException(Exception e) {}
                }
                """
            )
        );
    }

    @Test
    void shouldSuppressWithSpecificRecipeNameInComment() {
        rewriteRun(
            java(
                """
                class Test {
                    void method() {
                        try {
                            doSomething();
commit and push
                        // cui-rewrite:disable InvalidExceptionUsageRecipe
                        } catch (Exception e) {
                            handleException(e);
                        }
                    }

                    void doSomething() {}
                    void handleException(Exception e) {}
                }
                """
            )
        );
    }
}