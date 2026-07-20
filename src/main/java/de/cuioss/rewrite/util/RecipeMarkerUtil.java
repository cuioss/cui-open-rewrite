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
package de.cuioss.rewrite.util;

import de.cuioss.rewrite.util.RecipeLogMessages.WARN;
import de.cuioss.tools.logging.CuiLogger;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.PrintOutputCapture;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaPrinter;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for managing OpenRewrite recipe markers and task comments.
 * Provides methods to create consistent task messages and check for existing markers.
 */
public final class RecipeMarkerUtil {

    private static final CuiLogger LOGGER = new CuiLogger(RecipeMarkerUtil.class);

    private RecipeMarkerUtil() {
        // Utility class
    }

    /**
     * Emits a single WARN-level build-log line for a recipe finding, making the finding visible
     * even when its marker was already present in the source (and therefore produces no diff).
     *
     * <p>The source file path is resolved from the enclosing
     * {@link org.openrewrite.java.tree.J.CompilationUnit} and the marked element's (line, column)
     * is derived from its start offset within the printed compilation-unit source (offset counted
     * up to the first non-prefix character of the element, converted to line/column by counting
     * newlines). The {@code preExisting} flag selects wording that distinguishes a newly-detected
     * finding from an already-present one so the two remain independently greppable.</p>
     *
     * @param element     the marked Java element (its position drives the reported line/column)
     * @param taskMessage the advisory task/marker message describing the finding
     * @param recipeName  the name of the recipe that produced the finding
     * @param cursor      the current cursor, used to resolve the enclosing compilation unit
     * @param preExisting {@code true} when the marker was already present (no diff),
     *                    {@code false} when the finding is newly detected on this run
     */
    public static void logFinding(J element, String taskMessage, String recipeName, Cursor cursor, boolean preExisting) {
        J.CompilationUnit compilationUnit = cursor.firstEnclosingOrThrow(J.CompilationUnit.class);
        Path sourcePath = compilationUnit.getSourcePath();
        int[] lineColumn = lineAndColumn(compilationUnit, element);
        int line = lineColumn[0];
        int column = lineColumn[1];
        if (preExisting) {
            LOGGER.warn(WARN.FINDING_PRE_EXISTING, sourcePath, line, column, recipeName, taskMessage);
        } else {
            LOGGER.warn(WARN.FINDING_DETECTED, sourcePath, line, column, recipeName, taskMessage);
        }
    }

    /**
     * Derives the 1-based {@code (line, column)} of the given element within the printed source of
     * the compilation unit. The element's start offset is the position of its first non-prefix
     * character: the compilation unit is printed with a {@link JavaPrinter} that records the output
     * length immediately after the element's leading {@link Space} (its prefix) has been printed.
     *
     * @param compilationUnit the enclosing compilation unit whose printed source is measured
     * @param element         the element to locate (matched by node id within the unit)
     * @return a two-element array {@code [line, column]}, both 1-based; {@code [1, 1]} when the
     *         element cannot be located within the printed source
     */
    private static int[] lineAndColumn(J.CompilationUnit compilationUnit, J element) {
        int[] capturedOffset = {-1};
        PrintOutputCapture<Integer> capture = new PrintOutputCapture<>(0);
        JavaPrinter<Integer> printer = new JavaPrinter<>() {

            private boolean armed;

            @Override
            public @Nullable J visit(@Nullable Tree tree, PrintOutputCapture<Integer> p) {
                // Match by node id rather than object identity: withMarkers/withPrefix produce a
                // copy that preserves getId(), so a rewritten or copied element passed to
                // logFinding still locates its original source position instead of falling back
                // to [1,1].
                if (tree != null && tree.getId().equals(element.getId())) {
                    armed = true;
                }
                return super.visit(tree, p);
            }

            @Override
            public Space visitSpace(Space space, Space.Location loc, PrintOutputCapture<Integer> p) {
                Space result = super.visitSpace(space, loc, p);
                if (armed && capturedOffset[0] < 0) {
                    capturedOffset[0] = p.out.length();
                    armed = false;
                }
                return result;
            }
        };
        printer.visit(compilationUnit, capture);
        String source = capture.getOut();
        int offset = capturedOffset[0] < 0 ? 0 : capturedOffset[0];
        int line = 1;
        int column = 1;
        for (int i = 0; i < offset && i < source.length(); i++) {
            if (source.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new int[]{line, column};
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
     * <p>Any comments already present in the catch prefix (developer notes, unrelated suppression
     * comments) are preserved — the new marker is appended after them rather than replacing them,
     * keeping the recipe non-destructive to unrelated AST content.</p>
     *
     * @param catchBlock  the catch to mark
     * @param taskMessage the advisory task message to embed as a multiline comment
     * @param indent      the indentation (leading whitespace) of the enclosing try/catch construct
     * @return a copy of {@code catchBlock} whose prefix carries the own-line marker comment
     */
    public static J.Try.Catch withOwnLineCatchMarker(J.Try.Catch catchBlock, String taskMessage, String indent) {
        String newlineIndent = "\n" + indent;
        Comment marker = new TextComment(true, taskMessage, newlineIndent, Markers.EMPTY);
        List<Comment> comments = new ArrayList<>(catchBlock.getPrefix().getComments());
        comments.add(marker);
        return catchBlock.withPrefix(Space.build(newlineIndent, comments));
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
     * Checks if a J element has a SearchResult marker whose description matches the given
     * message or recipe name. Scoping the check to a specific message/recipe name prevents
     * cross-recipe interference: a marker added by a different recipe no longer counts as this
     * recipe's own pre-existing finding.
     *
     * @param element the Java element to check
     * @param messageOrRecipeName the marker message or recipe name to match against the
     *                            SearchResult description (exact match or substring)
     * @return true if a SearchResult marker with a matching description is present
     */
    public static boolean hasSearchResultMarker(J element, String messageOrRecipeName) {
        return element.getMarkers().findFirst(SearchResult.class)
            .map(SearchResult::getDescription)
            .filter(desc -> desc != null
                && (desc.equals(messageOrRecipeName) || desc.contains(messageOrRecipeName)))
            .isPresent();
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
