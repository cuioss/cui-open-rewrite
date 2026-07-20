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

import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.openrewrite.java.Assertions.java;

// cui-rewrite:disable CuiLogRecordPatternRecipe
@EnableTestLogger
@SuppressWarnings("java:S2699") // OpenRewrite tests use implicit assertions via the RewriteTest framework
class CuiLogRecordPatternRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CuiLogRecordPatternRecipe())
            .parser(JavaParser.fromJavaVersion()
                .dependsOn(
                    CuiLoggerTestFixtures.CUI_LOGGER_STUB,
                    CuiLoggerTestFixtures.CUI_LOGGER_FACTORY_STUB,
                    CuiLoggerTestFixtures.LOG_RECORD_STUB,
                    CuiLoggerTestFixtures.LOG_RECORD_MODEL_STUB
                ));
    }

    @Test
    void detectMissingLogRecordForInfo() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;

                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);

                    void method() {
                        String username = "john";
                        LOGGER.info("User %s logged in", username);
                    }
                }
                """,
                """
                import de.cuioss.tools.logging.CuiLogger;

                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);

                    void method() {
                        String username = "john";
                        /*~~(TODO: INFO needs LogRecord. Suppress: // cui-rewrite:disable CuiLogRecordPatternRecipe)~~>*/LOGGER.info("User %s logged in", username);
                    }
                }
                """
            )
        );
    }

    @Test
    void detectMissingLogRecordForError() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;

                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);

                    void method(Exception e) {
                        LOGGER.error(e, "Error occurred: %s", e.getMessage());
                    }
                }
                """,
                """
                import de.cuioss.tools.logging.CuiLogger;

                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);

                    void method(Exception e) {
                        /*~~(TODO: ERROR needs LogRecord. Suppress: // cui-rewrite:disable CuiLogRecordPatternRecipe)~~>*/LOGGER.error(e, "Error occurred: %s", e.getMessage());
                    }
                }
                """
            )
        );
    }

    @Test
    void detectMissingLogRecordForWarn() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;

                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);

                    void method() {
                        LOGGER.warn("Something is wrong");
                    }
                }
                """,
                """
                import de.cuioss.tools.logging.CuiLogger;

                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);

                    void method() {
                        /*~~(TODO: WARN needs LogRecord. Suppress: // cui-rewrite:disable CuiLogRecordPatternRecipe)~~>*/LOGGER.warn("Something is wrong");
                    }
                }
                """
            )
        );
    }

    @Test
    void detectLogRecordUsageInDebug() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;
                import de.cuioss.tools.logging.LogRecord;
                import de.cuioss.tools.logging.LogRecordModel;

                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    private static final LogRecord DEBUG_MESSAGE = LogRecordModel.builder()
                        .template("Debug: %s")
                        .build();

                    void method() {
                        LOGGER.debug(DEBUG_MESSAGE.format("value"));
                    }
                }
                """,
                """
                import de.cuioss.tools.logging.CuiLogger;
                import de.cuioss.tools.logging.LogRecord;
                import de.cuioss.tools.logging.LogRecordModel;

                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    private static final LogRecord DEBUG_MESSAGE = LogRecordModel.builder()
                        .template("Debug: %s")
                        .build();

                    void method() {
                        /*~~(TODO: DEBUG no LogRecord. Suppress: // cui-rewrite:disable CuiLogRecordPatternRecipe)~~>*/LOGGER.debug(DEBUG_MESSAGE.format("value"));
                    }
                }
                """
            )
        );
    }

    @Test
    void detectLogRecordUsageInTrace() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;
                import de.cuioss.tools.logging.LogRecord;
                import de.cuioss.tools.logging.LogRecordModel;

                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    private static final LogRecord TRACE_MESSAGE = LogRecordModel.builder()
                        .template("Trace: %s")
                        .build();

                    void method() {
                        LOGGER.trace(TRACE_MESSAGE.format());
                    }
                }
                """,
                """
                import de.cuioss.tools.logging.CuiLogger;
                import de.cuioss.tools.logging.LogRecord;
                import de.cuioss.tools.logging.LogRecordModel;

                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    private static final LogRecord TRACE_MESSAGE = LogRecordModel.builder()
                        .template("Trace: %s")
                        .build();

                    void method() {
                        /*~~(TODO: TRACE no LogRecord. Suppress: // cui-rewrite:disable CuiLogRecordPatternRecipe)~~>*/LOGGER.trace(TRACE_MESSAGE.format());
                    }
                }
                """
            )
        );
    }

    @Test
    void transformFormatCallWithParameterToDirectLogRecord() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;
                import de.cuioss.tools.logging.LogRecord;
                import de.cuioss.tools.logging.LogRecordModel;

                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);

                    static class INFO {
                        static final LogRecord USER_LOGIN = LogRecordModel.builder()
                            .template("User %s logged in")
                            .build();
                    }

                    void method() {
                        String username = "john";
                        LOGGER.info(INFO.USER_LOGIN.format(username));
                    }
                }
                """,
                """
                import de.cuioss.tools.logging.CuiLogger;
                import de.cuioss.tools.logging.LogRecord;
                import de.cuioss.tools.logging.LogRecordModel;

                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);

                    static class INFO {
                        static final LogRecord USER_LOGIN = LogRecordModel.builder()
                            .template("User %s logged in")
                            .build();
                    }

                    void method() {
                        String username = "john";
                        LOGGER.info(INFO.USER_LOGIN, username);
                    }
                }
                """
            )
        );
    }

    @Test
    void transformZeroParamFormatCallToDirectLogRecord() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;
                import de.cuioss.tools.logging.LogRecord;
                import de.cuioss.tools.logging.LogRecordModel;

                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);

                    static class ERROR {
                        static final LogRecord DATABASE_ERROR = LogRecordModel.builder()
                            .template("Database error occurred")
                            .build();
                    }

                    void method(Exception e) {
                        LOGGER.error(e, ERROR.DATABASE_ERROR.format());
                    }
                }
                """,
                """
                import de.cuioss.tools.logging.CuiLogger;
                import de.cuioss.tools.logging.LogRecord;
                import de.cuioss.tools.logging.LogRecordModel;

                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);

                    static class ERROR {
                        static final LogRecord DATABASE_ERROR = LogRecordModel.builder()
                            .template("Database error occurred")
                            .build();
                    }

                    void method(Exception e) {
                        LOGGER.error(e, ERROR.DATABASE_ERROR);
                    }
                }
                """
            )
        );
    }

    @Test
    void acceptCorrectDebugWithoutLogRecord() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;

                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);

                    void method() {
                        String value = "test";
                        LOGGER.debug("Processing value: %s", value);
                    }
                }
                """
            )
        );
    }

    @Test
    void acceptCorrectTraceWithoutLogRecord() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;

                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);

                    void method(Exception e) {
                        LOGGER.trace(e, "Detailed trace: %s", e.getMessage());
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
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);

                    void method() {
                        // cui-rewrite:disable
                        LOGGER.info("Direct logging is suppressed");
                    }
                }
                """
            )
        );
    }

    @Test
    void detectIncorrectPlaceholderInLogRecordTemplate() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.LogRecord;
                import de.cuioss.tools.logging.LogRecordModel;

                class Test {
                    static class INFO {
                        static final LogRecord USER_LOGIN = LogRecordModel.builder()
                            .template("User {} logged in with ID: %d")
                            .prefix("TEST")
                            .identifier(1)
                            .build();
                    }
                }
                """,
                """
                import de.cuioss.tools.logging.LogRecord;
                import de.cuioss.tools.logging.LogRecordModel;

                class Test {
                    static class INFO {
                        static final LogRecord USER_LOGIN = LogRecordModel.builder()
                            .template("User %s logged in with ID: %s")
                            .prefix("TEST")
                            .identifier(1)
                            .build();
                    }
                }
                """
            )
        );
    }

    @Test
    void acceptCorrectPlaceholderInLogRecordTemplate() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.LogRecord;
                import de.cuioss.tools.logging.LogRecordModel;

                class Test {
                    static class INFO {
                        static final LogRecord USER_LOGIN = LogRecordModel.builder()
                            .template("User %s logged in with ID: %s")
                            .prefix("TEST")
                            .identifier(1)
                            .build();
                    }
                }
                """
            )
        );
    }

    @Test
    void transformZeroParamFormatToDirectLogRecordWithoutException() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;
                import de.cuioss.tools.logging.LogRecord;
                import de.cuioss.tools.logging.LogRecordModel;

                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    private static final LogRecord INFO_MESSAGE = LogRecordModel.builder()
                        .template("Simple info message")
                        .build();

                    void method() {
                        LOGGER.info(INFO_MESSAGE.format());
                    }
                }
                """,
                """
                import de.cuioss.tools.logging.CuiLogger;
                import de.cuioss.tools.logging.LogRecord;
                import de.cuioss.tools.logging.LogRecordModel;

                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    private static final LogRecord INFO_MESSAGE = LogRecordModel.builder()
                        .template("Simple info message")
                        .build();

                    void method() {
                        LOGGER.info(INFO_MESSAGE);
                    }
                }
                """
            )
        );
    }

    @Test
    void transformZeroParamFormatWithExceptionToDirectLogRecord() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;
                import de.cuioss.tools.logging.LogRecord;
                import de.cuioss.tools.logging.LogRecordModel;

                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);

                    static class ERROR {
                        static final LogRecord DATABASE_ERROR = LogRecordModel.builder()
                            .template("Database connection failed")
                            .build();
                    }

                    void method(Exception e) {
                        LOGGER.error(e, ERROR.DATABASE_ERROR.format());
                    }
                }
                """,
                """
                import de.cuioss.tools.logging.CuiLogger;
                import de.cuioss.tools.logging.LogRecord;
                import de.cuioss.tools.logging.LogRecordModel;

                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);

                    static class ERROR {
                        static final LogRecord DATABASE_ERROR = LogRecordModel.builder()
                            .template("Database connection failed")
                            .build();
                    }

                    void method(Exception e) {
                        LOGGER.error(e, ERROR.DATABASE_ERROR);
                    }
                }
                """
            )
        );
    }

    @Test
    void transformFormatWithMultipleParametersToDirectLogRecord() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;
                import de.cuioss.tools.logging.LogRecord;
                import de.cuioss.tools.logging.LogRecordModel;

                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);

                    static class WARN {
                        static final LogRecord FIELD_READ_FAILED = LogRecordModel.builder()
                            .template("Field %s read failed: %s, %s")
                            .build();
                    }

                    void method(Exception e) {
                        String field = "name";
                        boolean initialAccessible = true;
                        Object source = this;
                        LOGGER.warn(e, WARN.FIELD_READ_FAILED.format(field, initialAccessible, source));
                    }
                }
                """,
                """
                import de.cuioss.tools.logging.CuiLogger;
                import de.cuioss.tools.logging.LogRecord;
                import de.cuioss.tools.logging.LogRecordModel;

                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);

                    static class WARN {
                        static final LogRecord FIELD_READ_FAILED = LogRecordModel.builder()
                            .template("Field %s read failed: %s, %s")
                            .build();
                    }

                    void method(Exception e) {
                        String field = "name";
                        boolean initialAccessible = true;
                        Object source = this;
                        LOGGER.warn(e, WARN.FIELD_READ_FAILED, field, initialAccessible, source);
                    }
                }
                """
            )
        );
    }

    @Test
    void acceptDirectLogRecordWithoutParameters() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;
                import de.cuioss.tools.logging.LogRecord;
                import de.cuioss.tools.logging.LogRecordModel;

                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    private static final LogRecord INFO_MESSAGE = LogRecordModel.builder()
                        .template("Application started")
                        .build();

                    void method() {
                        LOGGER.info(INFO_MESSAGE);
                    }
                }
                """
            )
        );
    }

    @Test
    void acceptDirectLogRecordWithParameters() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;
                import de.cuioss.tools.logging.LogRecord;
                import de.cuioss.tools.logging.LogRecordModel;

                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);

                    static class INFO {
                        static final LogRecord USER_LOGIN = LogRecordModel.builder()
                            .template("User %s logged in")
                            .build();
                    }

                    void method() {
                        String username = "john";
                        LOGGER.info(INFO.USER_LOGIN, username);
                    }
                }
                """
            )
        );
    }

    @Test
    void acceptDirectLogRecordWithExceptionAndParameters() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;
                import de.cuioss.tools.logging.LogRecord;
                import de.cuioss.tools.logging.LogRecordModel;

                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);

                    static class ERROR {
                        static final LogRecord PROPERTY_WRITE_FAILED = LogRecordModel.builder()
                            .template("Property %s write failed for %s")
                            .build();
                    }

                    void method(Exception exception) {
                        String name = "username";
                        String className = "UserClass";
                        LOGGER.error(exception, ERROR.PROPERTY_WRITE_FAILED, name, className);
                    }
                }
                """
            )
        );
    }

    @Test
    void shouldWorkWithSuppressionComment() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;

                public class Example {
                    private static final CuiLogger LOGGER = new CuiLogger(Example.class);

                    public void test() {
                        // cui-rewrite:disable
                        LOGGER.info("Message without LogRecord");
                    }
                }
                """
            )
        );
    }

    @Test
    void shouldSkipTestSources() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;

                public class MyTest {
                    private static final CuiLogger LOGGER = new CuiLogger(MyTest.class);

                    void testMethod() {
                        // This would normally trigger the recipe, but should be skipped for test sources
                        LOGGER.info("Test message without LogRecord");
                    }
                }
                """,
                s -> s.path("src/test/java/MyTest.java")
                    .markers(JavaSourceSet.build("test", List.of()))
            )
        );
    }

    /**
     * Issue #1: Method reference ::format should be transformed to direct LogRecord usage
     * LOGGER.info(INFO.GENERATING_REPORTS::format) -> LOGGER.info(INFO.GENERATING_REPORTS)
     */
    @Test
    void shouldTransformMethodReferenceFormatToDirectLogRecord() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;
                import de.cuioss.tools.logging.LogRecord;

                class ReportGenerator {
                    private static final CuiLogger LOGGER = new CuiLogger(ReportGenerator.class);

                    static class INFO {
                        static final LogRecord GENERATING_REPORTS = null;
                    }

                    void generateDetailedPage() {
                        LOGGER.info(INFO.GENERATING_REPORTS::format);
                    }
                }
                """,
                """
                import de.cuioss.tools.logging.CuiLogger;
                import de.cuioss.tools.logging.LogRecord;

                class ReportGenerator {
                    private static final CuiLogger LOGGER = new CuiLogger(ReportGenerator.class);

                    static class INFO {
                        static final LogRecord GENERATING_REPORTS = null;
                    }

                    void generateDetailedPage() {
                        LOGGER.info(INFO.GENERATING_REPORTS);
                    }
                }
                """
            )
        );
    }

    /**
     * Issue #2: Method reference ::format with exception should be transformed
     * LOGGER.error(e, ERROR.WRK_PROCESSOR_FAILED::format) -> LOGGER.error(e, ERROR.WRK_PROCESSOR_FAILED)
     */
    @Test
    void shouldTransformMethodReferenceFormatWithExceptionToDirectLogRecord() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;
                import de.cuioss.tools.logging.LogRecord;
                import java.io.IOException;

                class WrkResultPostProcessor {
                    private static final CuiLogger LOGGER = new CuiLogger(WrkResultPostProcessor.class);

                    static class ERROR {
                        static final LogRecord WRK_PROCESSOR_FAILED = null;
                    }

                    void process() {
                        try {
                            // some code
                        } catch (IOException e) {
                            LOGGER.error(e, ERROR.WRK_PROCESSOR_FAILED::format);
                        }
                    }
                }
                """,
                """
                import de.cuioss.tools.logging.CuiLogger;
                import de.cuioss.tools.logging.LogRecord;
                import java.io.IOException;

                class WrkResultPostProcessor {
                    private static final CuiLogger LOGGER = new CuiLogger(WrkResultPostProcessor.class);

                    static class ERROR {
                        static final LogRecord WRK_PROCESSOR_FAILED = null;
                    }

                    void process() {
                        try {
                            // some code
                        } catch (IOException e) {
                            LOGGER.error(e, ERROR.WRK_PROCESSOR_FAILED);
                        }
                    }
                }
                """
            )
        );
    }

    /**
     * Issue #3: String concatenation with LogRecord should be flagged as a bug
     * LOGGER.info(INFO.GENERATING_REPORTS, "text" + variable) is ALWAYS wrong
     */
    @Test
    void shouldFlagStringConcatenationWithLogRecordAsBug() {
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;
                import de.cuioss.tools.logging.LogRecord;

                class BadgeGenerator {
                    private static final CuiLogger LOGGER = new CuiLogger(BadgeGenerator.class);

                    static class INFO {
                        static final LogRecord GENERATING_REPORTS = null;
                    }

                    void generateBadges(String perfBadgePath) {
                        LOGGER.info(INFO.GENERATING_REPORTS, "Performance badge written to " + perfBadgePath);
                    }
                }
                """,
                """
                import de.cuioss.tools.logging.CuiLogger;
                import de.cuioss.tools.logging.LogRecord;

                class BadgeGenerator {
                    private static final CuiLogger LOGGER = new CuiLogger(BadgeGenerator.class);

                    static class INFO {
                        static final LogRecord GENERATING_REPORTS = null;
                    }

                    void generateBadges(String perfBadgePath) {
                        /*~~(TODO: String concatenation with LogRecord parameter is always wrong. Use separate parameters instead. Suppress: // cui-rewrite:disable CuiLogRecordPatternRecipe)~~>*/LOGGER.info(INFO.GENERATING_REPORTS, "Performance badge written to " + perfBadgePath);
                    }
                }
                """
            )
        );
    }

    @Test
    void shouldFlagNestedStringConcatenationWithLogRecord() {
        // Tests recursive checking in containsStringConcatenation()
        // String concat is buried in a nested expression: count + ("text" + name)
        // This exercises the recursive path at lines 461-462
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;
                import de.cuioss.tools.logging.LogRecord;

                class Example {
                    private static final CuiLogger LOGGER = new CuiLogger(Example.class);

                    static class INFO {
                        static final LogRecord MESSAGE = null;
                    }

                    void logWithNestedExpression(int count, String name) {
                        // Nested expression: outer binary is int+obj, inner binary is string concat
                        LOGGER.info(INFO.MESSAGE, count + ("prefix" + name));
                    }
                }
                """,
                """
                      import de.cuioss.tools.logging.CuiLogger;
                      import de.cuioss.tools.logging.LogRecord;

                      class Example {
                          private static final CuiLogger LOGGER = new CuiLogger(Example.class);

                          static class INFO {
                              static final LogRecord MESSAGE = null;
                          }

                          void logWithNestedExpression(int count, String name) {
                              // Nested expression: outer binary is int+obj, inner binary is string concat
                              /*~~(TODO: String concatenation with LogRecord parameter is always wrong. Use separate parameters instead. Suppress: // cui-rewrite:disable CuiLogRecordPatternRecipe)~~>*/LOGGER.info(INFO.MESSAGE, count + ("prefix" + name));
                          }
                      }
                """
            )
        );
    }

    @Test
    void shouldIgnoreTemplateCallOnNonLogRecordBuilder() {
        // Corner case: .template() method on a different class (not LogRecordModel.Builder)
        // Should be ignored by the recipe
        rewriteRun(
            java(
                """
                class Example {
                    interface TemplateBuilder {
                        void template(String s);
                    }

                    void doSomething(TemplateBuilder builder) {
                        builder.template("Some text");
                    }
                }
                """
            )
        );
    }

    @Test
    void shouldIgnoreTemplateCallWithWrongArgumentCount() {
        // Corner case: .template() with 0 or 2+ arguments
        // Should be ignored by the recipe
        rewriteRun(
            java(
                """
                import de.cuioss.tools.logging.LogRecordModel;

                class Example {
                    void doSomething() {
                        // This would be a compile error, but recipe should handle gracefully
                        LogRecordModel.builder().build();
                    }
                }
                """
            )
        );
    }

    /**
     * Regression scenario: the recipe must not crash when encountering a {@code getName()}
     * method invocation that is unrelated to logging.
     */
    @Test
    void shouldNotFailOnGetNameMethod() {
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

    /**
     * Regression scenario: a local {@code getName()} call must not be treated as a log level,
     * while a genuine {@code LOG.info(...)} call is still flagged.
     */
    @Test
    void shouldNotFailOnNonLoggerMethods() {
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
                        /*~~(TODO: INFO needs LogRecord. Suppress: // cui-rewrite:disable CuiLogRecordPatternRecipe)~~>*/LOG.info("Test message"); // This should be processed but shouldn't fail
                    }

                    private String getName() {
                        return "test";
                    }
                }
                """
            )
        );
    }

    // --- WARN build-log visibility (issue #116) ---

    @Test
    void shouldLogWarnForMissingLogRecordNewAndPreExisting() {
        rewriteRun(
            spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;

                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);

                    void method() {
                        String username = "john";
                        LOGGER.info("User %s logged in", username);
                    }
                }
                """,
                """
                import de.cuioss.tools.logging.CuiLogger;

                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);

                    void method() {
                        String username = "john";
                        /*~~(TODO: INFO needs LogRecord. Suppress: // cui-rewrite:disable CuiLogRecordPatternRecipe)~~>*/LOGGER.info("User %s logged in", username);
                    }
                }
                """
            )
        );

        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "Finding detected at");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "Finding pre-existing at");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
            "by CuiLogRecordPatternRecipe: TODO: INFO needs LogRecord");
    }

    @Test
    void shouldLogWarnForForbiddenLogRecordNewAndPreExisting() {
        rewriteRun(
            spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;
                import de.cuioss.tools.logging.LogRecord;
                import de.cuioss.tools.logging.LogRecordModel;

                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    private static final LogRecord DEBUG_MESSAGE = LogRecordModel.builder()
                        .template("Debug: %s")
                        .build();

                    void method() {
                        LOGGER.debug(DEBUG_MESSAGE.format("value"));
                    }
                }
                """,
                """
                import de.cuioss.tools.logging.CuiLogger;
                import de.cuioss.tools.logging.LogRecord;
                import de.cuioss.tools.logging.LogRecordModel;

                class Test {
                    private static final CuiLogger LOGGER = new CuiLogger(Test.class);
                    private static final LogRecord DEBUG_MESSAGE = LogRecordModel.builder()
                        .template("Debug: %s")
                        .build();

                    void method() {
                        /*~~(TODO: DEBUG no LogRecord. Suppress: // cui-rewrite:disable CuiLogRecordPatternRecipe)~~>*/LOGGER.debug(DEBUG_MESSAGE.format("value"));
                    }
                }
                """
            )
        );

        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "Finding detected at");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "Finding pre-existing at");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
            "by CuiLogRecordPatternRecipe: TODO: DEBUG no LogRecord");
    }

    @Test
    void shouldLogWarnForStringConcatenationNewAndPreExisting() {
        rewriteRun(
            spec -> spec.cycles(2).expectedCyclesThatMakeChanges(1),
            java(
                """
                import de.cuioss.tools.logging.CuiLogger;
                import de.cuioss.tools.logging.LogRecord;

                class BadgeGenerator {
                    private static final CuiLogger LOGGER = new CuiLogger(BadgeGenerator.class);

                    static class INFO {
                        static final LogRecord GENERATING_REPORTS = null;
                    }

                    void generateBadges(String perfBadgePath) {
                        LOGGER.info(INFO.GENERATING_REPORTS, "Performance badge written to " + perfBadgePath);
                    }
                }
                """,
                """
                import de.cuioss.tools.logging.CuiLogger;
                import de.cuioss.tools.logging.LogRecord;

                class BadgeGenerator {
                    private static final CuiLogger LOGGER = new CuiLogger(BadgeGenerator.class);

                    static class INFO {
                        static final LogRecord GENERATING_REPORTS = null;
                    }

                    void generateBadges(String perfBadgePath) {
                        /*~~(TODO: String concatenation with LogRecord parameter is always wrong. Use separate parameters instead. Suppress: // cui-rewrite:disable CuiLogRecordPatternRecipe)~~>*/LOGGER.info(INFO.GENERATING_REPORTS, "Performance badge written to " + perfBadgePath);
                    }
                }
                """
            )
        );

        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "Finding detected at");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "Finding pre-existing at");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
            "by CuiLogRecordPatternRecipe: TODO: String concatenation with LogRecord parameter is always wrong");
    }
}