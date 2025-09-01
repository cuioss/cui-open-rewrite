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

import de.cuioss.rewrite.util.RecipeSuppressionUtil;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

/**
 * Tests that verify logging behavior of the AnnotationNewlineFormat recipe
 * according to the CUI logging standards.
 * llm-rules: /Users/oliver/git/cui-llm-rules/standards/logging/testing-guide.adoc
 */
@EnableTestLogger
class AnnotationSuppressionLoggingTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AnnotationNewlineFormat())
            .parser(JavaParser.fromJavaVersion());
    }

    @Test
    void shouldLogWhenClassSuppressionIsTriggered() {
        // when
        rewriteRun(
            java(
                """
                // cui-rewrite:disable
                @Deprecated @SuppressWarnings("all") public class TestClass {
                    public static final String VALUE = "test";
                }
                """
            )
        );

        // then - verify info level log message is present
        LogAsserts.assertLogMessagePresentContaining(
            TestLogLevel.INFO,
            "Skipping class 'TestClass' for recipe 'AnnotationNewlineFormat' due to cui-rewrite:disable comment"
        );
    }

    @Test
    void shouldLogWhenMethodSuppressionIsTriggered() {
        // when
        rewriteRun(
            java(
                """
                public class TestClass {
                    // cui-rewrite:disable
                    @Override @Deprecated public String toString() {
                        return "test";
                    }
                }
                """
            )
        );

        // then - verify info level log message is present
        LogAsserts.assertLogMessagePresentContaining(
            TestLogLevel.INFO,
            "Skipping method 'toString' for recipe 'AnnotationNewlineFormat' due to cui-rewrite:disable comment"
        );
    }

    @Test
    void shouldLogWhenFieldSuppressionIsTriggered() {
        // when
        rewriteRun(
            java(
                """
                public class TestClass {
                    // cui-rewrite:disable
                    @Deprecated @SuppressWarnings("unused") private String field;
                }
                """
            )
        );

        // then - verify info level log message is present
        LogAsserts.assertLogMessagePresentContaining(
            TestLogLevel.INFO,
            "Skipping field"
        );
    }

    @Test
    void shouldNotLogWhenNoSuppressionPresent() {
        // when - format without suppression comment
        rewriteRun(
            java(
                """
                @Deprecated @SuppressWarnings("all") public class TestClass {
                    public static final String VALUE = "test";
                }
                """,
                """
                @Deprecated
                @SuppressWarnings("all")
                public class TestClass {
                    public static final String VALUE = "test";
                }
                """
            )
        );

        // then - verify no info message about suppression
        // The suppression message should not be present when no suppression comment exists
        LogAsserts.assertNoLogMessagePresent(
            TestLogLevel.INFO,
            "Skipping class 'TestClass' for recipe 'AnnotationNewlineFormat' due to cui-rewrite:disable comment"
        );
    }

    @Test
    void shouldNotLogWarningsOrErrors() {
        // when - execute any recipe operation
        rewriteRun(
            java(
                """
                // cui-rewrite:disable
                @Deprecated public class TestClass {}
                """
            )
        );

        // then - verify no warnings or errors are logged
        // Using the class-based assertion for general check
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN,
            RecipeSuppressionUtil.class);
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.ERROR,
            RecipeSuppressionUtil.class);
    }

    // Debug level testing is optional according to the testing guide
    // and CuiLogger defaults to INFO level in tests
}