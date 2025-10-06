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

import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

/**
 * Base visitor that eliminates suppression code duplication while maintaining recipe functionality.
 * 
 * <p>Automatically handles class and method level suppression (which always work the same way)
 * and provides a convenient helper for element-level suppression (which varies by recipe logic).
 * 
 * <p><strong>Design Rationale:</strong> Complete automatic suppression is not feasible because
 * many recipes need to process elements before determining whether they should be suppressed.
 * This class strikes the optimal balance between eliminating duplication and preserving functionality.
 */
public abstract class BaseSuppressionVisitor extends JavaIsoVisitor<ExecutionContext> {

    private final String recipeName;

    protected BaseSuppressionVisitor(String recipeName) {
        this.recipeName = recipeName;
    }

    /**
     * Helper method to check if the current element is suppressed.
     * Use this in visit methods that need element-level suppression control.
     */
    protected boolean isSuppressed() {
        return RecipeSuppressionUtil.isSuppressed(getCursor(), recipeName);
    }

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
        if (isSuppressed()) {
            return classDecl; // Skip entire class and children - this pattern is always the same
        }
        return super.visitClassDeclaration(classDecl, ctx);
    }

    @Override
    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
        if (isSuppressed()) {
            return method; // Skip entire method and children - this pattern is always the same
        }
        return super.visitMethodDeclaration(method, ctx);
    }
}