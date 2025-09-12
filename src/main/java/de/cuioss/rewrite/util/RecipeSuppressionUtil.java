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

import de.cuioss.tools.logging.CuiLogger;
import org.openrewrite.Cursor;
import org.openrewrite.java.tree.Comment;
import org.openrewrite.java.tree.J;

import java.util.List;

/**
 * Utility class for checking if OpenRewrite recipes should be suppressed
 * based on special comments in the source code.
 *
 * <p>Supports the following suppression patterns:</p>
 * <ul>
 *   <li>{@code // cui-rewrite:disable} - Suppresses all recipes for the next element</li>
 *   <li>{@code // cui-rewrite:disable RecipeName} - Suppresses specific recipe</li>
 * </ul>
 *
 * <p>Note: Trailing comments on the same line are not fully supported due to
 * OpenRewrite AST limitations where comments may be attached to unexpected parts of the AST.</p>
 */
public final class RecipeSuppressionUtil {

    private static final CuiLogger LOG = new CuiLogger(RecipeSuppressionUtil.class);
    private static final String SUPPRESSION_MARKER = "cui-rewrite:disable";

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private RecipeSuppressionUtil() {
        throw new UnsupportedOperationException("Utility class");
    }


    /**
     * Comprehensive suppression check that handles ALL suppression scenarios.
     * This is the main method recipes should use - it automatically checks:
     * - Direct element suppression (preceding comments, annotations)
     * - Parent suppression (methods, classes, fields, try blocks, etc.)
     * - Context-appropriate boundaries and scoping
     *
     * @param cursor The current cursor position
     * @param recipeName The simple or fully qualified name of the recipe to check (null for any recipe)
     * @return true if the element should be suppressed
     */
    public static boolean isSuppressed(Cursor cursor, String recipeName) {
        J element = cursor.getValue();

        // Check for direct suppression on the current element
        if (hasDirectSuppression(element, cursor, recipeName)) {
            return true;
        }

        // Check comprehensive parent suppression
        return hasParentSuppression(cursor, recipeName);
    }

    /**
     * Checks for direct suppression on the current element.
     * This includes preceding comments, annotations, and element-specific locations.
     */
    private static boolean hasDirectSuppression(J element, Cursor cursor, String recipeName) {
        // Check for preceding comments (line before)
        if (hasSuppression(element.getPrefix().getComments(), cursor, recipeName)) {
            logSuppression(element, recipeName);
            return true;
        }

        // Check annotations and element-specific locations
        return checkElementSpecificSuppression(element, cursor, recipeName);
    }

    /**
     * Comprehensive parent suppression check that handles ALL parent scenarios.
     * This method automatically determines the appropriate parent types and boundaries
     * based on the current element context.
     */
    private static boolean hasParentSuppression(Cursor cursor, String recipeName) {
        J element = cursor.getValue();

        // For catch blocks and throw statements: check try blocks and methods within method boundary
        if ((element instanceof J.Try.Catch || element instanceof J.Throw) && checkParentsWithinBoundary(cursor, recipeName, J.MethodDeclaration.class,
            J.Try.class, J.MethodDeclaration.class)) {
            return true;
        }


        // For new class expressions (exception creation): check fields and methods
        if (element instanceof J.NewClass) {
            Cursor parentCursor = cursor.getParentTreeCursor();
            if (!(parentCursor.getValue() instanceof J.Throw) && checkFirstParentOfTypes(cursor, recipeName, J.VariableDeclarations.class, J.MethodDeclaration.class)) {
                return true;
            }

        }

        // For method invocations: check methods and classes
        if (element instanceof J.MethodInvocation && checkFirstParentOfTypes(cursor, recipeName, J.MethodDeclaration.class, J.ClassDeclaration.class)) {
            return true;
        }


        // Always check class-level suppression (applies to all elements)
        return isParentClassSuppressed(cursor, recipeName);
    }

