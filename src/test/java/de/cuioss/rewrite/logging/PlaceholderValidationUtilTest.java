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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceholderValidationUtilTest {

    static Stream<Arguments> incorrectPlaceholderCases() {
        return Stream.of(
            // message, expectedIncorrect
            Arguments.of("", false),
            Arguments.of("Simple message", false),
            Arguments.of("Message with %s placeholder", false),
            Arguments.of("%s %s multiple", false),
            Arguments.of("100%% done", false),
            Arguments.of("50%%d escaped", false),
            Arguments.of("Message with {} placeholder", true),
            Arguments.of("{} {} multiple", true),
            Arguments.of("Integer %d placeholder", true),
            Arguments.of("Float %f placeholder", true),
            Arguments.of("Boolean %b placeholder", true),
            Arguments.of("Hex %x placeholder", true),
            Arguments.of("Scientific %e placeholder", true),
            Arguments.of("Mixed %s and {} placeholders", true),
            Arguments.of("%s correct but %d incorrect", true),
            // width / precision / flags / index / line-separator forms (previously missed)
            Arguments.of("Width %5d", true),
            Arguments.of("Precision %.2f", true),
            Arguments.of("Padded %08x", true),
            Arguments.of("Grouped %,d", true),
            Arguments.of("Indexed %1$s", true),
            Arguments.of("Newline %n", true)
        );
    }

    @ParameterizedTest
    @MethodSource("incorrectPlaceholderCases")
    void hasIncorrectPlaceholders(String message, boolean expectedIncorrect) {
        assertEquals(expectedIncorrect, PlaceholderValidationUtil.hasIncorrectPlaceholders(message));
    }

    @Test
    void hasIncorrectPlaceholdersNull() {
        assertFalse(PlaceholderValidationUtil.hasIncorrectPlaceholders(null));
    }

    static Stream<Arguments> correctPlaceholderCases() {
        return Stream.of(
            // input, expected
            Arguments.of("", ""),
            Arguments.of("Simple message without placeholders", "Simple message without placeholders"),
            Arguments.of("Message with %s placeholder", "Message with %s placeholder"),
            Arguments.of("Message with {} placeholder", "Message with %s placeholder"),
            Arguments.of("{} {} multiple", "%s %s multiple"),
            Arguments.of("Integer %d placeholder", "Integer %s placeholder"),
            Arguments.of("Float %f placeholder", "Float %s placeholder"),
            Arguments.of("Hex %x placeholder", "Hex %s placeholder"),
            Arguments.of("HEX %X placeholder", "HEX %s placeholder"),
            Arguments.of("Octal %o placeholder", "Octal %s placeholder"),
            Arguments.of("Mixed %s and {} placeholders", "Mixed %s and %s placeholders"),
            Arguments.of("%s correct and %d also correct", "%s correct and %s also correct"),
            Arguments.of("{} %d %f all converted", "%s %s %s all converted"),
            // width / precision / flags / index / line-separator forms
            Arguments.of("Width %5d", "Width %s"),
            Arguments.of("Precision %.2f", "Precision %s"),
            Arguments.of("Padded %08x", "Padded %s"),
            Arguments.of("Grouped %,d", "Grouped %s"),
            Arguments.of("Indexed %1$s", "Indexed %s"),
            Arguments.of("Newline %n", "Newline %s"),
            // %% escape must be preserved, not corrupted
            Arguments.of("100%% done", "100%% done"),
            Arguments.of("50%%d escaped", "50%%d escaped")
        );
    }

    @ParameterizedTest
    @MethodSource("correctPlaceholderCases")
    void correctPlaceholders(String input, String expected) {
        assertEquals(expected, PlaceholderValidationUtil.correctPlaceholders(input));
    }

    @Test
    void correctPlaceholdersNull() {
        assertNull(PlaceholderValidationUtil.correctPlaceholders(null));
    }

    static Stream<Arguments> countPlaceholderCases() {
        return Stream.of(
            // message, expectedCount
            Arguments.of("", 0),
            Arguments.of("Simple message", 0),
            Arguments.of("Message with %s", 1),
            Arguments.of("%s and %s", 2),
            Arguments.of("%s %s %s", 3),
            Arguments.of("%s%s", 2),
            Arguments.of("100%% complete with %s", 1),
            Arguments.of("100%% complete", 0),
            Arguments.of("User %s logged in at %s with 100%% success rate: %s", 3),
            // the s in an escaped percent must not be counted
            Arguments.of("%%s", 0),
            // an indexed directive is not a bare %s
            Arguments.of("%1$s %s", 1)
        );
    }

    @ParameterizedTest
    @MethodSource("countPlaceholderCases")
    void countPlaceholders(String message, int expectedCount) {
        assertEquals(expectedCount, PlaceholderValidationUtil.countPlaceholders(message));
    }

    @Test
    void countPlaceholdersNull() {
        assertEquals(0, PlaceholderValidationUtil.countPlaceholders(null));
    }
}
