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

@SuppressWarnings("java:S2699") // OpenRewrite tests use implicit assertions via the RewriteTest framework
class AnnotationNewlineFormatTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AnnotationNewlineFormat())
            .parser(JavaParser.fromJavaVersion());
    }

    @Test
    void formatSingleClassAnnotation() {
        rewriteRun(
            java(
                """
                @Deprecated public class Constants {
                    public static final String VALUE = "test";
                }
                """,
                """
                @Deprecated
                public class Constants {
                    public static final String VALUE = "test";
                }
                """
            )
        );
    }

    @Test
    void formatMultipleClassAnnotations() {
        rewriteRun(
            java(
                """
                @Deprecated @SuppressWarnings("all") public class Person {
                    private String name;
                    private int age;
                }
                """,
                """
                @Deprecated
                @SuppressWarnings("all")
                public class Person {
                    private String name;
                    private int age;
                }
                """
            )
        );
    }

    @Test
    void preserveExistingNewlines() {
        rewriteRun(
            java(
                """
                @Deprecated
                public class AlreadyFormatted {
                    @Override
                    public String toString() {
                        return "formatted";
                    }
                }
                """
            )
        );
    }

    @Test
    void formatInterfaceAnnotations() {
        rewriteRun(
            java(
                """
                @FunctionalInterface public interface MyFunction {
                    void apply();
                }
                """,
                """
                @FunctionalInterface
                public interface MyFunction {
                    void apply();
                }
                """
            )
        );
    }

    @Test
    void formatEnumAnnotations() {
        rewriteRun(
            java(
                """
                @Deprecated public enum Status {
                    ACTIVE, INACTIVE
                }
                """,
                """
                @Deprecated
                public enum Status {
                    ACTIVE, INACTIVE
                }
                """
            )
        );
    }
}