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

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

/**
 * Test to verify proper indentation preservation for GitHub Issue #1:
 * Annotation formatting should preserve parent context indentation in nested structures
 */
@SuppressWarnings("java:S2699") // OpenRewrite tests use implicit assertions
class AnnotationNewlineFormatIssue1Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AnnotationNewlineFormat())
            .parser(JavaParser.fromJavaVersion());
    }

    /**
     * Test proper indentation for nested class annotations.
     * The formatted annotation should maintain the parent class indentation (4 spaces).
     */
    @Test
    void nestedClassShouldPreserveIndentation() {
        rewriteRun(
            java(
                """
                public class Outer {
                    @Deprecated public class Inner {
                        @Override public String toString() {
                            return "inner";
                        }
                    }
                }
                """,
                """
                public class Outer {
                    @Deprecated
                    public class Inner {
                        @Override
                        public String toString() {
                            return "inner";
                        }
                    }
                }
                """
            )
        );
    }

    /**
     * Test deeply nested class indentation.
     */
    @Test
    void deeplyNestedClassShouldPreserveIndentation() {
        rewriteRun(
            java(
                """
                public class Level1 {
                    public class Level2 {
                        @Deprecated public class Level3 {
                            private String field;
                        }
                    }
                }
                """,
                """
                public class Level1 {
                    public class Level2 {
                        @Deprecated
                        public class Level3 {
                            private String field;
                        }
                    }
                }
                """
            )
        );
    }

    /**
     * Test method annotation indentation in nested class.
     */
    @Test
    void methodInNestedClassShouldPreserveIndentation() {
        rewriteRun(
            java(
                """
                public class Outer {
                    public class Inner {
                        @Override public String toString() {
                            return "inner";
                        }
                    }
                }
                """,
                """
                public class Outer {
                    public class Inner {
                        @Override
                        public String toString() {
                            return "inner";
                        }
                    }
                }
                """
            )
        );
    }
}
