/*
 * Copyright © 2022 CUI-OpenSource-Software (info@cuioss.de)
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

import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

// cui-rewrite:disable CuiLoggerStandardsRecipe
@EnableTestLogger
@SuppressWarnings("java:S2699") // OpenRewrite tests use implicit assertions via the RewriteTest framework
class CuiLoggerStandardsRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CuiLoggerStandardsRecipe())
            .typeValidationOptions(TypeValidation.none())
            .parser(JavaParser.fromJavaVersion()
                .dependsOn(
                    CuiLoggerTestFixtures.CUI_LOGGER_STUB,
                    CuiLoggerTestFixtures.CUI_LOGGER_FACTORY_STUB,
                    CuiLoggerTestFixtures.LOG_RECORD_STUB
                ));
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
                        private static final CuiLogger LOGGER = new CuiLogger(Test.class);
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
                        private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    }
                    """
            )
        );
    }

    @Test
    void detectIncorrectPlaceholderPatterns() {
        rewriteRun(
            java(
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    
                    class Test {
                        private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    
                        void method() {
                            String value = "test";
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
                            String value = "test";
                            LOGGER.info("Message with %s placeholder", value);
                            LOGGER.debug("Value is %s", 42);
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
                            String value1 = "test";
                            LOGGER.info("Message with %s and %s", value1);
                        }
                    }
                    """,
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    
                    class Test {
                        private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    
                        void method() {
                            String value1 = "test";
                            /*~~(TODO: 2 placeholders, 1 params. Suppress: // cui-rewrite:disable CuiLoggerStandardsRecipe)~~>*/LOGGER.info("Message with %s and %s", value1);
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
                            LOGGER.error(e, "Error occurred");
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
                            /*~~(TODO: Use CuiLogger. Suppress: // cui-rewrite:disable CuiLoggerStandardsRecipe)~~>*/System.out.println("Should not use System.out");
                            /*~~(TODO: Use CuiLogger. Suppress: // cui-rewrite:disable CuiLoggerStandardsRecipe)~~>*/System.err.println("Should not use System.err");
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
                            String value = "test";
                            LOGGER.info("Correct message with %s", value);
                            LOGGER.error(e, "Error with %s", value);
                        }
                    }
                    """
            )
        );
    }

    @Test
    void shouldHandleLoggerWithPublicModifier() {
        rewriteRun(
            java(
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    
                    public class Test {
                        public static final CuiLogger logger = new CuiLogger(Test.class);
                    }
                    """,
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    
                    public class Test {
                        private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    }
                    """
            )
        );
    }

    @Test
    void shouldHandleLoggerWithProtectedModifier() {
        rewriteRun(
            java(
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    
                    public class Test {
                        protected static CuiLogger LOGGER = new CuiLogger(Test.class);
                    }
                    """,
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    
                    public class Test {
                        private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    }
                    """
            )
        );
    }

    @Test
    void shouldHandleLoggerWithoutFinalModifier() {
        rewriteRun(
            java(
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    
                    public class Test {
                        private static CuiLogger LOGGER = new CuiLogger(Test.class);
                    }
                    """,
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    
                    public class Test {
                        private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    }
                    """
            )
        );
    }

    @Test
    void shouldHandleLoggerWithoutStaticModifier() {
        rewriteRun(
            java(
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    
                    public class Test {
                        private final CuiLogger LOGGER = new CuiLogger(Test.class);
                    }
                    """,
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    
                    public class Test {
                        private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    }
                    """
            )
        );
    }

    @Test
    void shouldHandleExceptionFirstWithPlaceholders() {
        rewriteRun(
            java(
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    
                    class Test {
                        private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    
                        void method(Exception e) {
                            String value = "test";
                            // When exception is first, message is second arg
                            // Should not flag this as parameter count mismatch
                            LOGGER.error(e, "Error with value: %s", value);
                        }
                    }
                    """
            )
        );
    }

    @Test
    void shouldDetectParameterMismatchWithExceptionFirst() {
        rewriteRun(
            java(
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    
                    class Test {
                        private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    
                        void method(Exception e) {
                            String value = "test";
                            // Exception first, then message with 2 placeholders but only 1 param
                            LOGGER.error(e, "Error with %s and %s", value);
                        }
                    }
                    """,
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    
                    class Test {
                        private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    
                        void method(Exception e) {
                            String value = "test";
                            // Exception first, then message with 2 placeholders but only 1 param
                            /*~~(TODO: 2 placeholders, 1 params. Suppress: // cui-rewrite:disable CuiLoggerStandardsRecipe)~~>*/LOGGER.error(e, "Error with %s and %s", value);
                        }
                    }
                    """
            )
        );
    }

    @Test
    void shouldNotModifyLocalVariableLoggers() {
        rewriteRun(
            java(
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    import de.cuioss.tools.logging.CuiLoggerFactory;
                    
                    public class TestClass {
                        // This field should be renamed and fixed
                        public CuiLogger log = new CuiLogger(TestClass.class);
                        
                        void testMethod() {
                            // Local variables should NOT be modified
                            CuiLogger localLogger = new CuiLogger(TestClass.class);
                            localLogger.info("Local logger message");
                            
                            // Even with factory pattern
                            CuiLogger factoryLogger = CuiLoggerFactory.getLogger(TestClass.class);
                            factoryLogger.debug("Factory logger message");
                            
                            // Even with wrong naming
                            final CuiLogger wrongName = new CuiLogger(TestClass.class);
                            wrongName.error("Wrong name but local");
                        }
                        
                        void anotherMethod() {
                            // Static-like local variables should also NOT be modified
                            final CuiLogger LOGGER = CuiLoggerFactory.getLogger();
                            LOGGER.warn("Warning from local");
                            
                            // Even with private static final-like declaration
                            final CuiLogger logger = new CuiLogger(TestClass.class);
                            logger.info("Info from local");
                        }
                    }
                    """,
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    import de.cuioss.tools.logging.CuiLoggerFactory;
                    
                    public class TestClass {
                        // This field should be renamed and fixed
                        private static final CuiLogger LOGGER = new CuiLogger(TestClass.class);
                        
                        void testMethod() {
                            // Local variables should NOT be modified
                            CuiLogger localLogger = new CuiLogger(TestClass.class);
                            localLogger.info("Local logger message");
                            
                            // Even with factory pattern
                            CuiLogger factoryLogger = CuiLoggerFactory.getLogger(TestClass.class);
                            factoryLogger.debug("Factory logger message");
                            
                            // Even with wrong naming
                            final CuiLogger wrongName = new CuiLogger(TestClass.class);
                            wrongName.error("Wrong name but local");
                        }
                        
                        void anotherMethod() {
                            // Static-like local variables should also NOT be modified
                            final CuiLogger LOGGER = CuiLoggerFactory.getLogger();
                            LOGGER.warn("Warning from local");
                            
                            // Even with private static final-like declaration
                            final CuiLogger logger = new CuiLogger(TestClass.class);
                            logger.info("Info from local");
                        }
                    }
                    """
            )
        );
    }

    @Test
    void shouldNotAddPrivateModifierToInterfaceField() {
        rewriteRun(
            java(
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    
                    public interface MyInterface {
                        CuiLogger LOGGER = new CuiLogger(MyInterface.class);
                    
                        default void doSomething() {
                            LOGGER.debug("hello");
                        }
                    }
                    """
            )
        );
    }

    @Test
    void shouldRenameLoggerInInterfaceButNotAddPrivate() {
        rewriteRun(
            java(
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    
                    public interface MyInterface {
                        CuiLogger log = new CuiLogger(MyInterface.class);
                    
                        default void doSomething() {
                            log.debug("hello");
                        }
                    }
                    """,
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    
                    public interface MyInterface {
                        CuiLogger LOGGER = new CuiLogger(MyInterface.class);
                    
                        default void doSomething() {
                            LOGGER.debug("hello");
                        }
                    }
                    """
            )
        );
    }

    @Test
    void shouldNotAddPrivateModifierToInterfaceFieldWithExplicitModifiers() {
        rewriteRun(
            java(
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    
                    public interface MyInterface {
                        public static final CuiLogger LOGGER = new CuiLogger(MyInterface.class);
                    }
                    """
            )
        );
    }

    @Test
    void shouldHandleLoggerWithNoModifiers() {
        rewriteRun(
            java(
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    
                    public class Example {
                        CuiLogger LOGGER = new CuiLogger(Example.class);
                    }
                    """,
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    
                    public class Example {
                        private static final CuiLogger LOGGER = new CuiLogger(Example.class);
                    }
                    """
            )
        );
    }

    @Test
    void shouldNotRenameConstructorInjectedLoggerWhenStandardLoggerExists() {
        rewriteRun(
            java(
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    
                    public class CuiRestClientBuilder {
                    
                        private static final CuiLogger LOGGER = new CuiLogger(CuiRestClientBuilder.class);
                        private final CuiLogger givenLogger;
                    
                        public CuiRestClientBuilder(final CuiLogger givenLogger) {
                            this.givenLogger = givenLogger;
                        }
                    
                        public void doSomething() {
                            givenLogger.debug("using injected logger");
                        }
                    }
                    """
            )
        );
    }

    @Test
    void shouldNotRenameConstructorInjectedLoggerWithoutStandardLogger() {
        rewriteRun(
            java(
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    
                    public class LogClientRequestFilter {
                    
                        private final CuiLogger logger;
                    
                        public LogClientRequestFilter(final CuiLogger logger) {
                            this.logger = logger;
                        }
                    
                        public void filter() {
                            logger.debug("filtering request");
                        }
                    }
                    """
            )
        );
    }

    @Test
    void shouldNotRenameInstanceLoggerFieldAssignedInConstructor() {
        rewriteRun(
            java(
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    
                    public class MyService {
                    
                        private final CuiLogger log;
                    
                        public MyService(final CuiLogger log) {
                            this.log = log;
                        }
                    
                        public void doWork() {
                            log.info("working");
                        }
                    }
                    """
            )
        );
    }

    @Test
    void shouldRenameQualifiedThisReference() {
        rewriteRun(
            java(
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    
                    class Test {
                        private static final CuiLogger log = new CuiLogger(Test.class);
                    
                        void method() {
                            this.log.info("hello");
                        }
                    }
                    """,
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    
                    class Test {
                        private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    
                        void method() {
                            this.LOGGER.info("hello");
                        }
                    }
                    """
            )
        );
    }

    @Test
    void shouldRenameNonInvocationReference() {
        rewriteRun(
            java(
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    
                    class Test {
                        private static final CuiLogger log = new CuiLogger(Test.class);
                    
                        CuiLogger expose() {
                            return log;
                        }
                    }
                    """,
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    
                    class Test {
                        private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    
                        CuiLogger expose() {
                            return LOGGER;
                        }
                    }
                    """
            )
        );
    }

    @Test
    void shouldNotRenameWhenLoggerNameCollisionExists() {
        rewriteRun(
            java(
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    
                    class Test {
                        private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                        private static final CuiLogger log = new CuiLogger(Test.class);
                    }
                    """,
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    
                    class Test {
                        private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                        /*~~(TODO: Rename logger to LOGGER (name already in use). Suppress: // cui-rewrite:disable CuiLoggerStandardsRecipe)~~>*/private static final CuiLogger log = new CuiLogger(Test.class);
                    }
                    """
            )
        );
    }

    @Test
    void shouldRenameFieldAndUnqualifiedReferenceInMethodBody() {
        rewriteRun(
            java(
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    
                    class Test {
                        private static final CuiLogger log = new CuiLogger(Test.class);
                    
                        void method() {
                            log.info("test");
                        }
                    }
                    """,
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    
                    class Test {
                        private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    
                        void method() {
                            LOGGER.info("test");
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
                        private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    
                        void method(Exception e) {
                            LOGGER.error(e, "Error %s with number %s", "message", 42);
                        }
                    }
                    """
            )
        );
    }

    // --- WARN build-log visibility (issue #116) ---

    @Test
    void shouldLogWarnForSystemStreamUsageNewAndPreExisting() {
        rewriteRun(
            spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
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
                            /*~~(TODO: Use CuiLogger. Suppress: // cui-rewrite:disable CuiLoggerStandardsRecipe)~~>*/System.out.println("Should not use System.out");
                            /*~~(TODO: Use CuiLogger. Suppress: // cui-rewrite:disable CuiLoggerStandardsRecipe)~~>*/System.err.println("Should not use System.err");
                        }
                    }
                    """
            )
        );

        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "Finding detected at");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "Finding pre-existing at");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
            "by CuiLoggerStandardsRecipe: TODO: Use CuiLogger");
    }

    @Test
    void shouldLogWarnForLoggerNameCollisionNewAndPreExisting() {
        rewriteRun(
            spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
            java(
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    
                    class Test {
                        private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                        private static final CuiLogger log = new CuiLogger(Test.class);
                    }
                    """,
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    
                    class Test {
                        private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                        /*~~(TODO: Rename logger to LOGGER (name already in use). Suppress: // cui-rewrite:disable CuiLoggerStandardsRecipe)~~>*/private static final CuiLogger log = new CuiLogger(Test.class);
                    }
                    """
            )
        );

        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "Finding detected at");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "Finding pre-existing at");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
            "by CuiLoggerStandardsRecipe: TODO: Rename logger to LOGGER (name already in use)");
    }

    @Test
    void shouldLogWarnForParameterCountMismatchNewAndPreExisting() {
        rewriteRun(
            spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
            java(
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    
                    class Test {
                        private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    
                        void method() {
                            String value1 = "test";
                            LOGGER.info("Message with %s and %s", value1);
                        }
                    }
                    """,
                """
                    import de.cuioss.tools.logging.CuiLogger;
                    
                    class Test {
                        private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    
                        void method() {
                            String value1 = "test";
                            /*~~(TODO: 2 placeholders, 1 params. Suppress: // cui-rewrite:disable CuiLoggerStandardsRecipe)~~>*/LOGGER.info("Message with %s and %s", value1);
                        }
                    }
                    """
            )
        );

        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "Finding detected at");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "Finding pre-existing at");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
            "by CuiLoggerStandardsRecipe: TODO: 2 placeholders, 1 params");
    }
}