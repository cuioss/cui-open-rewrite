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
                        } /*~~(TODO: Catch specific not Exception)~~>*/catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    void doSomething() throws Exception {
                        /*~~(TODO: Throw specific not Exception)~~>*/throw new Exception("test");
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
                        } /*~~(TODO: Catch specific not RuntimeException)~~>*/catch (RuntimeException e) {
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
                        } /*~~(TODO: Catch specific not Throwable)~~>*/catch (Throwable t) {
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
                        /*~~(TODO: Throw specific not Exception)~~>*/throw new Exception("Bad practice");
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
                        /*~~(TODO: Throw specific not RuntimeException)~~>*/throw new RuntimeException("Bad practice");
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
                        Exception e = /*~~(TODO: Use specific not Exception)~~>*/new Exception("Bad practice");
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
                        } /*~~(TODO: Catch specific not Exception)~~>*/catch (Exception e) {
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
                            } /*~~(TODO: Catch specific not RuntimeException)~~>*/catch (RuntimeException re) {
                                /*~~(TODO: Throw specific not Exception)~~>*/throw new Exception("Wrapping", re);
                            }
                        } /*~~(TODO: Catch specific not Exception)~~>*/catch (Exception e) {
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
                            } /*~~(TODO: Catch specific not Exception)~~>*/catch (Exception e) {
                                /*~~(TODO: Throw specific not RuntimeException)~~>*/throw new RuntimeException(e);
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

    @Test void respectClassLevelSuppression() {
        rewriteRun(
            java(
                """
                // cui-rewrite:disable InvalidExceptionUsageRecipe
                class Test {
                    void test() {
                        try {
                            doSomething();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }

                    void doSomething() throws Exception {
                        throw new Exception("Should be suppressed");
                    }
                }
                """
            )
        );
    }

    @Test void detectDuplicateMarkersNotAdded() {
        rewriteRun(
            spec -> spec.expectedCyclesThatMakeChanges(1),
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
                        /*~~(TODO: Throw specific not Exception)~~>*/throw new Exception("Bad practice");
                    }
                }
                """
            )
        );
    }

    @Test void detectExceptionInAssignment() {
        rewriteRun(
            java(
                """
                class Test {
                    Exception createException() {
                        return new RuntimeException("Assignment case");
                    }
                }
                """,
                """
                class Test {
                    Exception createException() {
                        return /*~~(TODO: Use specific not RuntimeException)~~>*/new RuntimeException("Assignment case");
                    }
                }
                """
            )
        );
    }

    @Test void detectExceptionPassedAsParameter() {
        rewriteRun(
            java(
                """
                class Test {
                    void test() {
                        handleException(new Exception("Parameter case"));
                    }
                    
                    void handleException(Exception e) {
                        // Handle exception
                    }
                }
                """,
                """
                class Test {
                    void test() {
                        handleException(/*~~(TODO: Use specific not Exception)~~>*/new Exception("Parameter case"));
                    }
                    
                    void handleException(Exception e) {
                        // Handle exception
                    }
                }
                """
            )
        );
    }

    @Test void respectMethodLevelSuppression() {
        rewriteRun(
            java(
                """
                class Test {
                    // cui-rewrite:disable InvalidExceptionUsageRecipe
                    void suppressedMethod() throws Exception {
                        throw new Exception("Method suppressed");
                    }

                    void notSuppressedMethod() throws Exception {
                        throw new Exception("Not suppressed");
                    }
                }
                """,
                """
                class Test {
                    // cui-rewrite:disable InvalidExceptionUsageRecipe
                    void suppressedMethod() throws Exception {
                        throw new Exception("Method suppressed");
                    }

                    void notSuppressedMethod() throws Exception {
                        /*~~(TODO: Throw specific not Exception)~~>*/throw new Exception("Not suppressed");
                    }
                }
                """
            )
        );
    }

    @Test void handleThrowableType() {
        rewriteRun(
            java(
                """
                class Test {
                    void test() {
                        try {
                            doSomething();
                        } catch (Throwable t) {
                            throw new Throwable("Wrapping", t);
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
                            doSomething();
                        } /*~~(TODO: Catch specific not Throwable)~~>*/catch (Throwable t) {
                            /*~~(TODO: Throw specific not Throwable)~~>*/throw new Throwable("Wrapping", t);
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

    @Test void handleExceptionInStaticContext() {
        rewriteRun(
            java(
                """
                class Test {
                    static {
                        try {
                            initialize();
                        } catch (Exception e) {
                            throw new RuntimeException("Static init failed", e);
                        }
                    }

                    static void initialize() throws Exception {
                        throw new Exception("Init error");
                    }
                }
                """,
                """
                class Test {
                    static {
                        try {
                            initialize();
                        } /*~~(TODO: Catch specific not Exception)~~>*/catch (Exception e) {
                            /*~~(TODO: Throw specific not RuntimeException)~~>*/throw new RuntimeException("Static init failed", e);
                        }
                    }

                    static void initialize() throws Exception {
                        /*~~(TODO: Throw specific not Exception)~~>*/throw new Exception("Init error");
                    }
                }
                """
            )
        );
    }

    @Test void preventDuplicateMarkersOnCatch() {
        rewriteRun(
            spec -> spec.expectedCyclesThatMakeChanges(1).cycles(2),
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
                        // Something
                    }
                }
                """,
                """
                class Test {
                    void test() {
                        try {
                            doSomething();
                        } /*~~(TODO: Catch specific not Exception)~~>*/catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    void doSomething() throws Exception {
                        // Something
                    }
                }
                """
            )
        );
    }

    @Test void preventDuplicateMarkersOnThrow() {
        rewriteRun(
            spec -> spec.expectedCyclesThatMakeChanges(1).cycles(2),
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
                        /*~~(TODO: Throw specific not Exception)~~>*/throw new Exception("Bad practice");
                    }
                }
                """
            )
        );
    }

    @Test void preventDuplicateMarkersOnNewClass() {
        rewriteRun(
            spec -> spec.expectedCyclesThatMakeChanges(1).cycles(2),
            java(
                """
                class Test {
                    Exception createException() {
                        return new RuntimeException("Assignment case");
                    }
                }
                """,
                """
                class Test {
                    Exception createException() {
                        return /*~~(TODO: Use specific not RuntimeException)~~>*/new RuntimeException("Assignment case");
                    }
                }
                """
            )
        );
    }

    @Test void handleExceptionInTryWithResources() {
        rewriteRun(
            java(
                """
                import java.io.FileInputStream;
                import java.io.IOException;

                class Test {
                    void test() {
                        try (FileInputStream fis = new FileInputStream("test.txt")) {
                            // Use resource
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
                """,
                """
                import java.io.FileInputStream;
                import java.io.IOException;

                class Test {
                    void test() {
                        try (FileInputStream fis = new FileInputStream("test.txt")) {
                            // Use resource
                        } /*~~(TODO: Catch specific not Exception)~~>*/catch (Exception e) {
                            /*~~(TODO: Throw specific not RuntimeException)~~>*/throw new RuntimeException(e);
                        }
                    }
                }
                """
            )
        );
    }

}