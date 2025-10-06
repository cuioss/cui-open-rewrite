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
class AnnotationNewlineFormatIssue2Test implements RewriteTest {

    @Override public void defaults(RecipeSpec spec) {
        spec.recipe(new AnnotationNewlineFormat())
            .parser(JavaParser.fromJavaVersion());
    }

    /**
     * Issue #2: Annotation with trailing inline comment should NOT be reformatted
     * when the method declaration is already on the same line.
     *
     * The recipe should NOT change this code because:
     * 1. There's only one annotation
     * 2. The comment is intentionally placed on the same line as the annotation
     * 3. There's no formatting issue to fix
     */
    @Test void shouldNotReformatAnnotationWithTrailingInlineComment() {
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
            // Expected: NO CHANGE - this code should remain exactly as is
            )
        );
    }

    /**
     * Similar test case: Class-level annotation with trailing comment
     */
    @Test void shouldNotReformatClassAnnotationWithTrailingInlineComment() {
        rewriteRun(
            java(
                """
                @Deprecated // This class will be removed in version 2.0
                public class LegacyService {
                    public void process() {
                    }
                }
                """
            // Expected: NO CHANGE
            )
        );
    }

    /**
     * Similar test case: Field-level annotation with trailing comment
     */
    @Test void shouldNotReformatFieldAnnotationWithTrailingInlineComment() {
        rewriteRun(
            java(
                """
                public class Configuration {
                    @Deprecated // Use newLogger instead
                    private String legacyLogger;
                }
                """
            // Expected: NO CHANGE
            )
        );
    }
}
