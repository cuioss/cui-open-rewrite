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

class CuiLoggerAutoFixTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CuiLoggerStandardsRecipe())
            .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void fixesPublicModifierToPrivateStaticFinal() {
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
                    /*~~(Fixed logger modifiers to 'private static final')~~>*/private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                }
                """
            )
        );
    }

    @Test
    void fixesProtectedModifierToPrivateStaticFinal() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;
                
                class Test {
                    protected final CuiLogger LOGGER = new CuiLogger(Test.class);
                }
                """,
                """
                import de.cuioss.tools.logging.CuiLogger;
                
                class Test {
                    /*~~(Fixed logger modifiers to 'private static final')~~>*/private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                }
                """
            )
        );
    }

    @Test
    void replacesSLF4JPlaceholders() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;
                
                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    
                    void method() {
                        LOGGER.info("Message with {} placeholder", value);
                        LOGGER.debug("Multiple {} placeholders {}", value1, value2);
                    }
                }
                """,
                """
                import de.cuioss.tools.logging.CuiLogger;
                
                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    
                    void method() {
                        /*~~(Replaced incorrect placeholder pattern with %s)~~>*/LOGGER.info("Message with %s placeholder", value);
                        /*~~(Replaced incorrect placeholder pattern with %s)~~>*/LOGGER.debug("Multiple %s placeholders %s", value1, value2);
                    }
                }
                """
            )
        );
    }

    @Test
    void replacesPrintfStylePlaceholders() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;
                
                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    
                    void method() {
                        LOGGER.info("Integer %d, Float %f, Hex %x", intVal, floatVal, hexVal);
                        LOGGER.debug("String %s is correct", strVal);
                    }
                }
                """,
                """
                import de.cuioss.tools.logging.CuiLogger;
                
                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    
                    void method() {
                        /*~~(Replaced incorrect placeholder pattern with %s)~~>*/LOGGER.info("Integer %s, Float %s, Hex %s", intVal, floatVal, hexVal);
                        LOGGER.debug("String %s is correct", strVal);
                    }
                }
                """
            )
        );
    }

    @Test
    void movesExceptionToFirstPosition() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;
                
                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    
                    void method(Exception e) {
                        LOGGER.error("Error occurred with %s", value, e);
                        LOGGER.warn("Warning message", e);
                    }
                }
                """,
                """
                import de.cuioss.tools.logging.CuiLogger;
                
                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    
                    void method(Exception e) {
                        /*~~(Moved exception parameter to first position)~~>*/LOGGER.error(e, "Error occurred with %s", value);
                        /*~~(Moved exception parameter to first position)~~>*/LOGGER.warn(e, "Warning message");
                    }
                }
                """
            )
        );
    }

    @Test
    void combinedFixes() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;
                
                class Test {
                    public CuiLogger log = new CuiLogger(Test.class);
                    
                    void method(Exception e) {
                        log.error("Error {} with number %d", "message", 42, e);
                    }
                }
                """,
                """
                import de.cuioss.tools.logging.CuiLogger;
                
                class Test {
                    /*~~(Renamed logger field to 'LOGGER')~~>*//*~~(Fixed logger modifiers to 'private static final')~~>*/private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    
                    void method(Exception e) {
                        /*~~(Replaced incorrect placeholder pattern with %s)~~>*//*~~(Moved exception parameter to first position)~~>*/log.error(e, "Error %s with number %s", "message", 42);
                    }
                }
                """
            )
        );
    }

    @Test
    void doesNotChangeCorrectCode() {
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