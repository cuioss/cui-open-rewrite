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
import org.openrewrite.java.format.AutoFormat;
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

    @Test
    void formatPackagePrivateMethodWithAnnotation() {
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

    @Test
    void formatPackagePrivateFieldWithAnnotation() {
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

    @Test
    void formatMultiplePackagePrivateFieldsWithAnnotations() {
        rewriteRun(
            java(
                """
                class TestClass {
                    @Deprecated String field1;
                    @Deprecated @SuppressWarnings("all") Object field2;
                }
                """,
                """
                class TestClass {
                    @Deprecated
                    String field1;
                    @Deprecated
                    @SuppressWarnings("all")
                    Object field2;
                }
                """
            )
        );
    }

    @Test
    void formatPackagePrivateClassWithAnnotation() {
        // This test expects no change since package-private classes at top level
        // already have the correct formatting
        rewriteRun(
            java(
                """
                @Deprecated
                class PackagePrivateClass {
                    void method() {}
                }
                """
            )
        );
    }

    @Test
    void formatMethodWithoutModifiersButWithReturnType() {
        rewriteRun(
            java(
                """
                class TestClass {
                    @Deprecated @SuppressWarnings("all") String getName() {
                        return "name";
                    }
                }
                """,
                """
                class TestClass {
                    @Deprecated
                    @SuppressWarnings("all")
                    String getName() {
                        return "name";
                    }
                }
                """
            )
        );
    }

    @Test
    void formatFieldWithoutModifiersButWithType() {
        rewriteRun(
            java(
                """
                class TestClass {
                    @Deprecated @SuppressWarnings("all") String[] items;
                }
                """,
                """
                class TestClass {
                    @Deprecated
                    @SuppressWarnings("all")
                    String[] items;
                }
                """
            )
        );
    }

    @Test
    void preserveFormattingWhenNoAnnotations() {
        rewriteRun(
            java(
                """
                public class TestClass {
                    private String field;

                    public void method() {
                        // method body
                    }
                }
                """
            )
        );
    }

    @Test
    void formatNestedClassAnnotations() {
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

    @Test
    void formatAnnotationWithArrayValues() {
        rewriteRun(
            java(
                """
                @SuppressWarnings({"unchecked", "rawtypes"}) public class TestClass {
                    private Object field;
                }
                """,
                """
                @SuppressWarnings({"unchecked", "rawtypes"})
                public class TestClass {
                    private Object field;
                }
                """
            )
        );
    }

    // FIXED: Trailing comments on annotations are now preserved on the same line
    // This was previously a known limitation, but has been resolved.
    // Single annotations with inline comments are now left untouched.
    @Test
    void preserveTrailingCommentsOnSingleAnnotation() {
        rewriteRun(
            java(
                """
                public class TestClass {
                    @SuppressWarnings("squid:S00107") // This comment explains why
                    public void methodWithManyParams(int a, int b, int c, int d, int e, int f, int g, int h) {
                        // method body
                    }
                }
                """
            // Expected: NO CHANGE - inline comment is preserved
            )
        );
    }

    // FIXED: Trailing inline comments are now preserved on the same line
    // This was previously a known limitation, but has been resolved.
    // Single annotations with inline comments are now left untouched.
    @Test
    void preserveTrailingCommentOnSingleAnnotation() {
        rewriteRun(
            java(
                """
                public class AccessTokenCache {
                    @SuppressWarnings("java:S3776") // owolff: 16 instead of 15 is acceptable here due to complexity of cache logic
                    public String computeIfAbsent(String key) {
                        return null;
                    }
                }
                """
            // Expected: NO CHANGE - inline comment is preserved
            )
        );
    }

    // Reproducing Issue: Trailing comments are being moved to separate line
    // when method has no modifiers (package-private)
    @Test
    void preserveTrailingCommentOnAnnotationWithPackagePrivateMethod() {
        rewriteRun(
            java(
                """
                public class TestClass {
                    @SuppressWarnings("java:S1612") // Cannot use method reference due to ambiguous get() methods
                    void concurrentAccess() {
                        // method body
                    }
                }
                """
            // Expected: NO CHANGE - inline comment should be preserved
            )
        );
    }

    // Test with method that already has the annotation on a separate line
    // to ensure the recipe skips it
    @Test
    void skipWhenAnnotationAlreadyOnSeparateLine() {
        rewriteRun(
            java(
                """
                public class TestClass {
                    @SuppressWarnings("java:S1612")
                    void concurrentAccess() {
                        // method body
                    }
                }
                """
            // Expected: NO CHANGE - already properly formatted
            )
        );
    }

    // Test with annotation already on separate line BUT with trailing comment
    // The comment should stay on the annotation's line
    @Test
    void annotationOnSeparateLineWithTrailingComment() {
        rewriteRun(
            java(
                """
                public class TestClass {
                    @SuppressWarnings("java:S1612") // Cannot use method reference due to ambiguous get() methods
                    void concurrentAccess() {
                        // method body
                    }
                }
                """
            // Expected: NO CHANGE - inline comment should be preserved
            )
        );
    }

    // Test the EXACT scenario from the user's example with proper indentation
    @Test
    void exactUserScenarioWithTrailingComment() {
        rewriteRun(
            java(
                """
                public class ConcurrentAccessTest {
                    @SuppressWarnings("java:S1612") // Cannot use method reference due to ambiguous get() methods
                    void concurrentAccess() {
                        // Test concurrent access patterns
                    }
                }
                """
            // Expected: NO CHANGE
            // Should NOT transform to:
            // @SuppressWarnings("java:S1612")
            // // Cannot use method reference due to ambiguous get() methods
            // void concurrentAccess() {
            )
        );
    }

    // FIXED: Multiple annotations with trailing comment on last annotation
    // The recipe now correctly preserves trailing comments
    @Test
    void multipleAnnotationsWithTrailingCommentOnLast_ShouldPreserveComment() {
        rewriteRun(
            java(
                """
                public class TestClass {
                    @Deprecated
                    @SuppressWarnings("java:S1612") // Cannot use method reference due to ambiguous get() methods
                    void concurrentAccess() {
                        // method body
                    }
                }
                """
            // Expected: NO CHANGE - trailing comment is preserved
            )
        );
    }

    // FIXED: @Override with trailing comment
    @Test
    void overrideAnnotationWithTrailingComment_ShouldPreserveComment() {
        rewriteRun(
            java(
                """
                public class HttpJwksLoader {
                    @Override
                    @SuppressWarnings("java:S3776") // Cognitive complexity - initialization logic requires these checks
                    public void initJWKSLoader() {
                        // method body
                    }
                }
                """
            // Expected: NO CHANGE - trailing comment is preserved
            )
        );
    }

    // FIXED: Field with multiple annotations and trailing comment on last
    @Test
    void fieldWithMultipleAnnotationsAndTrailingComment_ShouldPreserveComment() {
        rewriteRun(
            java(
                """
                import java.util.List;

                public class ClaimValue {
                    @SuppressWarnings("unused")
                    @Deprecated // Must not be null, but may be empty
                    private final List<String> asList = null;
                }
                """
            // Expected: NO CHANGE - trailing comment is preserved
            )
        );
    }

    /**
     * Test that single method annotation is separated from modifiers.
     * Issue: @SafeVarargs private static boolean -> should be @SafeVarargs\nprivate static boolean
     */
    @Test
    void shouldSeparateSingleMethodAnnotationFromModifiers() {
        rewriteRun(
            java(
                """
                class TestClass {
                    @SafeVarargs private static boolean check(Class<?>... types) {
                        return true;
                    }
                }
                """,
                """
                class TestClass {
                    @SafeVarargs
                    private static boolean check(Class<?>... types) {
                        return true;
                    }
                }
                """
            )
        );
    }

    /**
     * Test that method annotations maintain proper indentation when split.
     * Both annotations should have 4-space indentation to match the method's context.
     * This is critical because AutoFormat will recombine annotations with inconsistent indentation.
     */
    @Test
    void shouldPreserveIndentationForMethodAnnotations() {
        rewriteRun(
            java(
                """
                @Deprecated @SuppressWarnings("all") public class TestClass {
                    @Deprecated @Override public void method() {}
                }
                """,
                """
                @Deprecated
                @SuppressWarnings("all")
                public class TestClass {
                    @Deprecated
                    @Override
                    public void method() {}
                }
                """
            )
        );
    }

    /**
     * Reproduces the AutoFormat issue: After AnnotationNewlineFormat splits class-level annotations,
     * AutoFormat should NOT recombine them. This test verifies the formatting persists through AutoFormat.
     */
    @Test
    void classAnnotationsShouldPersistThroughAutoFormat() {
        rewriteRun(
            spec -> spec.recipe(new AnnotationNewlineFormat())
                .recipe(new AutoFormat())
                .parser(JavaParser.fromJavaVersion()
                    .dependsOn(
                        """
                        package de.cuioss.test.juli.junit5;
                        public @interface EnableTestLogger {
                            de.cuioss.test.juli.TestLogLevel rootLevel();
                        }
                        """,
                        """
                        package de.cuioss.test.juli;
                        public enum TestLogLevel { DEBUG }
                        """
                    )),
            java(
                """
                import de.cuioss.test.juli.TestLogLevel;
                import de.cuioss.test.juli.junit5.EnableTestLogger;

                @EnableTestLogger(rootLevel = TestLogLevel.DEBUG) @SuppressWarnings({
                    "java:S2699",
                    "java:S5976"
                })
                class RecipeSuppressionUtilTest {
                    void testMethod() {
                        // body
                    }
                }
                """,
                """
                import de.cuioss.test.juli.TestLogLevel;
                import de.cuioss.test.juli.junit5.EnableTestLogger;

                @EnableTestLogger(rootLevel = TestLogLevel.DEBUG)
                @SuppressWarnings({
                        "java:S2699",
                        "java:S5976"
                })
                class RecipeSuppressionUtilTest {
                    void testMethod() {
                        // body
                    }
                }
                """
            )
        );
    }

}