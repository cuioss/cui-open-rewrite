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
/**
 * Shared utilities supporting the CUI OpenRewrite recipes.
 *
 * <p>This package provides the cross-cutting building blocks used by the recipes:
 * {@link de.cuioss.rewrite.util.BaseSuppressionVisitor} and
 * {@link de.cuioss.rewrite.util.RecipeSuppressionUtil} for {@code // cui-rewrite:disable} suppression
 * handling, {@link de.cuioss.rewrite.util.PathExclusionVisitor} for excluding generated sources under
 * {@code target/}/{@code build/}, and {@link de.cuioss.rewrite.util.RecipeMarkerUtil} for creating and
 * detecting the {@code SearchResult} task markers.</p>
 */
@NullMarked
package de.cuioss.rewrite.util;

import org.jspecify.annotations.NullMarked;
