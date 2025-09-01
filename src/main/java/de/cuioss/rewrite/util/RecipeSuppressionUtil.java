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
     * Checks if the given element should be suppressed for a specific recipe.
     * 
     * @param element The AST element to check
     * @param cursor The current cursor position  
     * @param recipeName The simple or fully qualified name of the recipe to check (null for any recipe)
     * @return true if the element should be suppressed
     */
    public static boolean isSuppressed(J element, Cursor cursor, String recipeName) {
        // Check for preceding comments (line before)
        if (hasSuppression(element.getPrefix().getComments(), cursor, recipeName)) {
            logSuppression(element, recipeName);
            return true;
        }

        // Check annotations and element-specific locations
        return checkElementSpecificSuppression(element, cursor, recipeName);
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
        // Check first annotation's comments
        if (!cd.getLeadingAnnotations().isEmpty() &&
            hasSuppression(cd.getLeadingAnnotations().getFirst().getPrefix().getComments(), cursor, recipeName)) {
            logSuppression(element, recipeName);
            return true;
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
            String text = comment.printComment(cursor);
            if (text.contains(SUPPRESSION_MARKER)) {
                // Check if it's a general suppression or recipe-specific
                if (recipeName == null) {
                    return true; // General suppression
                }

                // Check for recipe-specific suppression
                String afterMarker = text.substring(text.indexOf(SUPPRESSION_MARKER) + SUPPRESSION_MARKER.length()).trim();
                if (afterMarker.isEmpty()) {
                    return true; // No recipe specified means suppress all
                }

                // Check if the recipe name matches (simple or fully qualified)
                String simpleRecipeName = recipeName.contains(".") ?
                    recipeName.substring(recipeName.lastIndexOf('.') + 1) : recipeName;
                String simpleAfterMarker = afterMarker.contains(".") ?
                    afterMarker.substring(afterMarker.lastIndexOf('.') + 1) : afterMarker;

                return afterMarker.equals(recipeName) || afterMarker.equals(simpleRecipeName) ||
                    simpleAfterMarker.equals(simpleRecipeName);
            }
        }

        return false;
    }

    /**
     * Logs the suppression event.
     */
    private static void logSuppression(J element, String recipeName) {
        String elementType = getElementType(element);
        String elementName = getElementName(element);

        if (recipeName != null) {
            LOG.info("Skipping {} '{}' for recipe '{}' due to {} comment",
                elementType, elementName, recipeName, SUPPRESSION_MARKER);
        } else {
            LOG.info("Skipping {} '{}' due to {} comment",
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
}