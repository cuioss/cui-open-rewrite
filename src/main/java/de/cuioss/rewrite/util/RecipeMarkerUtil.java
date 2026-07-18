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

import org.openrewrite.Cursor;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;

import java.util.List;

/**
 * Utility class for managing OpenRewrite recipe markers and task comments.
 * Provides methods to create consistent task messages and check for existing markers.
 */
public final class RecipeMarkerUtil {

    private RecipeMarkerUtil() {
        // Utility class
    }

    /**
     * Creates a TO-DO task message with suppression hint.
     *
     * @param action the action description (e.g., "Use CuiLogger", "Fix parameter count")
     * @param recipeName the name of the recipe for suppression
     * @return formatted task message with suppression hint
     */
    public static String createTaskMessage(String action, String recipeName) {
        return "TODO: %s. Suppress: // cui-rewrite:disable %s".formatted(action, recipeName);
    }

    /**
     * Builds a copy of the given catch with the advisory task marker placed on its own line
     * above the {@code catch} keyword.
     *
     * <p>The catch element's leading {@link Space} — the prefix between the try-block's closing
     * {@code }} and the {@code catch} keyword — is reshaped to {@code newline + indent}, followed
     * by the marker as a multiline block comment, followed by {@code newline + indent} before the
     * {@code catch} keyword. This keeps the {@code catch} line itself untouched (apart from
     * indentation), avoiding the coverage/blame churn an inline marker would cause. Because the
     * marker is a multiline comment carried in the catch prefix, the existing duplicate-detection
     * path ({@link #hasTaskComment}) recognizes it on subsequent runs and the idempotence guard
     * still fires.</p>
     *
     * @param catchBlock  the catch to mark
     * @param taskMessage the advisory task message to embed as a multiline comment
     * @param indent      the indentation (leading whitespace) of the enclosing try/catch construct
     * @return a copy of {@code catchBlock} whose prefix carries the own-line marker comment
     */
    public static J.Try.Catch withOwnLineCatchMarker(J.Try.Catch catchBlock, String taskMessage, String indent) {
        String newlineIndent = "\n" + indent;
        Comment marker = new TextComment(true, taskMessage, newlineIndent, Markers.EMPTY);
        return catchBlock.withPrefix(Space.build(newlineIndent, List.of(marker)));
    }

    /**
     * Checks if a J element already has a SearchResult marker.
     *
     * @param element the Java element to check
     * @return true if the element has a SearchResult marker
     */
    public static boolean hasSearchResultMarker(J element) {
        return element.getMarkers().findFirst(SearchResult.class).isPresent();
    }

    /**
     * Checks if a Space contains a multiline comment with the given message.
     * This prevents duplicate markers when recipes run multiple times.
     *
     * @param space the Space to check for comments
     * @param taskMessage the task message to search for
     * @param cursor the current cursor for printing comments
     * @return true if a matching comment exists
     */
    public static boolean containsTaskInSpace(Space space, String taskMessage, Cursor cursor) {
        return space.getComments().stream()
            .filter(Comment::isMultiline)
            .map(comment -> comment.printComment(cursor))
            .anyMatch(text -> text.contains(taskMessage));
    }

    /**
     * Checks if a task comment already exists for the given element.
     * This prevents duplicate markers when recipes run multiple times.
     *
     * @param element the element to check
     * @param taskMessage the task message to search for
     * @param cursor the current cursor for printing comments
     * @return true if a matching task comment exists
     */
    public static boolean hasTaskComment(J element, String taskMessage, Cursor cursor) {
        return switch (element) {
            case J.Try.Catch catchBlock -> hasTaskCommentInCatchBlock(catchBlock, taskMessage, cursor);
            case J.Throw throwStmt -> hasTaskCommentInThrowStatement(throwStmt, taskMessage, cursor);
            case J.NewClass newClass -> containsTaskInSpace(newClass.getPrefix(), taskMessage, cursor);
            case J.MethodInvocation mi -> containsTaskInSpace(mi.getPrefix(), taskMessage, cursor);
            default -> containsTaskInSpace(element.getPrefix(), taskMessage, cursor);
        };
    }

    private static boolean hasTaskCommentInCatchBlock(J.Try.Catch catchBlock, String taskMessage, Cursor cursor) {
        if (containsTaskInSpace(catchBlock.getPrefix(), taskMessage, cursor)) {
            return true;
        }
        // Also check the body prefix
        return containsTaskInSpace(catchBlock.getBody().getPrefix(), taskMessage, cursor);
    }

    private static boolean hasTaskCommentInThrowStatement(J.Throw throwStmt, String taskMessage, Cursor cursor) {
        if (containsTaskInSpace(throwStmt.getPrefix(), taskMessage, cursor)) {
            return true;
        }
        // Also check exception prefix if it's a NewClass
        if (throwStmt.getException() instanceof J.NewClass nc) {
            return containsTaskInSpace(nc.getPrefix(), taskMessage, cursor);
        }
        return false;
    }
}