    /**
     * Checks element-specific suppression locations based on the element type.
     */
    private static boolean checkElementSpecificSuppression(J element, Cursor cursor, String recipeName) {
        if (element instanceof J.ClassDeclaration cd) {
            return checkClassSuppression(cd, element, cursor, recipeName);
        } else if (element instanceof J.MethodDeclaration md) {
            return checkMethodSuppression(md, element, cursor, recipeName);
        } else if (element instanceof J.VariableDeclarations vd) {
            return checkVariableSuppression(vd, element, cursor, recipeName);
        }
        return false;
    }

    private static boolean checkClassSuppression(J.ClassDeclaration cd, J element, Cursor cursor, String recipeName) {
        // Check all annotations for comments (comments between annotations get attached to the next one)
        for (J.Annotation annotation : cd.getLeadingAnnotations()) {
            if (hasSuppression(annotation.getPrefix().getComments(), cursor, recipeName)) {
                logSuppression(element, recipeName);
                return true;
            }
        }

        // Check for trailing comments on class declaration (attached to body)
        // cd.getBody() is always non-null for class declarations
        if (hasSuppression(cd.getBody().getPrefix().getComments(), cursor, recipeName)) {
            logSuppression(element, recipeName);
            return true;
        }

        return false;
    }

    private static boolean checkMethodSuppression(J.MethodDeclaration md, J element, Cursor cursor, String recipeName) {
        if (!md.getLeadingAnnotations().isEmpty() &&
            hasSuppression(md.getLeadingAnnotations().getFirst().getPrefix().getComments(), cursor, recipeName)) {
            logSuppression(element, recipeName);
            return true;
        }
        return false;
    }

    private static boolean checkVariableSuppression(J.VariableDeclarations vd, J element, Cursor cursor, String recipeName) {
        if (!vd.getLeadingAnnotations().isEmpty() &&
            hasSuppression(vd.getLeadingAnnotations().getFirst().getPrefix().getComments(), cursor, recipeName)) {
            logSuppression(element, recipeName);
            return true;
        }
        return false;
    }

