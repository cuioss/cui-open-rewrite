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

class CuiLoggerStandardsRecipeTest implements RewriteTest {
    
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CuiLoggerStandardsRecipe());
    }
    
    @Test
    void detectIncorrectLoggerName() {
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
                    /*~~>*/private static final CuiLogger log = new CuiLogger(Test.class);
                }
                """
            )
        );
    }
    
    @Test
    void detectIncorrectLoggerModifiers() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;
                
                class Test {
                    public static CuiLogger LOGGER = new CuiLogger(Test.class);
                }
                """,
                """
                import de.cuioss.tools.logging.CuiLogger;
                
                class Test {
                    /*~~>*/public static CuiLogger LOGGER = new CuiLogger(Test.class);
                }
                """
            )
        );
    }
    
    @Test
    void replaceIncorrectPlaceholderPatterns() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;
                
                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    
                    void method() {
                        LOGGER.info("Message with {} placeholder", value);
                        LOGGER.debug("Value is %d", 42);
                    }
                }
                """,
                """
                import de.cuioss.tools.logging.CuiLogger;
                
                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    
                    void method() {
                        /*~~>*/LOGGER.info("Message with %s placeholder", value);
                        /*~~>*/LOGGER.debug("Value is %s", 42);
                    }
                }
                """
            )
        );
    }
    
    @Test
    void detectParameterCountMismatch() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;
                
                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    
                    void method() {
                        LOGGER.info("Message with %s and %s", value1);
                    }
                }
                """,
                """
                import de.cuioss.tools.logging.CuiLogger;
                
                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    
                    void method() {
                        /*~~>*/LOGGER.info("Message with %s and %s", value1);
                    }
                }
                """
            )
        );
    }
    
    @Test
    void detectWrongExceptionPosition() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;
                
                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    
                    void method(Exception e) {
                        LOGGER.error("Error occurred", e);
                    }
                }
                """,
                """
                import de.cuioss.tools.logging.CuiLogger;
                
                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    
                    void method(Exception e) {
                        /*~~>*/LOGGER.error("Error occurred", e);
                    }
                }
                """
            )
        );
    }
    
    @Test
    void detectSystemOutUsage() {
        rewriteRun(
            java(
                """
                class Test {
                    void method() {
                        System.out.println("Should not use System.out");
                        System.err.println("Should not use System.err");
                    }
                }
                """,
                """
                class Test {
                    void method() {
                        /*~~>*/System.out.println("Should not use System.out");
                        /*~~>*/System.err.println("Should not use System.err");
                    }
                }
                """
            )
        );
    }
    
    @Test
    void suppressionWorks() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;
                
                class Test {
                    // cui-rewrite:disable
                    private static final CuiLogger log = new CuiLogger(Test.class);
                    
                    void method() {
                        // cui-rewrite:disable
                        System.out.println("This is suppressed");
                    }
                }
                """
            )
        );
    }
    
    @Test
    void correctLoggerUsageNotFlagged() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;
                
                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    
                    void method(Exception e) {
                        LOGGER.info("Correct message with %s", value);
                        LOGGER.error(e, "Error with %s", value);
                    }
                }
                """
            )
        );
    }
}