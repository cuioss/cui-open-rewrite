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
package de.cuioss.rewrite;

import org.junit.jupiter.api.Test;
import org.openrewrite.Recipe;
import org.openrewrite.config.Environment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that the declarative {@code de.cuioss.rewrite.CuiDefaults} composite recipe is discoverable
 * on the classpath and lists the CUI recipes in the documented order (AutoFormat first).
 */
class CuiDefaultsRecipeTest {

    private static final String RECIPE_NAME = "de.cuioss.rewrite.CuiDefaults";

    @Test
    void shouldLoadCompositeInDocumentedOrder() {
        Environment environment = Environment.builder()
            .scanRuntimeClasspath("de.cuioss.rewrite")
            .build();

        Recipe recipe = environment.activateRecipes(RECIPE_NAME);
        assertNotNull(recipe);
        assertFalse(recipe.getDescription().isBlank(), "description must not be blank");

        List<String> orderedNames = recipe.getRecipeList().stream()
            .map(Recipe::getName)
            .toList();

        assertEquals(
            List.of(
                "org.openrewrite.java.format.AutoFormat",
                "de.cuioss.rewrite.format.AnnotationNewlineFormat",
                "de.cuioss.rewrite.logging.CuiLoggerStandardsRecipe",
                "de.cuioss.rewrite.logging.CuiLogRecordPatternRecipe",
                "de.cuioss.rewrite.logging.InvalidExceptionUsageRecipe"
            ),
            orderedNames
        );
    }
}