    /**
     * Checks if the given list of comments contains a suppression directive.
     */
    private static boolean hasSuppression(List<Comment> comments, Cursor cursor, String recipeName) {
        if (comments == null || comments.isEmpty()) {
            return false;
        }

        for (Comment comment : comments) {
            if (checkCommentForSuppression(comment, cursor, recipeName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks a single comment for suppression directive.
     */
    private static boolean checkCommentForSuppression(Comment comment, Cursor cursor, String recipeName) {
        String text = comment.printComment(cursor);
        if (!text.contains(SUPPRESSION_MARKER)) {
            return false;
        }

        // Check if it's a general suppression or recipe-specific
        if (recipeName == null) {
            return true; // General suppression
        }

        // Check for recipe-specific suppression
        String afterMarker = text.substring(text.indexOf(SUPPRESSION_MARKER) + SUPPRESSION_MARKER.length()).trim();
        if (afterMarker.isEmpty()) {
            return true; // No recipe specified means suppress all
        }

        return isRecipeNameMatch(recipeName, afterMarker);
    }

    /**
     * Checks if recipe name matches the suppression pattern.
     */
    private static boolean isRecipeNameMatch(String recipeName, String suppressionPattern) {
        // Check if the recipe name matches (simple or fully qualified)
        String simpleRecipeName = recipeName.contains(".") ?
            recipeName.substring(recipeName.lastIndexOf('.') + 1) : recipeName;
        String simplePattern = suppressionPattern.contains(".") ?
            suppressionPattern.substring(suppressionPattern.lastIndexOf('.') + 1) : suppressionPattern;

        return suppressionPattern.equals(recipeName) ||
            suppressionPattern.equals(simpleRecipeName) ||
            simplePattern.equals(simpleRecipeName);
    }

    /**
     * Logs the suppression event.
     */
    private static void logSuppression(J element, String recipeName) {
        String elementType = getElementType(element);
        String elementName = getElementName(element);

        if (recipeName != null) {
            LOG.debug("Skipping %s '%s' for recipe '%s' due to %s comment",
                elementType, elementName, recipeName, SUPPRESSION_MARKER);
        } else {
            LOG.debug("Skipping %s '%s' due to %s comment",
                elementType, elementName, SUPPRESSION_MARKER);
        }
    }

    /**
     * Gets a human-readable type name for the element.
     */
    private static String getElementType(J element) {
        if (element instanceof J.ClassDeclaration) {
            return "class";
        } else if (element instanceof J.MethodDeclaration) {
            return "method";
        } else if (element instanceof J.VariableDeclarations) {
            return "field";
        }
        return "element";
    }

    /**
     * Gets the name of the element for logging purposes.
     */
    private static String getElementName(J element) {
        if (element instanceof J.ClassDeclaration cd) {
            return cd.getSimpleName();
        } else if (element instanceof J.MethodDeclaration md) {
            return md.getSimpleName();
        } else if (element instanceof J.VariableDeclarations vd) {
            return vd.getVariables().isEmpty() ? "field" : vd.getVariables().getFirst().getSimpleName();
        }
        return "unknown";
    }

    /**
     * Helper method: checks parents within a boundary.
     */
    @SafeVarargs private static boolean checkParentsWithinBoundary(Cursor cursor, String recipeName,
        Class<? extends J> stopAtType, Class<? extends J>... parentTypes) {
        Cursor parentCursor = cursor.getParentTreeCursor();
        while (true) {
            Object value = parentCursor.getValue();
            if (!(value instanceof J parent)) {
                return false;
            }

            // Check if parent matches any of the specified types
            for (Class<? extends J> parentType : parentTypes) {
                if (parentType.isInstance(parent) && isSuppressed(parentCursor, recipeName)) {
                    return true;
                }

            }

            // Stop if we've reached the boundary type
            if (stopAtType.isInstance(parent)) {
                return false;
            }

            parentCursor = parentCursor.getParentTreeCursor();
        }
    }

    /**
     * Helper method: finds first parent of any of the specified types and checks suppression.
     */
    @SafeVarargs private static boolean checkFirstParentOfTypes(Cursor cursor, String recipeName, Class<? extends J>... parentTypes) {
        Cursor parentCursor = cursor.getParentTreeCursor();
        while (true) {
            Object value = parentCursor.getValue();
            if (!(value instanceof J parent)) {
                return false;
            }

            for (Class<? extends J> parentType : parentTypes) {
                if (parentType.isInstance(parent)) {
                    return isSuppressed(parentCursor, recipeName);
                }
            }

            parentCursor = parentCursor.getParentTreeCursor();
        }
    }

    /**
     * Checks if any parent class in the cursor tree has a suppression comment.
     * This allows class-level suppression to apply to all elements within the class.
     *
     * @param cursor The current cursor position
     * @param recipeName The recipe name to check for suppression
     * @return true if a parent class has suppression
     */
    // owolff: Refactoring would introduce complexity - hence suppressing
    @SuppressWarnings("java:S3776") private static boolean isParentClassSuppressed(Cursor cursor, String recipeName) {
        // Walk up the cursor tree looking for class declarations
        Cursor current = cursor;
        while (current != null) {
            Object value = current.getValue();
            if (value instanceof J.ClassDeclaration cd) {
                // Check if this class has suppression comment
                if (hasSuppression(cd.getPrefix().getComments(), cursor, recipeName)) {
                    LOG.debug("Found class-level suppression on class %s for recipe %s",
                        cd.getSimpleName(), recipeName);
                    return true;
                }

                // Check all annotations for suppression comments
                // Comments between annotations often get attached to the next annotation
                for (J.Annotation annotation : cd.getLeadingAnnotations()) {
                    if (hasSuppression(annotation.getPrefix().getComments(), cursor, recipeName)) {
                        LOG.debug("Found class-level suppression on class %s annotation for recipe %s",
                            cd.getSimpleName(), recipeName);
                        return true;
                    }
                }

                // Also check the class body prefix for trailing comments
                if (hasSuppression(cd.getBody().getPrefix().getComments(), cursor, recipeName)) {
                    LOG.debug("Found class-level suppression on class %s body for recipe %s",
                        cd.getSimpleName(), recipeName);
                    return true;
                }
            }

            // Check if we're at the root before trying to get parent
            if (current.getParent() == null || "root".equals(current.toString())) {
                break;
            }
            current = current.getParentTreeCursor();
        }
        return false;
    }
}