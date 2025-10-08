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
package de.cuioss.rewrite.format;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.format.NormalizeFormatVisitor;

/**
 * Test recipe wrapper for NormalizeFormatVisitor.
 * Required because anonymous Recipe classes cannot be serialized for testing.
 */
public class NormalizeFormatRecipe extends Recipe {

    @Override
    public String getDisplayName() {
        return "Normalize Format";
    }

    @Override
    public String getDescription() {
        return "Normalize whitespace to outermost elements.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new NormalizeFormatVisitor<>();
    }
}
