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
package de.cuioss.rewrite.util;

import de.cuioss.tools.logging.LogRecord;
import de.cuioss.tools.logging.LogRecordModel;

/**
 * DSL-style {@link LogRecord} definitions for the cui-open-rewrite recipes.
 *
 * <p>Holds the WARN-level records emitted by {@link RecipeMarkerUtil#logFinding} so every
 * marker-producing recipe reports each finding in the build log — even when the marker was
 * already present in the source and therefore produces no diff. The two records use
 * distinguishable wording ({@code detected} vs {@code pre-existing}) and separate identifiers
 * so newly-detected and already-present findings stay independently greppable.</p>
 *
 * @see de.cuioss.rewrite.util.RecipeMarkerUtil#logFinding
 */
public final class RecipeLogMessages {

    /** Common identifier prefix for all cui-open-rewrite log records. */
    public static final String PREFIX = "CUI_REWRITE";

    private RecipeLogMessages() {
        // Utility class
    }

    /** WARN-level records (identifier range 100-199). */
    public static final class WARN {

        /**
         * Emitted when a recipe newly detects a finding on the current run. Parameters, in order:
         * source file path, line, column, recipe name, task/marker message.
         */
        public static final LogRecord FINDING_DETECTED = LogRecordModel.builder()
            .template("Finding detected at %s:%s:%s by %s: %s")
            .prefix(PREFIX)
            .identifier(100)
            .build();

        /**
         * Emitted when a recipe re-encounters a finding whose marker was already present in the
         * source (no diff produced). Parameters, in order: source file path, line, column, recipe
         * name, task/marker message.
         */
        public static final LogRecord FINDING_PRE_EXISTING = LogRecordModel.builder()
            .template("Finding pre-existing at %s:%s:%s by %s: %s")
            .prefix(PREFIX)
            .identifier(101)
            .build();

        private WARN() {
            // Utility class
        }
    }
}
