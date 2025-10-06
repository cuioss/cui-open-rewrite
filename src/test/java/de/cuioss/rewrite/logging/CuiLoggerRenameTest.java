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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("java:S2699") // OpenRewrite tests use implicit assertions via the RewriteTest framework
class CuiLoggerRenameTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CuiLoggerStandardsRecipe())
            .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void renamesLoggerToUppercase() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;

                class Test {
                    private static final CuiLogger log = new CuiLogger(Test.class);
                }
                """,
                """
                import de.cuioss.tools.logging.CuiLogger;

                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                }
                """
            )
        );
    }

    @Test
    void doesNotRenameCorrectLoggerName() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;

                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                }
                """
            )
        );
    }
}