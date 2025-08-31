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
class AnnotationSuppressionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AnnotationNewlineFormat())
            .parser(JavaParser.fromJavaVersion());
    }

    @Test
    void suppressSingleLineForClass() {
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
    }

    @Test
    void suppressSingleLineForMethod() {
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
    }

    @Test
    void suppressSingleLineForField() {
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
    }

    @Test
    void suppressGlobalAll() {
        rewriteRun(
            java(
                """
                // cui-rewrite:disable
                @Deprecated @SuppressWarnings("all") public class TestClass {
                    @Override @Deprecated public String toString() {
                        return "test";
                    }
                }
                """
            )
        );
    }

    @Test
    void suppressSpecificRecipeBySimpleName() {
        rewriteRun(
            java(
                """
                // cui-rewrite:disable AnnotationNewlineFormat
                @Deprecated @SuppressWarnings("all") public class TestClass {
                    public static final String VALUE = "test";
                }
                // cui-rewrite:enable AnnotationNewlineFormat
                """
            )
        );
    }

    @Test
    void suppressSpecificRecipeByFullName() {
        rewriteRun(
            java(
                """
                // cui-rewrite:disable de.cuioss.rewrite.format.AnnotationNewlineFormat
                @Deprecated @SuppressWarnings("all") public class TestClass {
                    public static final String VALUE = "test";
                }
                // cui-rewrite:enable de.cuioss.rewrite.format.AnnotationNewlineFormat
                """
            )
        );
    }

    @Test
    void suppressBlockScope() {
        rewriteRun(
            java(
                """
                @Deprecated @SuppressWarnings("all") public class TestClass {
                    // cui-rewrite:disable AnnotationNewlineFormat
                    @Override @Deprecated public String toString() {
                        return "test";
                    }
                    
                    @SuppressWarnings("unused") @Deprecated private String field1;
                    // cui-rewrite:enable AnnotationNewlineFormat
                    
                    @SuppressWarnings("unused") @Deprecated private String field2;
                }
                """,
                """
                @Deprecated
                @SuppressWarnings("all")
                public class TestClass {
                    // cui-rewrite:disable AnnotationNewlineFormat
                    @Override @Deprecated public String toString() {
                        return "test";
                    }
                    
                    @SuppressWarnings("unused") @Deprecated private String field1;
                    // cui-rewrite:enable AnnotationNewlineFormat
                    
                    @SuppressWarnings("unused")
                    @Deprecated
                    private String field2;
                }
                """
            )
        );
    }

    @Test
    void suppressNextLineWithoutRecipeName() {
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
    }

    @Test
    void suppressWithTrailingComment() {
        // Note: Trailing comments on the same line are not fully supported 
        // due to OpenRewrite AST limitations. The comment may be attached 
        // to the body or other parts of the AST, making detection unreliable.
        // This test documents the current behavior.
        rewriteRun(
            java(
                """
                @Deprecated @SuppressWarnings("all") public class TestClass { // cui-rewrite:disable
                    public static final String VALUE = "test";
                }
                """,
                """
                @Deprecated
                @SuppressWarnings("all")
                public class TestClass { // cui-rewrite:disable
                    public static final String VALUE = "test";
                }
                """
            )
        );
    }

    @Test
    void suppressWithWrongRecipeName() {
        rewriteRun(
            java(
                """
                // cui-rewrite:disable SomeOtherRecipe
                @Deprecated @SuppressWarnings("all") public class TestClass {
                    public static final String VALUE = "test";
                }
                """,
                """
                // cui-rewrite:disable SomeOtherRecipe
                @Deprecated
                @SuppressWarnings("all")
                public class TestClass {
                    public static final String VALUE = "test";
                }
                """
            )
        );
    }

    @Test
    void suppressPartialBlock() {
        rewriteRun(
            java(
                """
                public class TestClass {
                    @Override @Deprecated public String method1() {
                        return "1";
                    }
                    
                    // cui-rewrite:disable AnnotationNewlineFormat
                    @Override @Deprecated public String method2() {
                        return "2";
                    }
                    
                    @Override @Deprecated public String method3() {
                        return "3";
                    }
                    // cui-rewrite:enable AnnotationNewlineFormat
                    
                    @Override @Deprecated public String method4() {
                        return "4";
                    }
                }
                """,
                """
                public class TestClass {
                    @Override
                    @Deprecated
                    public String method1() {
                        return "1";
                    }
                    
                    // cui-rewrite:disable AnnotationNewlineFormat
                    @Override @Deprecated public String method2() {
                        return "2";
                    }
                    
                    @Override @Deprecated public String method3() {
                        return "3";
                    }
                    // cui-rewrite:enable AnnotationNewlineFormat
                    
                    @Override
                    @Deprecated
                    public String method4() {
                        return "4";
                    }
                }
                """
            )
        );
    }
}