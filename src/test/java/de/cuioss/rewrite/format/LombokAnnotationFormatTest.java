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
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("java:S2699") // OpenRewrite tests use implicit assertions via the RewriteTest framework
class LombokAnnotationFormatTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AnnotationNewlineFormat())
            .parser(JavaParser.fromJavaVersion()
                .logCompilationWarningsAndErrors(true))
            .typeValidationOptions(TypeValidation.none());
    }

    @DocumentExample
    @Test
    void formatUtilityClassAnnotation() {
        rewriteRun(
            java(
                """
                import lombok.experimental.UtilityClass;
                
                @UtilityClass public class Constants {
                    public static final String VALUE = "test";
                }
                """,
                """
                import lombok.experimental.UtilityClass;
                
                @UtilityClass
                public class Constants {
                    public static final String VALUE = "test";
                }
                """
            )
        );
    }

    @Test
    void formatDataAnnotation() {
        rewriteRun(
            java(
                """
                import lombok.Data;
                
                @Data public class Person {
                    private String name;
                    private int age;
                }
                """,
                """
                import lombok.Data;
                
                @Data
                public class Person {
                    private String name;
                    private int age;
                }
                """
            )
        );
    }

    @Test
    void formatMultipleLombokAnnotations() {
        rewriteRun(
            java(
                """
                import lombok.Data;
                import lombok.Builder;
                import lombok.NoArgsConstructor;
                import lombok.AllArgsConstructor;
                
                @Data @Builder @NoArgsConstructor @AllArgsConstructor public class Person {
                    private String name;
                    private int age;
                }
                """,
                """
                import lombok.Data;
                import lombok.Builder;
                import lombok.NoArgsConstructor;
                import lombok.AllArgsConstructor;
                
                @Data
                @Builder
                @NoArgsConstructor
                @AllArgsConstructor
                public class Person {
                    private String name;
                    private int age;
                }
                """
            )
        );
    }

    @Test
    void formatSlf4jAnnotation() {
        rewriteRun(
            java(
                """
                import lombok.extern.slf4j.Slf4j;
                
                @Slf4j public class LoggingService {
                    public void doSomething() {
                        log.info("Doing something");
                    }
                }
                """,
                """
                import lombok.extern.slf4j.Slf4j;
                
                @Slf4j
                public class LoggingService {
                    public void doSomething() {
                        log.info("Doing something");
                    }
                }
                """
            )
        );
    }

    @Test
    void preserveAlreadyFormattedLombokAnnotations() {
        rewriteRun(
            java(
                """
                import lombok.Data;
                import lombok.Builder;
                
                @Data
                @Builder
                public class Person {
                    private String name;
                    private int age;
                }
                """
            )
        );
    }

    @Test
    void formatMixedLombokAndJavaAnnotations() {
        rewriteRun(
            java(
                """
                import lombok.Data;
                
                @Data @Deprecated public class OldPerson {
                    private String name;
                }
                """,
                """
                import lombok.Data;
                
                @Data
                @Deprecated
                public class OldPerson {
                    private String name;
                }
                """
            )
        );
    }
}