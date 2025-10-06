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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.time.Duration;
import java.util.List;
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
    private static final String SUPPRESSION_COMMENT = "cui-rewrite:disable";

    private static final Set<String> GENERIC_EXCEPTION_TYPES = Set.of(
        "java.lang.Exception",
        "java.lang.RuntimeException",
        "java.lang.Throwable"
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
        return new InvalidExceptionUsageVisitor();
    }

    @Override
    public List<Recipe> getRecipeList() {
        return List.of();
    }

    private static class InvalidExceptionUsageVisitor extends JavaIsoVisitor<ExecutionContext> {

        /**
         * Creates a task message with suppression hint for the given action and exception type.
         *
         * @param action the action being performed (e.g., "Catch", "Throw", "Use")
         * @param simpleType the simple name of the exception type
         * @return the complete task message with suppression hint
         */
        private String createTaskMessage(String action, String simpleType) {
            return "TODO: %s specific not %s. Suppress: // cui-rewrite:disable %s".formatted(
                action, simpleType, RECIPE_NAME);
        }

        /**
         * Checks if a comment with the given message already exists near the element.
         * This prevents duplicate markers when recipes run multiple times.
         * We check for the specific format that OpenRewrite uses: "/*~~(...)~~>* /"
         */
        private boolean hasTaskComment(J element, String taskMessage) {
            return switch (element) {
                case J.Try.Catch catchBlock -> hasTaskCommentInCatchBlock(catchBlock, taskMessage);
                case J.Throw throwStmt -> hasTaskCommentInThrowStatement(throwStmt, taskMessage);
                case J.NewClass newClass -> containsTaskInSpace(newClass.getPrefix(), taskMessage);
                default -> false;
            };
        }

        private boolean hasTaskCommentInCatchBlock(J.Try.Catch catchBlock, String taskMessage) {
            if (containsTaskInSpace(catchBlock.getPrefix(), taskMessage)) {
                return true;
            }
            // Also check the body prefix
            return containsTaskInSpace(catchBlock.getBody().getPrefix(), taskMessage);
        }

        private boolean hasTaskCommentInThrowStatement(J.Throw throwStmt, String taskMessage) {
            if (containsTaskInSpace(throwStmt.getPrefix(), taskMessage)) {
                return true;
            }
            // Also check exception prefix if it's a NewClass
            if (throwStmt.getException() instanceof J.NewClass nc) {
                return containsTaskInSpace(nc.getPrefix(), taskMessage);
            }
            return false;
        }

        /**
         * Checks if a Space contains a comment with the given message.
         * Uses printComment() to get the full comment text, similar to suppression checking.
         */
        private boolean containsTaskInSpace(Space space, String taskMessage) {
            return space.getComments().stream()
                .filter(Comment::isMultiline)
                .map(comment -> comment.printComment(getCursor()))
                .anyMatch(text -> text.contains(taskMessage));
        }

        /**
         * Checks if there's a suppression comment at the end of the try block that applies to this catch block.
         * This handles the common pattern where suppression is placed before the closing brace of try.
         */
        private boolean checkTryBlockEndSuppression() {
            // Navigate to the parent Try statement
            var cursor = getCursor().getParentTreeCursor();
            if (cursor.getValue() instanceof J.Try tryStatement) {
                return checkSuppressionInTryBody(tryStatement.getBody(), cursor);
            }
            return false;
        }

        private boolean checkSuppressionInTryBody(J.Block tryBody, Cursor cursor) {
            // First check comments in the try body's end space (before the closing brace)
            var endComments = tryBody.getEnd().getComments();
            for (Comment comment : endComments) {
                if (hasSuppressionComment(comment.printComment(cursor))) {
                    return true;
                }
            }

            // Also check if the last statement in the try block has trailing comments
            // This handles cases where the comment is attached to the last statement
            if (!tryBody.getStatements().isEmpty()) {
                var afterComments = tryBody.getStatements().getLast().getPrefix().getComments();
                for (Comment comment : afterComments) {
                    if (hasSuppressionComment(comment.printComment(cursor))) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean hasSuppressionComment(String text) {
            return text.contains(SUPPRESSION_COMMENT) &&
                (text.contains(RECIPE_NAME) || text.trim().endsWith(SUPPRESSION_COMMENT));
        }


        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
            // Check for class-level suppression
            if (RecipeSuppressionUtil.isSuppressed(getCursor(), RECIPE_NAME)) {
                // Skip the entire class
                return classDecl;
            }
            return super.visitClassDeclaration(classDecl, ctx);
        }

        @Override
        @SuppressWarnings("java:S2637")
        // SearchResult.found() never returns null for non-null input
        public J.Try.Catch visitCatch(J.Try.Catch catchBlock, ExecutionContext ctx) {
            J.Try.Catch c = super.visitCatch(catchBlock, ctx);

            // Check for suppression (handles all scenarios automatically)
            if (RecipeSuppressionUtil.isSuppressed(getCursor(), RECIPE_NAME)) {
                return c;
            }

            // Additional check: the comment before the catch might be in the catch's own prefix
            // This is because comments before "} catch" often attach to the catch block itself
            var catchComments = c.getPrefix().getComments();
            for (Comment comment : catchComments) {
                String text = comment.printComment(getCursor());
                if (text.contains(SUPPRESSION_COMMENT) &&
                    (text.contains(RECIPE_NAME) ||
                        text.trim().endsWith(SUPPRESSION_COMMENT))) {
                    return c;
                }
            }

            // Special case: Check if suppression comment is at the end of the try block body
            // This handles the case where the suppression is placed before the closing brace of try
            if (checkTryBlockEndSuppression()) {
                return c;
            }

            // Check the caught exception type
            J.ControlParentheses<J.VariableDeclarations> parameterParens = c.getParameter();
            J.VariableDeclarations parameter = parameterParens.getTree();
            if (parameter.getType() != null) {
                JavaType type = parameter.getType();
                JavaType.FullyQualified fqType = TypeUtils.asFullyQualified(type);

                if (fqType != null && GENERIC_EXCEPTION_TYPES.contains(fqType.getFullyQualifiedName())) {
                    String simpleType = fqType.getClassName();
                    String taskMessage = createTaskMessage("Catch", simpleType);

                    // Check if this comment already exists (from a previous run)
                    if (hasTaskComment(c, taskMessage) || c.getMarkers().findFirst(SearchResult.class).isPresent()) {
                        return c;
                    }
                    return SearchResult.found(c, taskMessage);
                }
            }

            return c;
        }

        @Override
        @SuppressWarnings("java:S2637")
        // SearchResult.found() never returns null for non-null input
        public J.Throw visitThrow(J.Throw thrown, ExecutionContext ctx) {
            J.Throw t = super.visitThrow(thrown, ctx);

            // Check if suppressed
            if (RecipeSuppressionUtil.isSuppressed(getCursor(), RECIPE_NAME)) {
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
                    if (hasTaskComment(t, taskMessage) || t.getMarkers().findFirst(SearchResult.class).isPresent()) {
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
            if (RecipeSuppressionUtil.isSuppressed(getCursor(), RECIPE_NAME)) {
                return nc;
            }

            JavaType type = nc.getType();
            JavaType.FullyQualified fqType = TypeUtils.asFullyQualified(type);

            if (fqType != null && GENERIC_EXCEPTION_TYPES.contains(fqType.getFullyQualifiedName())) {
                String simpleType = fqType.getClassName();
                String taskMessage = createTaskMessage("Use", simpleType);

                // Check if this comment already exists (from a previous run)
                if (hasTaskComment(nc, taskMessage) || nc.getMarkers().findFirst(SearchResult.class).isPresent()) {
                    return nc;
                }
                return SearchResult.found(nc, taskMessage);
            }

            return nc;
        }
    }
}