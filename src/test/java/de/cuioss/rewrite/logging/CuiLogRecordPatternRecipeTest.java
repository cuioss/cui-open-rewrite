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
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.List;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("java:S2699") // OpenRewrite tests use implicit assertions via the RewriteTest framework
class CuiLogRecordPatternRecipeTest implements RewriteTest {

    @Override public void defaults(RecipeSpec spec) {
        spec.recipe(new CuiLogRecordPatternRecipe())
            .parser(JavaParser.fromJavaVersion()
                .dependsOn(
                    """
                    package de.cuioss.tools.logging;
                    import java.util.function.Supplier;
                    public class CuiLogger {
                        public CuiLogger(Class<?> clazz) {}
                        public void trace(String message, Object... args) {}
                        public void debug(String message, Object... args) {}
                        public void info(String message, Object... args) {}
                        public void warn(String message, Object... args) {}
                        public void error(String message, Object... args) {}
                        public void fatal(String message, Object... args) {}
                        public void trace(Throwable t, String message, Object... args) {}
                        public void debug(Throwable t, String message, Object... args) {}
                        public void info(Throwable t, String message, Object... args) {}
                        public void warn(Throwable t, String message, Object... args) {}
                        public void error(Throwable t, String message, Object... args) {}
                        public void fatal(Throwable t, String message, Object... args) {}
                        public void trace(Supplier<String> message) {}
                        public void debug(Supplier<String> message) {}
                        public void info(Supplier<String> message) {}
                        public void warn(Supplier<String> message) {}
                        public void error(Supplier<String> message) {}
                        public void fatal(Supplier<String> message) {}
                        public void trace(Throwable t, Supplier<String> message) {}
                        public void debug(Throwable t, Supplier<String> message) {}
                        public void info(Throwable t, Supplier<String> message) {}
                        public void warn(Throwable t, Supplier<String> message) {}
                        public void error(Throwable t, Supplier<String> message) {}
                        public void fatal(Throwable t, Supplier<String> message) {}
                    }
                    """,
                    """
                    package de.cuioss.tools.logging;
                    public interface LogRecord {
                        String format(Object... args);
                    }
                    """,
                    """
                    package de.cuioss.tools.logging;
                    public class LogRecordModel implements LogRecord {
                        public String format(Object... args) { return ""; }
                        public static Builder builder() { return new Builder(); }
                        public static class Builder {
                            public Builder template(String template) { return this; }
                            public Builder prefix(String prefix) { return this; }
                            public Builder identifier(int id) { return this; }
                            public LogRecord build() { return new LogRecordModel(); }
                        }
                    }
                    """
                ));
    }

    @Test void detectMissingLogRecordForInfo() {
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
                        /*~~(TODO: INFO needs LogRecord)~~>*/LOGGER.info("User %s logged in", username);
                    }
                }
                """
            )
        );
    }

    @Test void detectMissingLogRecordForError() {
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
                        /*~~(TODO: ERROR needs LogRecord)~~>*/LOGGER.error(e, "Error occurred: %s", e.getMessage());
                    }
                }
                """
            )
        );
    }

    @Test void detectMissingLogRecordForWarn() {
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
                        /*~~(TODO: WARN needs LogRecord)~~>*/LOGGER.warn("Something is wrong");
                    }
                }
                """
            )
        );
    }

    @Test void detectLogRecordUsageInDebug() {
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
                        /*~~(TODO: DEBUG no LogRecord)~~>*/LOGGER.debug(DEBUG_MESSAGE.format("value"));
                    }
                }
                """
            )
        );
    }

    @Test void detectLogRecordUsageInTrace() {
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
                        /*~~(TODO: TRACE no LogRecord)~~>*/LOGGER.trace(TRACE_MESSAGE.format());
                    }
                }
                """
            )
        );
    }

    @Test void acceptCorrectLogRecordUsageForInfo() {
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
                """
            )
        );
    }

    @Test void detectZeroParamFormatCallForError() {
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
                        LOGGER.error(e, ERROR.DATABASE_ERROR::format);
                    }
                }
                """
            )
        );
    }

    @Test void acceptCorrectDebugWithoutLogRecord() {
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

    @Test void acceptCorrectTraceWithoutLogRecord() {
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

    @Test void suppressionWorks() {
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

    @Test void detectIncorrectPlaceholderInLogRecordTemplate() {
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

    @Test void acceptCorrectPlaceholderInLogRecordTemplate() {
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

    @Test void convertZeroParamFormatToMethodReference() {
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
                        LOGGER.info(INFO_MESSAGE::format);
                    }
                }
                """
            )
        );
    }

    @Test void convertZeroParamFormatWithExceptionToMethodReference() {
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
                        LOGGER.error(e, ERROR.DATABASE_ERROR::format);
                    }
                }
                """
            )
        );
    }

    @Test void doNotConvertFormatWithParameters() {
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
                """
            )
        );
    }

    @Test void acceptExistingMethodReference() {
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
                        LOGGER.info(INFO_MESSAGE::format);
                    }
                }
                """
            )
        );
    }

    @Test void shouldWorkWithSuppressionComment() {
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

    @Test void shouldSkipTestSources() {
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
}