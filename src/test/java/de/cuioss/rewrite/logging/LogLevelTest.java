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

import de.cuioss.rewrite.logging.CuiLogRecordPatternRecipe.CuiLogRecordPatternVisitor.LogLevel;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the LogLevel enum to achieve full coverage.
 */
class LogLevelTest {

    @Test
    void shouldParseValidLogLevels() {
        assertEquals(Optional.of(LogLevel.TRACE), LogLevel.fromMethodName("trace"));
        assertEquals(Optional.of(LogLevel.DEBUG), LogLevel.fromMethodName("debug"));
        assertEquals(Optional.of(LogLevel.INFO), LogLevel.fromMethodName("info"));
        assertEquals(Optional.of(LogLevel.WARN), LogLevel.fromMethodName("warn"));
        assertEquals(Optional.of(LogLevel.ERROR), LogLevel.fromMethodName("error"));
        assertEquals(Optional.of(LogLevel.FATAL), LogLevel.fromMethodName("fatal"));
    }

    @Test
    void shouldParseUpperCaseLogLevels() {
        assertEquals(Optional.of(LogLevel.INFO), LogLevel.fromMethodName("INFO"));
        assertEquals(Optional.of(LogLevel.ERROR), LogLevel.fromMethodName("ERROR"));
    }

    @Test
    void shouldParseMixedCaseLogLevels() {
        assertEquals(Optional.of(LogLevel.INFO), LogLevel.fromMethodName("InFo"));
        assertEquals(Optional.of(LogLevel.WARN), LogLevel.fromMethodName("WaRn"));
    }

    @Test
    void shouldReturnEmptyForInvalidMethodNames() {
        // This tests the IllegalArgumentException catch block
        assertEquals(Optional.empty(), LogLevel.fromMethodName("notALogLevel"));
        assertEquals(Optional.empty(), LogLevel.fromMethodName("flush"));
        assertEquals(Optional.empty(), LogLevel.fromMethodName("getName"));
        assertEquals(Optional.empty(), LogLevel.fromMethodName(""));
        assertEquals(Optional.empty(), LogLevel.fromMethodName("invalid"));
    }
}
