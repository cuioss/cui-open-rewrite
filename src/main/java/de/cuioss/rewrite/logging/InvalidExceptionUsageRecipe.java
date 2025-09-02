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

import de.cuioss.rewrite.util.RecipeSuppressionUtil;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.time.Duration;
import java.util.Set;

/**
 * Recipe to detect and flag invalid usage of generic exception types.
 *
 * <p>This recipe identifies and flags the following bad practices:</p>
 * <ul>
 *   <li>Catching raw {@code Exception}, {@code RuntimeException}, or {@code Throwable}</li>
 *   <li>Throwing raw {@code Exception}, {@code RuntimeException}, or {@code Throwable}</li>
 * </ul>
 *
 * <p>Note: This recipe only flags issues and does not provide auto-fixes, as the appropriate
 * specific exception type depends on the context and business logic.</p>
 *
 * <p>Suppression is supported via {@code // cui-rewrite:disable InvalidExceptionUsageRecipe}</p>
 */
public class InvalidExceptionUsageRecipe extends Recipe {

    private static final String RECIPE_NAME = "InvalidExceptionUsageRecipe";

    private static final Set<String> GENERIC_EXCEPTION_TYPES = Set.of(
        "java.lang.Exception",
        "java.lang.RuntimeException",
        "java.lang.Throwable"
    );

    @Override public String getDisplayName() {
        return "Invalid exception usage";
    }

    @Override public String getDescription() {
        return "Flags usage of generic exception types (Exception, RuntimeException, Throwable) " +
            "in catch blocks and throw statements. Code should use specific exception types instead.";
    }

    @Override public Set<String> getTags() {
        return Set.of("CUI", "exceptions", "best-practices");
    }

    @Override public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new InvalidExceptionUsageVisitor();
    }

    private static class InvalidExceptionUsageVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override @SuppressWarnings("nullness") // SearchResult.found() never returns null for non-null input
        public J.Try.Catch visitCatch(J.Try.Catch catchBlock, ExecutionContext ctx) {
            J.Try.Catch c = super.visitCatch(catchBlock, ctx);

            // Check if suppressed
            if (RecipeSuppressionUtil.isSuppressed(c, getCursor(), RECIPE_NAME)) {
                return c;
            }

            // Check the caught exception type
            J.ControlParentheses<J.VariableDeclarations> parameterParens = c.getParameter();
            J.VariableDeclarations parameter = parameterParens.getTree();
            if (parameter.getType() != null) {
                JavaType type = parameter.getType();
                JavaType.FullyQualified fqType = TypeUtils.asFullyQualified(type);

                if (fqType != null && GENERIC_EXCEPTION_TYPES.contains(fqType.getFullyQualifiedName())) {
                    return SearchResult.found(c);
                }
            }

            return c;
        }

        @Override @SuppressWarnings("nullness") // SearchResult.found() never returns null for non-null input
        public J.Throw visitThrow(J.Throw thrown, ExecutionContext ctx) {
            J.Throw t = super.visitThrow(thrown, ctx);

            // Check if suppressed
            if (RecipeSuppressionUtil.isSuppressed(t, getCursor(), RECIPE_NAME)) {
                return t;
            }

            // Check if throwing a generic exception type
            if (t.getException() instanceof J.NewClass newClass) {
                JavaType type = newClass.getType();
                JavaType.FullyQualified fqType = TypeUtils.asFullyQualified(type);

                if (fqType != null && GENERIC_EXCEPTION_TYPES.contains(fqType.getFullyQualifiedName())) {
                    return SearchResult.found(t);
                }
            }

            return t;
        }

        @Override @SuppressWarnings("nullness") // SearchResult.found() never returns null for non-null input
        public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
            J.NewClass nc = super.visitNewClass(newClass, ctx);

            // Only check NewClass when it's not part of a throw statement (already handled)
            // This catches cases where exceptions are created but not immediately thrown
            if (getCursor().getParentTreeCursor().getValue() instanceof J.Throw) {
                return nc;
            }

            // Check if suppressed
            if (RecipeSuppressionUtil.isSuppressed(nc, getCursor(), RECIPE_NAME)) {
                return nc;
            }

            JavaType type = nc.getType();
            JavaType.FullyQualified fqType = TypeUtils.asFullyQualified(type);

            if (fqType != null && GENERIC_EXCEPTION_TYPES.contains(fqType.getFullyQualifiedName())) {
                return SearchResult.found(nc);
            }

            return nc;
        }
    }
}