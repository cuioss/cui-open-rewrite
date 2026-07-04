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

import org.jspecify.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for validating and correcting placeholder patterns in logging messages.
 * <p>
 * CUI logging standards require the use of {@code %s} placeholders exclusively.
 * This utility detects and can correct:
 * <ul>
 *   <li>SLF4J-style {@code {}} placeholders</li>
 *   <li>Any printf-style conversion other than {@code %s} — including width, precision,
 *       flags and argument-index forms such as {@code %d}, {@code %5d}, {@code %.2f},
 *       {@code %08x}, {@code %,d}, {@code %1$s} and {@code %n}</li>
 * </ul>
 * <p>
 * The literal-percent escape {@code %%} is recognised and left untouched, so a message such
 * as {@code "100%% done"} is neither flagged nor rewritten, and {@code "%%d"} keeps its escape
 * instead of being corrupted into {@code "%%s"}.
 */
public final class PlaceholderValidationUtil {

    /**
     * Matches a single printf-style format directive left-to-right:
     * either the literal-percent escape {@code %%}, or a conversion with an optional
     * argument index ({@code 3$}), flags ({@code -#+ 0,(}), width, precision and a
     * trailing conversion letter (covers {@code %s}, {@code %d}, {@code %n}, {@code %1$s}, …).
     */
    private static final Pattern FORMAT_DIRECTIVE =
        Pattern.compile("%%|%(?:\\d+\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?[a-zA-Z]");

    /**
     * The single correct CUI placeholder.
     */
    private static final String CORRECT_PLACEHOLDER = "%s";

    /**
     * The SLF4J placeholder that must be rewritten to {@code %s}.
     */
    private static final String SLF4J_PLACEHOLDER = "{}";

    private PlaceholderValidationUtil() {
        // Utility class
    }

    /**
     * Checks if a message contains incorrect placeholder patterns.
     *
     * @param message the message to check
     * @return true if incorrect placeholders are found
     */
    public static boolean hasIncorrectPlaceholders(@Nullable String message) {
        if (message == null) {
            return false;
        }
        if (message.contains(SLF4J_PLACEHOLDER)) {
            return true;
        }
        Matcher matcher = FORMAT_DIRECTIVE.matcher(message);
        while (matcher.find()) {
            String directive = matcher.group();
            if (!"%%".equals(directive) && !CORRECT_PLACEHOLDER.equals(directive)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Corrects placeholder patterns in a message by replacing them with {@code %s}.
     * SLF4J {@code {}} and every printf directive other than {@code %s}/{@code %%} become
     * {@code %s}; {@code %s} and the {@code %%} escape are preserved.
     *
     * @param message the message to correct
     * @return the corrected message with all placeholders replaced by {@code %s}
     */
    @Nullable
    public static String correctPlaceholders(@Nullable String message) {
        if (message == null) {
            return null;
        }
        // Replace SLF4J placeholders first; the resulting %s are then left untouched below.
        String withoutSlf4j = message.replace(SLF4J_PLACEHOLDER, CORRECT_PLACEHOLDER);

        Matcher matcher = FORMAT_DIRECTIVE.matcher(withoutSlf4j);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String directive = matcher.group();
            String replacement = ("%%".equals(directive) || CORRECT_PLACEHOLDER.equals(directive))
                ? directive
                : CORRECT_PLACEHOLDER;
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Counts the number of {@code %s} placeholders in a message. The literal-percent
     * escape {@code %%} is consumed first, so the {@code s} in {@code "%%s"} is not counted.
     *
     * @param message the message to analyze
     * @return the count of {@code %s} placeholders
     */
    public static int countPlaceholders(@Nullable String message) {
        if (message == null) {
            return 0;
        }
        Matcher matcher = FORMAT_DIRECTIVE.matcher(message);
        int count = 0;
        while (matcher.find()) {
            if (CORRECT_PLACEHOLDER.equals(matcher.group())) {
                count++;
            }
        }
        return count;
    }
}
