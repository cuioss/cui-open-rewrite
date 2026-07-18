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

import de.cuioss.rewrite.util.BaseSuppressionVisitor;
import de.cuioss.rewrite.util.PathExclusionVisitor;
import de.cuioss.rewrite.util.RecipeMarkerUtil;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
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

    public static final String RECIPE_NAME = "InvalidExceptionUsageRecipe";

    private static final Set<String> GENERIC_EXCEPTION_TYPES = Set.of(
        "java.lang.Exception",
        "java.lang.RuntimeException",
        "java.lang.Throwable"
    );

    private static final Set<String> JUNIT5_TEST_ANNOTATIONS = Set.of(
        "org.junit.jupiter.api.Test",
        "org.junit.jupiter.params.ParameterizedTest",
        "org.junit.jupiter.api.RepeatedTest",
        "org.junit.jupiter.api.TestTemplate",
        "org.junit.jupiter.api.TestFactory"
    );

    @Override
    public String getDisplayName() {
        return "Invalid exception usage";
    }

    @Override
    public String getDescription() {
        return """
            Flags usage of generic exception types (Exception, RuntimeException, Throwable) \
            in catch blocks and throw statements. Code should use specific exception types instead.""";
    }

    @Override
    public Set<String> getTags() {
        return Set.of("CUI", "exceptions", "best-practices");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new PathExclusionVisitor(), new InvalidExceptionUsageVisitor());
    }

    private static class InvalidExceptionUsageVisitor extends BaseSuppressionVisitor {

        InvalidExceptionUsageVisitor() {
            super(RECIPE_NAME);
        }

        /**
         * Creates a task message with suppression hint for the given action and exception type.
         *
         * @param action the action being performed (e.g., "Catch", "Throw", "Use")
         * @param simpleType the simple name of the exception type
         * @return the complete task message with suppression hint
         */
        private String createTaskMessage(String action, String simpleType) {
            String actionMessage = "%s specific not %s".formatted(action, simpleType);
            return RecipeMarkerUtil.createTaskMessage(actionMessage, RECIPE_NAME);
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            if (isTestMethod(method)) {
                return method; // Skip entire method body for JUnit 5 test methods
            }
            // BaseSuppressionVisitor handles method- and class-level suppression.
            return super.visitMethodDeclaration(method, ctx);
        }

        private boolean isTestMethod(J.MethodDeclaration method) {
            return method.getLeadingAnnotations().stream()
                .anyMatch(annotation -> {
                    JavaType.FullyQualified fqType = TypeUtils.asFullyQualified(annotation.getType());
                    return fqType != null && JUNIT5_TEST_ANNOTATIONS.contains(fqType.getFullyQualifiedName());
                });
        }

        @Override
        public J.Try.Catch visitCatch(J.Try.Catch catchBlock, ExecutionContext ctx) {
            J.Try.Catch c = super.visitCatch(catchBlock, ctx);

            // Suppression (catch prefix, class/method scope and the try-block-end pattern)
            // is handled centrally by RecipeSuppressionUtil.
            if (isSuppressed()) {
                return c;
            }

            // Check the caught exception type. A multi-catch (catch (A | B e)) has a
            // JavaType.MultiCatch union type, so each alternative must be inspected individually.
            J.ControlParentheses<J.VariableDeclarations> parameterParens = c.getParameter();
            J.VariableDeclarations parameter = parameterParens.getTree();
            String simpleType = findGenericExceptionSimpleName(parameter.getType());
            if (simpleType != null) {
                String taskMessage = createTaskMessage("Catch", simpleType);

                // Check if this comment already exists (from a previous run)
                if (RecipeMarkerUtil.hasTaskComment(c, taskMessage, getCursor()) || RecipeMarkerUtil.hasSearchResultMarker(c)) {
                    return c;
                }
                // Place the advisory marker on its own line above the catch (rather than inline
                // before the catch keyword) so the catch line itself is left untouched, avoiding
                // the coverage/blame churn an inline marker causes.
                return RecipeMarkerUtil.withOwnLineCatchMarker(c, taskMessage, catchIndent());
            }

            return c;
        }

        /**
         * Returns the indentation (leading whitespace) of the enclosing try/catch construct, used
         * to align the own-line catch marker with the closing brace of the try block.
         *
         * @return the indentation string, or an empty string when it cannot be resolved
         */
        private String catchIndent() {
            J.Try tryStatement = getCursor().firstEnclosing(J.Try.class);
            if (tryStatement == null) {
                return "";
            }
            String whitespace = tryStatement.getPrefix().getWhitespace();
            int lastNewline = whitespace.lastIndexOf('\n');
            return lastNewline >= 0 ? whitespace.substring(lastNewline + 1) : "";
        }

        /**
         * Returns the simple name of the first generic exception type in the given type, or
         * {@code null} if none is generic. Handles both single catch types and multi-catch unions.
         */
        private @Nullable String findGenericExceptionSimpleName(@Nullable JavaType type) {
            if (type instanceof JavaType.MultiCatch multiCatch) {
                for (JavaType alternative : multiCatch.getThrowableTypes()) {
                    String simpleName = genericSimpleNameOf(alternative);
                    if (simpleName != null) {
                        return simpleName;
                    }
                }
                return null;
            }
            return genericSimpleNameOf(type);
        }

        private @Nullable String genericSimpleNameOf(@Nullable JavaType type) {
            JavaType.FullyQualified fqType = TypeUtils.asFullyQualified(type);
            if (fqType != null && GENERIC_EXCEPTION_TYPES.contains(fqType.getFullyQualifiedName())) {
                return fqType.getClassName();
            }
            return null;
        }

        @Override
        @SuppressWarnings("java:S2637")
        // SearchResult.found() never returns null for non-null input
        public J.Throw visitThrow(J.Throw thrown, ExecutionContext ctx) {
            J.Throw t = super.visitThrow(thrown, ctx);

            // Check if suppressed
            if (isSuppressed()) {
                return t;
            }

            // Check if throwing a generic exception type
            if (t.getException() instanceof J.NewClass newClass) {
                JavaType type = newClass.getType();
                JavaType.FullyQualified fqType = TypeUtils.asFullyQualified(type);

                if (fqType != null && GENERIC_EXCEPTION_TYPES.contains(fqType.getFullyQualifiedName())) {
                    String simpleType = fqType.getClassName();
                    String taskMessage = createTaskMessage("Throw", simpleType);

                    // Check if this comment already exists (from a previous run)
                    if (RecipeMarkerUtil.hasTaskComment(t, taskMessage, getCursor()) || RecipeMarkerUtil.hasSearchResultMarker(t)) {
                        return t;
                    }
                    return SearchResult.found(t, taskMessage);
                }
            }

            return t;
        }

        @Override
        @SuppressWarnings("java:S2637")
        // SearchResult.found() never returns null for non-null input
        public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
            J.NewClass nc = super.visitNewClass(newClass, ctx);

            // Only check NewClass when it's not part of a throw statement (already handled)
            // This catches cases where exceptions are created but not immediately thrown
            if (getCursor().getParentTreeCursor().getValue() instanceof J.Throw) {
                return nc;
            }

            // Check if suppressed - either directly or via parent element
            if (isSuppressed()) {
                return nc;
            }

            JavaType type = nc.getType();
            JavaType.FullyQualified fqType = TypeUtils.asFullyQualified(type);

            if (fqType != null && GENERIC_EXCEPTION_TYPES.contains(fqType.getFullyQualifiedName())) {
                String simpleType = fqType.getClassName();
                String taskMessage = createTaskMessage("Use", simpleType);

                // Check if this comment already exists (from a previous run)
                if (RecipeMarkerUtil.hasTaskComment(nc, taskMessage, getCursor()) || RecipeMarkerUtil.hasSearchResultMarker(nc)) {
                    return nc;
                }
                return SearchResult.found(nc, taskMessage);
            }

            return nc;
        }
    }
}