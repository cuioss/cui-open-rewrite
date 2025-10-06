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

/**
 * Test to reproduce and verify the fix for GitHub Issue #5:
 * TODO markers are added repeatedly on every build run
 */
// cui-rewrite:disable InvalidExceptionUsageRecipe
@SuppressWarnings("java:S2699") // OpenRewrite tests use implicit assertions
class InvalidExceptionUsageRecipeIssue5Test implements RewriteTest {

    @Override public void defaults(RecipeSpec spec) {
        spec.recipe(new InvalidExceptionUsageRecipe())
            .parser(JavaParser.fromJavaVersion());
    }

    /**
     * Test scenario: Code already has the TODO marker comment in source.
     * The recipe should recognize it and NOT add another marker.
     */
    @Test void shouldNotAddDuplicateWhenCommentAlreadyExists() {
        rewriteRun(
            spec -> spec.expectedCyclesThatMakeChanges(0).cycles(1),
            java(
                """
                class Test {
                    void test() {
                        try {
                            doSomething();
                        } /*~~(TODO: Catch specific not Exception. Suppress: // cui-rewrite:disable InvalidExceptionUsageRecipe)~~>*/catch (Exception e) {
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

    /**
     * Test scenario similar to issue description - run recipe multiple times (3 cycles).
     * Should only make changes in first cycle.
     */
    @Test void shouldBeIdempotentAcrossMultipleCycles() {
        rewriteRun(
            spec -> spec.expectedCyclesThatMakeChanges(1).cycles(3),
            java(
                """
                class Test {
                    void test() throws Exception {
                        throw new Exception("test");
                    }
                }
                """,
                """
                class Test {
                    void test() throws Exception {
                        /*~~(TODO: Throw specific not Exception. Suppress: // cui-rewrite:disable InvalidExceptionUsageRecipe)~~>*/throw new Exception("test");
                    }
                }
                """
            )
        );
    }
}
