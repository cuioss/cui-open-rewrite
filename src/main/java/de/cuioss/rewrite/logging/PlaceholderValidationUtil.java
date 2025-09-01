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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for validating and correcting placeholder patterns in logging messages.
 * <p>
 * CUI logging standards require the use of %s placeholders exclusively.
 * This utility detects and can correct:
 * - SLF4J-style {} placeholders
 * - Printf-style placeholders like %d, %f, %x, etc.
 * </p>
 */
public final class PlaceholderValidationUtil {

    /**
     * Pattern for correct CUI placeholder (%s)
     */
    public static final Pattern CORRECT_PLACEHOLDER_PATTERN = Pattern.compile("%s");

    /**
     * Pattern for incorrect placeholders:
     * - {} from SLF4J
     * - %d, %f, %i, %o, %b, %x, %X, %e, %E, %g, %G from printf
     */
    public static final Pattern INCORRECT_PLACEHOLDER_PATTERN =
        Pattern.compile("\\{\\}|%[dfiobxXeEgG]");

    private PlaceholderValidationUtil() {
        // Utility class
    }

    /**
     * Checks if a message contains incorrect placeholder patterns.
     *
     * @param message the message to check
     * @return true if incorrect placeholders are found
     */
    public static boolean hasIncorrectPlaceholders(String message) {
        if (message == null) {
            return false;
        }
        return INCORRECT_PLACEHOLDER_PATTERN.matcher(message).find();
    }

    /**
     * Corrects placeholder patterns in a message by replacing them with %s.
     *
     * @param message the message to correct
     * @return the corrected message with all placeholders replaced by %s
     */
    public static String correctPlaceholders(String message) {
        if (message == null) {
            return null;
        }
        return message.replaceAll("\\{\\}", "%s")
            .replaceAll("%[dfiobxXeEgG]", "%s");
    }

    /**
     * Counts the number of %s placeholders in a message.
     *
     * @param message the message to analyze
     * @return the count of %s placeholders
     */
    public static int countPlaceholders(String message) {
        if (message == null) {
            return 0;
        }
        Matcher matcher = CORRECT_PLACEHOLDER_PATTERN.matcher(message);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /**
     * Validates that the number of placeholders matches the number of parameters.
     *
     * @param message the message with placeholders
     * @param paramCount the number of parameters provided
     * @return true if counts match, false otherwise
     */
    public static boolean validateParameterCount(String message, int paramCount) {
        return countPlaceholders(message) == paramCount;
    }

    /**
     * Result of placeholder validation
     */
    public static class ValidationResult {
        private final boolean hasIncorrectPlaceholders;
        private final int placeholderCount;
        private final String correctedMessage;

        public ValidationResult(String message) {
            this.hasIncorrectPlaceholders = PlaceholderValidationUtil.hasIncorrectPlaceholders(message);
            this.correctedMessage = this.hasIncorrectPlaceholders ?
                PlaceholderValidationUtil.correctPlaceholders(message) : message;
            this.placeholderCount = PlaceholderValidationUtil.countPlaceholders(this.correctedMessage);
        }

        public boolean hasIncorrectPlaceholders() {
            return hasIncorrectPlaceholders;
        }

        public int getPlaceholderCount() {
            return placeholderCount;
        }

        public String getCorrectedMessage() {
            return correctedMessage;
        }

        public boolean isParameterCountValid(int paramCount) {
            return placeholderCount == paramCount;
        }
    }
}