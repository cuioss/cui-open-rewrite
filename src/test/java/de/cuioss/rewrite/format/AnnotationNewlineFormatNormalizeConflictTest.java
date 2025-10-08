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
package de.cuioss.rewrite.format;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

/**
 * Tests the specific conflict between AnnotationNewlineFormat and NormalizeFormatVisitor.
 * <p>
 * NormalizeFormatVisitor moves all whitespace to the outermost AST element, which removes
 * the newlines that AnnotationNewlineFormat adds between annotations and declarations.
 * <p>
 * This test verifies that AnnotationNewlineFormat can handle NormalizeFormatVisitor
 * and maintain the split annotations.
 * <p>
 * <b>IMPORTANT: DO NOT MODIFY THIS TEST WITHOUT EXPLICIT USER PERMISSION!</b>
 * <p>
 * This test reproduces a critical bug where AnnotationNewlineFormat and NormalizeFormatVisitor
 * conflict with each other. The test MUST fail until the implementation is fixed properly.
 * Do not "fix" this test by changing the expected behavior - the implementation must be fixed instead.
 * <p>
 * The test uses BOTH recipes (.recipe().recipe()) intentionally to reproduce the exact
 * scenario that occurs in the pre-commit profile where AutoFormat (which includes
 * NormalizeFormatVisitor) runs together with AnnotationNewlineFormat.
 */
@SuppressWarnings("java:S2699") // OpenRewrite tests use implicit assertions via the RewriteTest framework
class AnnotationNewlineFormatNormalizeConflictTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        // CRITICAL: Order matters! NormalizeFormat must run FIRST, then AnnotationNewlineFormat
        // This mimics the real pre-commit profile where AutoFormat (includes Normalize) runs before AnnotationNewlineFormat
        spec.recipe(new NormalizeFormatRecipe())
            .recipe(new AnnotationNewlineFormat())
            .parser(JavaParser.fromJavaVersion()
                .dependsOn(
                    """
                    package org.junit.jupiter.api;
                    public @interface Test {}
                    """,
                    """
                    package org.junit.jupiter.api;
                    public @interface BeforeEach {}
                    """
                ));
    }

    /**
     * Test that AnnotationNewlineFormat's formatting survives NormalizeFormatVisitor.
     * This is the core issue: NormalizeFormatVisitor moves whitespace to the method
     * declaration and removes it from between the annotation and method.
     */
    @Test
    void shouldSplitAnnotationAndSurviveNormalization() {
        rewriteRun(
            java(
                """
                import org.junit.jupiter.api.Test;

                class TestClass {
                    @Test void method() {
                        // test
                    }
                }
                """,
                """
                import org.junit.jupiter.api.Test;

                class TestClass {
                    @Test
                    void method() {
                        // test
                    }
                }
                """
            )
        );
    }

    /**
     * Test with class-level annotations
     * NOTE: Classes without modifiers are not currently supported due to OpenRewrite AST limitations.
     * The "class" keyword is not a separate AST node, so we cannot add newline before it.
     * In practice, classes almost always have modifiers (public, final, abstract, etc.)
     */
    @Disabled("Classes without modifiers not supported - see comment")
    @Test
    void shouldSplitClassAnnotationAndSurviveNormalization() {
        rewriteRun(
            java(
                """
                @Deprecated class TestClass {
                    void method() {
                    }
                }
                """,
                """
                @Deprecated
                class TestClass {
                    void method() {
                    }
                }
                """
            )
        );
    }

    /**
     * Test with field-level annotations
     */
    @Test
    void shouldSplitFieldAnnotationAndSurviveNormalization() {
        rewriteRun(
            java(
                """
                class TestClass {
                    @Deprecated String field;
                }
                """,
                """
                class TestClass {
                    @Deprecated
                    String field;
                }
                """
            )
        );
    }

    /**
     * Test with multiple annotations
     */
    @Test
    void shouldSplitMultipleAnnotationsAndSurviveNormalization() {
        rewriteRun(
            java(
                """
                import org.junit.jupiter.api.Test;
                import org.junit.jupiter.api.BeforeEach;

                class TestClass {
                    @BeforeEach void setUp() {
                    }

                    @Test void testOne() {
                    }

                    @Test void testTwo() {
                    }
                }
                """,
                """
                import org.junit.jupiter.api.Test;
                import org.junit.jupiter.api.BeforeEach;

                class TestClass {
                    @BeforeEach
                    void setUp() {
                    }

                    @Test
                    void testOne() {
                    }

                    @Test
                    void testTwo() {
                    }
                }
                """
            )
        );
    }

    /**
     * Test with method that has return type (not void)
     */
    @Test
    void shouldSplitAnnotationFromMethodWithReturnType() {
        rewriteRun(
            java(
                """
                class TestClass {
                    @Override String toString() {
                        return "test";
                    }
                }
                """,
                """
                class TestClass {
                    @Override
                    String toString() {
                        return "test";
                    }
                }
                """
            )
        );
    }
}
