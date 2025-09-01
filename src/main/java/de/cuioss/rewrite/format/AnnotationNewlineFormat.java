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
package de.cuioss.rewrite.format;

import de.cuioss.rewrite.util.RecipeSuppressionUtil;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AnnotationNewlineFormat extends Recipe {

    @Override
    public String getDisplayName() {
        return "Format annotations with newlines";
    }

    @Override
    public String getDescription() {
        return "Ensures type-level and method-level annotations are on separate lines.";
    }

    @Override
    public Set<String> getTags() {
        return Set.of("CUI", "format", "annotation");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new AnnotationNewlineFormatVisitor();
    }

    private static class AnnotationNewlineFormatVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {

            // Check for suppression comments
            if (RecipeSuppressionUtil.isSuppressed(classDecl, getCursor(), "AnnotationNewlineFormat")) {
                return classDecl;
            }

            J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

            // Only process if there are annotations and changes are needed
            if (cd.getLeadingAnnotations().isEmpty() || !needsFormatting(cd)) {
                return cd;
            }

            // Format annotations - ensure each on separate line
            cd = cd.withLeadingAnnotations(formatAnnotationList(cd.getLeadingAnnotations()));

            // Ensure newline after last annotation with proper indentation
            cd = ensureProperSpacingAfterAnnotations(cd);

            return cd;
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
            // Check for suppression comments
            if (RecipeSuppressionUtil.isSuppressed(method, getCursor(), "AnnotationNewlineFormat")) {
                return method;
            }

            J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);

            // Only process if there are annotations and changes are needed
            if (md.getLeadingAnnotations().isEmpty() || !needsMethodFormatting(md)) {
                return md;
            }

            // Format annotations - ensure each on separate line
            md = md.withLeadingAnnotations(formatAnnotationList(md.getLeadingAnnotations()));

            // Ensure newline after last annotation with proper indentation
            md = ensureProperMethodSpacing(md);

            return md;
        }

        @Override
        public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
            // Check for suppression only on fields
            if (isFieldDeclaration() && RecipeSuppressionUtil.isSuppressed(multiVariable, getCursor(), "AnnotationNewlineFormat")) {
                return multiVariable;
            }

            J.VariableDeclarations vd = super.visitVariableDeclarations(multiVariable, ctx);

            // Only process field declarations (not local variables)
            if (!isFieldDeclaration() || vd.getLeadingAnnotations().isEmpty()) {
                return vd;
            }

            // Check if formatting is needed
            if (!needsFieldFormatting(vd)) {
                return vd;
            }

            // Format annotations - ensure each on separate line
            vd = vd.withLeadingAnnotations(formatAnnotationList(vd.getLeadingAnnotations()));

            // Ensure newline after last annotation with proper indentation
            vd = ensureProperFieldSpacing(vd);

            return vd;
        }

        private boolean needsFormatting(J.ClassDeclaration cd) {
            // Check if annotations need to be on separate lines
            if (cd.getLeadingAnnotations().size() > 1) {
                for (int i = 1; i < cd.getLeadingAnnotations().size(); i++) {
                    if (!cd.getLeadingAnnotations().get(i).getPrefix().getWhitespace().contains("\n")) {
                        return true;
                    }
                }
            }

            // Check if there needs to be a newline after annotations
            if (!cd.getModifiers().isEmpty()) {
                return !cd.getModifiers().getFirst().getPrefix().getWhitespace().contains("\n");
            }

            return false;
        }

        private boolean needsMethodFormatting(J.MethodDeclaration md) {
            // Check if annotations need to be on separate lines
            if (md.getLeadingAnnotations().size() > 1) {
                for (int i = 1; i < md.getLeadingAnnotations().size(); i++) {
                    if (!md.getLeadingAnnotations().get(i).getPrefix().getWhitespace().contains("\n")) {
                        return true;
                    }
                }
            }

            // Check if there needs to be a newline after annotations
            if (!md.getModifiers().isEmpty()) {
                return !md.getModifiers().getFirst().getPrefix().getWhitespace().contains("\n");
            } else {
                var returnType = md.getReturnTypeExpression();
                return returnType != null && !returnType.getPrefix().getWhitespace().contains("\n");
            }
        }

        private boolean needsFieldFormatting(J.VariableDeclarations vd) {
            // Check if annotations need to be on separate lines
            if (vd.getLeadingAnnotations().size() > 1) {
                for (int i = 1; i < vd.getLeadingAnnotations().size(); i++) {
                    if (!vd.getLeadingAnnotations().get(i).getPrefix().getWhitespace().contains("\n")) {
                        return true;
                    }
                }
            }

            // Check if there needs to be a newline after annotations
            if (!vd.getModifiers().isEmpty()) {
                return !vd.getModifiers().getFirst().getPrefix().getWhitespace().contains("\n");
            } else {
                var typeExpr = vd.getTypeExpression();
                return typeExpr != null && !typeExpr.getPrefix().getWhitespace().contains("\n");
            }
        }

        private List<J.Annotation> formatAnnotationList(List<J.Annotation> annotations) {
            if (annotations.size() <= 1) {
                return annotations;
            }

            List<J.Annotation> result = new ArrayList<>();
            result.add(annotations.getFirst()); // Keep first annotation as-is

            // Get base indentation from first annotation
            String baseIndent = getIndentationFromPrefix(annotations.getFirst().getPrefix());

            // Format subsequent annotations
            for (int i = 1; i < annotations.size(); i++) {
                J.Annotation annotation = annotations.get(i);
                String currentWhitespace = annotation.getPrefix().getWhitespace();

                // Only change if not already on new line
                if (!currentWhitespace.contains("\n")) {
                    annotation = annotation.withPrefix(
                        Space.format("\n" + baseIndent)
                    );
                }
                result.add(annotation);
            }

            return result;
        }

        private J.ClassDeclaration ensureProperSpacingAfterAnnotations(J.ClassDeclaration cd) {
            if (!cd.getModifiers().isEmpty()) {
                J.Modifier firstMod = cd.getModifiers().getFirst();
                String currentWhitespace = firstMod.getPrefix().getWhitespace();

                if (!currentWhitespace.contains("\n")) {
                    // Get indentation from annotations
                    String indent = getIndentationFromPrefix(cd.getLeadingAnnotations().getFirst().getPrefix());

                    List<J.Modifier> newModifiers = new ArrayList<>(cd.getModifiers());
                    newModifiers.set(0, firstMod.withPrefix(Space.format("\n" + indent)));
                    cd = cd.withModifiers(newModifiers);
                }
            }
            return cd;
        }

        private J.MethodDeclaration ensureProperMethodSpacing(J.MethodDeclaration md) {
            if (!md.getModifiers().isEmpty()) {
                J.Modifier firstMod = md.getModifiers().getFirst();
                if (needsNewline(firstMod.getPrefix())) {
                    md = md.withModifiers(
                        updateFirstModifierSpacing(md.getModifiers(),
                            getIndentationFromPrefix(md.getLeadingAnnotations().getFirst().getPrefix()))
                    );
                }
            } else {
                // Method without modifiers - check return type
                var returnType = md.getReturnTypeExpression();
                if (returnType != null) {
                    String currentWhitespace = returnType.getPrefix().getWhitespace();

                    if (!currentWhitespace.contains("\n")) {
                        String indent = getIndentationFromPrefix(md.getLeadingAnnotations().getFirst().getPrefix());
                        md = md.withReturnTypeExpression(
                            returnType.withPrefix(Space.format("\n" + indent))
                        );
                    }
                }
            }
            return md;
        }

        private J.VariableDeclarations ensureProperFieldSpacing(J.VariableDeclarations vd) {
            if (!vd.getModifiers().isEmpty()) {
                J.Modifier firstMod = vd.getModifiers().getFirst();
                if (needsNewline(firstMod.getPrefix())) {
                    vd = vd.withModifiers(
                        updateFirstModifierSpacing(vd.getModifiers(),
                            getIndentationFromPrefix(vd.getLeadingAnnotations().getFirst().getPrefix()))
                    );
                }
            } else {
                // Field without modifiers - check type
                var typeExpr = vd.getTypeExpression();
                if (typeExpr != null) {
                    String currentWhitespace = typeExpr.getPrefix().getWhitespace();

                    if (!currentWhitespace.contains("\n")) {
                        String indent = getIndentationFromPrefix(vd.getLeadingAnnotations().getFirst().getPrefix());
                        vd = vd.withTypeExpression(
                            typeExpr.withPrefix(Space.format("\n" + indent))
                        );
                    }
                }
            }
            return vd;
        }

        /**
         * Helper method to update the spacing of the first modifier in a list.
         * This eliminates duplicated code between method and field spacing.
         */
        private List<J.Modifier> updateFirstModifierSpacing(List<J.Modifier> modifiers, String indent) {
            if (modifiers.isEmpty()) {
                return modifiers;
            }

            J.Modifier firstMod = modifiers.getFirst();
            String currentWhitespace = firstMod.getPrefix().getWhitespace();

            if (!currentWhitespace.contains("\n")) {
                List<J.Modifier> newModifiers = new ArrayList<>(modifiers);
                newModifiers.set(0, firstMod.withPrefix(Space.format("\n" + indent)));
                return newModifiers;
            }

            return modifiers;
        }

        private String getIndentationFromPrefix(Space space) {
            String whitespace = space.getWhitespace();
            int lastNewline = whitespace.lastIndexOf('\n');
            if (lastNewline >= 0) {
                return whitespace.substring(lastNewline + 1);
            }
            return whitespace;
        }

        private boolean needsNewline(@Nullable Space space) {
            return space == null || !space.getWhitespace().contains("\n");
        }

        private boolean isFieldDeclaration() {
            Object parent = getCursor().getParentOrThrow().getValue();
            if (parent instanceof J.Block) {
                Object grandParent = getCursor().getParentOrThrow().getParentOrThrow().getValue();
                return grandParent instanceof J.ClassDeclaration;
            }
            return parent instanceof J.ClassDeclaration;
        }

    }
}