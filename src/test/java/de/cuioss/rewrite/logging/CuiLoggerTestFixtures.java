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

/**
 * Shared source stubs for the CuiLogger API used by the logging recipe tests
 * via {@code JavaParser.dependsOn(...)}.
 * <p>
 * {@link #CUI_LOGGER_STUB} is a superset that satisfies every referencing test:
 * it declares the {@code String}- and {@code Supplier}-based logging methods, the
 * exception-first overloads, and the {@code LogRecord}-based overloads. Tests that
 * exercise the {@code LogRecord} pattern additionally depend on {@link #LOG_RECORD_STUB}
 * and {@link #LOG_RECORD_MODEL_STUB}.
 */
final class CuiLoggerTestFixtures {

    private CuiLoggerTestFixtures() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Superset stub for {@code de.cuioss.tools.logging.CuiLogger} covering all overloads
     * required by the logging recipe tests.
     */
    public static final String CUI_LOGGER_STUB =
        """
            package de.cuioss.tools.logging;
            import java.util.function.Supplier;
            public class CuiLogger {
                public CuiLogger(Class<?> clazz) {}
                public CuiLogger(String className) {}
                // String-based logging
                public void trace(String message, Object... args) {}
                public void debug(String message, Object... args) {}
                public void info(String message, Object... args) {}
                public void warn(String message, Object... args) {}
                public void error(String message, Object... args) {}
                public void fatal(String message, Object... args) {}
                // String-based logging with exception
                public void trace(Throwable t, String message, Object... args) {}
                public void debug(Throwable t, String message, Object... args) {}
                public void info(Throwable t, String message, Object... args) {}
                public void warn(Throwable t, String message, Object... args) {}
                public void error(Throwable t, String message, Object... args) {}
                public void fatal(Throwable t, String message, Object... args) {}
                // Supplier-based logging
                public void trace(Supplier<String> message) {}
                public void debug(Supplier<String> message) {}
                public void info(Supplier<String> message) {}
                public void warn(Supplier<String> message) {}
                public void error(Supplier<String> message) {}
                public void fatal(Supplier<String> message) {}
                // Supplier-based logging with exception
                public void trace(Throwable t, Supplier<String> message) {}
                public void debug(Throwable t, Supplier<String> message) {}
                public void info(Throwable t, Supplier<String> message) {}
                public void warn(Throwable t, Supplier<String> message) {}
                public void error(Throwable t, Supplier<String> message) {}
                public void fatal(Throwable t, Supplier<String> message) {}
                // LogRecord-based logging (new direct pattern)
                public void info(LogRecord logRecord, Object... args) {}
                public void warn(LogRecord logRecord, Object... args) {}
                public void error(LogRecord logRecord, Object... args) {}
                public void fatal(LogRecord logRecord, Object... args) {}
                // LogRecord-based logging with exception (new direct pattern)
                public void info(Throwable t, LogRecord logRecord, Object... args) {}
                public void warn(Throwable t, LogRecord logRecord, Object... args) {}
                public void error(Throwable t, LogRecord logRecord, Object... args) {}
                public void fatal(Throwable t, LogRecord logRecord, Object... args) {}
            }
            """;

    /**
     * Stub for {@code de.cuioss.tools.logging.CuiLoggerFactory}.
     */
    public static final String CUI_LOGGER_FACTORY_STUB =
        """
            package de.cuioss.tools.logging;
            public class CuiLoggerFactory {
                public static CuiLogger getLogger() { return null; }
                public static CuiLogger getLogger(Class<?> clazz) { return null; }
                public static CuiLogger getLogger(String className) { return null; }
            }
            """;

    /**
     * Stub for the {@code de.cuioss.tools.logging.LogRecord} interface referenced by
     * {@link #CUI_LOGGER_STUB}.
     */
    public static final String LOG_RECORD_STUB =
        """
            package de.cuioss.tools.logging;
            public interface LogRecord {
                String format(Object... args);
            }
            """;

    /**
     * Stub for {@code de.cuioss.tools.logging.LogRecordModel} and its fluent builder.
     */
    public static final String LOG_RECORD_MODEL_STUB =
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
            """;
}
