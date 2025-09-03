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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("java:S2699") // OpenRewrite tests use implicit assertions via the RewriteTest framework
class InvalidExceptionUsageRecipeTest implements RewriteTest {

    @Override public void defaults(RecipeSpec spec) {
        spec.recipe(new InvalidExceptionUsageRecipe())
            .parser(JavaParser.fromJavaVersion());
    }

    @Test void detectCatchingException() {
        rewriteRun(
            java(
                """
                class Test {
                    void test() {
                        try {
                            doSomething();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    void doSomething() throws Exception {
                        throw new Exception("test");
                    }
                }
                """,
                """
                class Test {
                    void test() {
                        try {
                            doSomething();
                        } /*~~(TASK: Catch specific not Exception)~~>*/catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    void doSomething() throws Exception {
                        /*~~(TASK: Throw specific not Exception)~~>*/throw new Exception("test");
                    }
                }
                """
            )
        );
    }

    @Test void detectCatchingRuntimeException() {
        rewriteRun(
            java(
                """
                class Test {
                    void test() {
                        try {
                            doSomething();
                        } catch (RuntimeException e) {
                            e.printStackTrace();
                        }
                    }

                    void doSomething() {
                        // Something that might throw RuntimeException
                    }
                }
                """,
                """
                class Test {
                    void test() {
                        try {
                            doSomething();
                        } /*~~(TASK: Catch specific not RuntimeException)~~>*/catch (RuntimeException e) {
                            e.printStackTrace();
                        }
                    }

                    void doSomething() {
                        // Something that might throw RuntimeException
                    }
                }
                """
            )
        );
    }

    @Test void detectCatchingThrowable() {
        rewriteRun(
            java(
                """
                class Test {
                    void test() {
                        try {
                            doSomething();
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    }

                    void doSomething() {
                        // Something that might throw
                    }
                }
                """,
                """
                class Test {
                    void test() {
                        try {
                            doSomething();
                        } /*~~(TASK: Catch specific not Throwable)~~>*/catch (Throwable t) {
                            t.printStackTrace();
                        }
                    }

                    void doSomething() {
                        // Something that might throw
                    }
                }
                """
            )
        );
    }

    @Test void detectThrowingException() {
        rewriteRun(
            java(
                """
                class Test {
                    void test() throws Exception {
                        throw new Exception("Bad practice");
                    }
                }
                """,
                """
                class Test {
                    void test() throws Exception {
                        /*~~(TASK: Throw specific not Exception)~~>*/throw new Exception("Bad practice");
                    }
                }
                """
            )
        );
    }

    @Test void detectThrowingRuntimeException() {
        rewriteRun(
            java(
                """
                class Test {
                    void test() {
                        throw new RuntimeException("Bad practice");
                    }
                }
                """,
                """
                class Test {
                    void test() {
                        /*~~(TASK: Throw specific not RuntimeException)~~>*/throw new RuntimeException("Bad practice");
                    }
                }
                """
            )
        );
    }

    @Test void detectCreatingGenericException() {
        rewriteRun(
            java(
                """
                class Test {
                    void test() {
                        Exception e = new Exception("Bad practice");
                        // Do something with e
                    }
                }
                """,
                """
                class Test {
                    void test() {
                        Exception e = /*~~(TASK: Use specific not Exception)~~>*/new Exception("Bad practice");
                        // Do something with e
                    }
                }
                """
            )
        );
    }

    @Test void allowSpecificExceptions() {
        rewriteRun(
            java(
                """
                import java.io.IOException;

                class Test {
                    void test() {
                        try {
                            doSomething();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    void doSomething() throws IOException {
                        throw new IOException("Specific exception is OK");
                    }
                }
                """
            )
        );
    }

    @Test void allowMultipleCatchWithSpecificFirst() {
        rewriteRun(
            java(
                """
                import java.io.IOException;

                class Test {
                    void test() {
                        try {
                            doSomething();
                        } catch (IOException e) {
                            // Handle specific exception
                        } catch (Exception e) {
                            // Generic catch as fallback
                        }
                    }

                    void doSomething() throws IOException {
                        // Something
                    }
                }
                """,
                """
                import java.io.IOException;

                class Test {
                    void test() {
                        try {
                            doSomething();
                        } catch (IOException e) {
                            // Handle specific exception
                        } /*~~(TASK: Catch specific not Exception)~~>*/catch (Exception e) {
                            // Generic catch as fallback
                        }
                    }

                    void doSomething() throws IOException {
                        // Something
                    }
                }
                """
            )
        );
    }

    @Test void respectSuppressionComment() {
        rewriteRun(
            java(
                """
                class Test {
                    void test() {
                        try {
                            doSomething();
                        }
                        // cui-rewrite:disable InvalidExceptionUsageRecipe
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    void doSomething() throws Exception {
                        // cui-rewrite:disable InvalidExceptionUsageRecipe
                        throw new Exception("Suppressed");
                    }
                }
                """
            )
        );
    }

    @Test void respectGeneralSuppressionComment() {
        rewriteRun(
            java(
                """
                class Test {
                    void test() {
                        try {
                            doSomething();
                        }
                        // cui-rewrite:disable
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    void doSomething() throws Exception {
                        // cui-rewrite:disable
                        throw new Exception("Suppressed");
                    }
                }
                """
            )
        );
    }

    @Test void detectNestedExceptionUsage() {
        rewriteRun(
            java(
                """
                class Test {
                    void test() {
                        try {
                            try {
                                doSomething();
                            } catch (RuntimeException re) {
                                throw new Exception("Wrapping", re);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    void doSomething() {
                        // Something
                    }
                }
                """,
                """
                class Test {
                    void test() {
                        try {
                            try {
                                doSomething();
                            } /*~~(TASK: Catch specific not RuntimeException)~~>*/catch (RuntimeException re) {
                                /*~~(TASK: Throw specific not Exception)~~>*/throw new Exception("Wrapping", re);
                            }
                        } /*~~(TASK: Catch specific not Exception)~~>*/catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    void doSomething() {
                        // Something
                    }
                }
                """
            )
        );
    }

    @Test void detectExceptionInLambda() {
        rewriteRun(
            java(
                """
                import java.util.function.Supplier;

                class Test {
                    void test() {
                        Supplier<String> supplier = () -> {
                            try {
                                return doSomething();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        };
                    }

                    String doSomething() throws Exception {
                        return "test";
                    }
                }
                """,
                """
                import java.util.function.Supplier;

                class Test {
                    void test() {
                        Supplier<String> supplier = () -> {
                            try {
                                return doSomething();
                            } /*~~(TASK: Catch specific not Exception)~~>*/catch (Exception e) {
                                /*~~(TASK: Throw specific not RuntimeException)~~>*/throw new RuntimeException(e);
                            }
                        };
                    }

                    String doSomething() throws Exception {
                        return "test";
                    }
                }
                """
            )
        );
    }

    @Test void allowCustomExceptionExtendingGeneric() {
        rewriteRun(
            java(
                """
                class CustomException extends Exception {
                    CustomException(String message) {
                        super(message);
                    }
                }

                class Test {
                    void test() throws CustomException {
                        try {
                            doSomething();
                        } catch (CustomException e) {
                            throw new CustomException("Rethrowing custom");
                        }
                    }

                    void doSomething() throws CustomException {
                        throw new CustomException("Custom is OK");
                    }
                }
                """
            )
        );
    }

}