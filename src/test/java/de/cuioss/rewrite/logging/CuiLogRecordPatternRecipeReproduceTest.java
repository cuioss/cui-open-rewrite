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

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("java:S2699") // OpenRewrite tests use implicit assertions via the RewriteTest framework
class CuiLogRecordPatternRecipeReproduceTest implements RewriteTest {

    @Override public void defaults(RecipeSpec spec) {
        spec.recipe(new CuiLogRecordPatternRecipe());
    }

    @Test void shouldNotFailOnGetNameMethod() {
        rewriteRun(
            java(
                """
                package de.cuioss.tools.logging;

                public class CuiLoggerFactoryTest {

                    public void testMethod() {
                        TestObject obj = new TestObject();
                        String name = obj.getName(); // This should not crash the recipe
                    }

                    static class TestObject {
                        public String getName() {
                            return "test";
                        }
                    }
                }
                """
            )
        );
    }

    @Test void shouldNotFailOnNonLoggerMethods() {
        rewriteRun(
            java(
                """
                package de.cuioss.tools.logging;

                import de.cuioss.tools.logging.CuiLogger;
                import de.cuioss.tools.logging.CuiLoggerFactory;

                public class CuiLoggerFactoryTest {

                    private static final CuiLogger LOG = CuiLoggerFactory.getLogger(CuiLoggerFactoryTest.class);

                    public void testMethod() {
                        String name = getName(); // This should not be processed as a log level
                        LOG.info("Test message"); // This should be processed but shouldn't fail
                    }

                    private String getName() {
                        return "test";
                    }
                }
                """,
                """
                package de.cuioss.tools.logging;

                import de.cuioss.tools.logging.CuiLogger;
                import de.cuioss.tools.logging.CuiLoggerFactory;

                public class CuiLoggerFactoryTest {

                    private static final CuiLogger LOG = CuiLoggerFactory.getLogger(CuiLoggerFactoryTest.class);

                    public void testMethod() {
                        String name = getName(); // This should not be processed as a log level
                        /*~~(INFO needs LogRecord)~~>*/LOG.info("Test message"); // This should be processed but shouldn't fail
                    }

                    private String getName() {
                        return "test";
                    }
                }
                """
            )
        );
    }
}