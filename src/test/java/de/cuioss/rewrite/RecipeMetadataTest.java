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
package de.cuioss.rewrite;

import de.cuioss.rewrite.format.AnnotationNewlineFormat;
import de.cuioss.rewrite.logging.CuiLogRecordPatternRecipe;
import de.cuioss.rewrite.logging.CuiLoggerStandardsRecipe;
import de.cuioss.rewrite.logging.InvalidExceptionUsageRecipe;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.openrewrite.Recipe;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Smoke test ensuring every shipped recipe exposes complete metadata:
 * a non-blank display name and description plus at least one tag.
 */
class RecipeMetadataTest {

    static Stream<Recipe> recipes() {
        return Stream.of(
            new AnnotationNewlineFormat(),
            new CuiLoggerStandardsRecipe(),
            new CuiLogRecordPatternRecipe(),
            new InvalidExceptionUsageRecipe()
        );
    }

    @ParameterizedTest
    @MethodSource("recipes")
    void shouldExposeCompleteMetadata(Recipe recipe) {
        assertNotNull(recipe.getDisplayName(), "display name must not be null");
        assertFalse(recipe.getDisplayName().isBlank(), "display name must not be blank");

        assertNotNull(recipe.getDescription(), "description must not be null");
        assertFalse(recipe.getDescription().isBlank(), "description must not be blank");

        assertNotNull(recipe.getTags(), "tags must not be null");
        assertFalse(recipe.getTags().isEmpty(), "tags must not be empty");
    }
}
