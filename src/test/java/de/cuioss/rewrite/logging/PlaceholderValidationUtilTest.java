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

import static org.junit.jupiter.api.Assertions.*;

class PlaceholderValidationUtilTest {

    @Test
    void hasIncorrectPlaceholdersNull() {
        assertFalse(PlaceholderValidationUtil.hasIncorrectPlaceholders(null));
    }

    @Test
    void hasIncorrectPlaceholdersEmptyString() {
        assertFalse(PlaceholderValidationUtil.hasIncorrectPlaceholders(""));
    }

    @Test
    void hasIncorrectPlaceholdersNoPlaceholders() {
        assertFalse(PlaceholderValidationUtil.hasIncorrectPlaceholders("Simple message"));
    }

    @Test
    void hasIncorrectPlaceholdersCorrectPlaceholders() {
        assertFalse(PlaceholderValidationUtil.hasIncorrectPlaceholders("Message with %s placeholder"));
        assertFalse(PlaceholderValidationUtil.hasIncorrectPlaceholders("%s %s multiple"));
    }

    @Test
    void hasIncorrectPlaceholdersIncorrectCurlyBraces() {
        assertTrue(PlaceholderValidationUtil.hasIncorrectPlaceholders("Message with {} placeholder"));
        assertTrue(PlaceholderValidationUtil.hasIncorrectPlaceholders("{} {} multiple"));
    }

    @Test
    void hasIncorrectPlaceholdersIncorrectFormatSpecifiers() {
        assertTrue(PlaceholderValidationUtil.hasIncorrectPlaceholders("Integer %d placeholder"));
        assertTrue(PlaceholderValidationUtil.hasIncorrectPlaceholders("Float %f placeholder"));
        assertTrue(PlaceholderValidationUtil.hasIncorrectPlaceholders("Boolean %b placeholder"));
        assertTrue(PlaceholderValidationUtil.hasIncorrectPlaceholders("Hex %x placeholder"));
        assertTrue(PlaceholderValidationUtil.hasIncorrectPlaceholders("Scientific %e placeholder"));
    }

    @Test
    void hasIncorrectPlaceholdersMixedPlaceholders() {
        assertTrue(PlaceholderValidationUtil.hasIncorrectPlaceholders("Mixed %s and {} placeholders"));
        assertTrue(PlaceholderValidationUtil.hasIncorrectPlaceholders("%s correct but %d incorrect"));
    }

    @Test
    void correctPlaceholdersNull() {
        assertNull(PlaceholderValidationUtil.correctPlaceholders(null));
    }

    @Test
    void correctPlaceholdersEmptyString() {
        assertEquals("", PlaceholderValidationUtil.correctPlaceholders(""));
    }

    @Test
    void correctPlaceholdersNoPlaceholders() {
        String message = "Simple message without placeholders";
        assertEquals(message, PlaceholderValidationUtil.correctPlaceholders(message));
    }

    @Test
    void correctPlaceholdersAlreadyCorrect() {
        String message = "Message with %s placeholder";
        assertEquals(message, PlaceholderValidationUtil.correctPlaceholders(message));
    }

    @Test
    void correctPlaceholdersReplaceCurlyBraces() {
        assertEquals("Message with %s placeholder",
            PlaceholderValidationUtil.correctPlaceholders("Message with {} placeholder"));
        assertEquals("%s %s multiple",
            PlaceholderValidationUtil.correctPlaceholders("{} {} multiple"));
    }

    @Test
    void correctPlaceholdersReplaceFormatSpecifiers() {
        assertEquals("Integer %s placeholder",
            PlaceholderValidationUtil.correctPlaceholders("Integer %d placeholder"));
        assertEquals("Float %s placeholder",
            PlaceholderValidationUtil.correctPlaceholders("Float %f placeholder"));
        assertEquals("Boolean %s placeholder",
            PlaceholderValidationUtil.correctPlaceholders("Boolean %b placeholder"));
        assertEquals("Hex %s placeholder",
            PlaceholderValidationUtil.correctPlaceholders("Hex %x placeholder"));
        assertEquals("HEX %s placeholder",
            PlaceholderValidationUtil.correctPlaceholders("HEX %X placeholder"));
        assertEquals("Scientific %s placeholder",
            PlaceholderValidationUtil.correctPlaceholders("Scientific %e placeholder"));
        assertEquals("SCIENTIFIC %s placeholder",
            PlaceholderValidationUtil.correctPlaceholders("SCIENTIFIC %E placeholder"));
        assertEquals("General %s placeholder",
            PlaceholderValidationUtil.correctPlaceholders("General %g placeholder"));
        assertEquals("GENERAL %s placeholder",
            PlaceholderValidationUtil.correctPlaceholders("GENERAL %G placeholder"));
        assertEquals("Integer %s placeholder",
            PlaceholderValidationUtil.correctPlaceholders("Integer %i placeholder"));
        assertEquals("Octal %s placeholder",
            PlaceholderValidationUtil.correctPlaceholders("Octal %o placeholder"));
    }

    @Test
    void correctPlaceholdersMixedPlaceholders() {
        assertEquals("Mixed %s and %s placeholders",
            PlaceholderValidationUtil.correctPlaceholders("Mixed %s and {} placeholders"));
        assertEquals("%s correct and %s also correct",
            PlaceholderValidationUtil.correctPlaceholders("%s correct and %d also correct"));
        assertEquals("%s %s %s all converted",
            PlaceholderValidationUtil.correctPlaceholders("{} %d %f all converted"));
    }

    @Test
    void countPlaceholdersNull() {
        assertEquals(0, PlaceholderValidationUtil.countPlaceholders(null));
    }

    @Test
    void countPlaceholdersEmptyString() {
        assertEquals(0, PlaceholderValidationUtil.countPlaceholders(""));
    }

    @Test
    void countPlaceholdersNoPlaceholders() {
        assertEquals(0, PlaceholderValidationUtil.countPlaceholders("Simple message"));
    }

    @Test
    void countPlaceholdersSinglePlaceholder() {
        assertEquals(1, PlaceholderValidationUtil.countPlaceholders("Message with %s"));
    }

    @Test
    void countPlaceholdersMultiplePlaceholders() {
        assertEquals(2, PlaceholderValidationUtil.countPlaceholders("%s and %s"));
        assertEquals(3, PlaceholderValidationUtil.countPlaceholders("%s %s %s"));
    }

    @Test
    void countPlaceholdersConsecutivePlaceholders() {
        assertEquals(2, PlaceholderValidationUtil.countPlaceholders("%s%s"));
    }

    @Test
    void countPlaceholdersEscapedPercent() {
        assertEquals(1, PlaceholderValidationUtil.countPlaceholders("100%% complete with %s"));
        assertEquals(0, PlaceholderValidationUtil.countPlaceholders("100%% complete"));
    }

    @Test
    void countPlaceholdersComplexPattern() {
        assertEquals(3, PlaceholderValidationUtil.countPlaceholders("User %s logged in at %s with 100%% success rate: %s"));
    }
}