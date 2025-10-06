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
import org.openrewrite.marker.SearchResult;

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
