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

import de.cuioss.rewrite.logging.CuiLogRecordPatternRecipe.CuiLogRecordPatternVisitor.LogLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the LogLevel enum to achieve full coverage.
 */
class LogLevelTest {

    @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
    @CsvSource({
        // lower-case method names
        "trace, TRACE",
        "debug, DEBUG",
        "info,  INFO",
        "warn,  WARN",
        "error, ERROR",
        "fatal, FATAL",
        // upper-case method names
        "INFO,  INFO",
        "ERROR, ERROR",
        // mixed-case method names
        "InFo,  INFO",
        "WaRn,  WARN"
    })
    void shouldParseLogLevelsCaseInsensitively(String methodName, LogLevel expected) {
        assertEquals(Optional.of(expected), LogLevel.fromMethodName(methodName));
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
