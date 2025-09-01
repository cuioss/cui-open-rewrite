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
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markers;
import org.openrewrite.marker.SearchResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class CuiLoggerStandardsRecipe extends Recipe {

    private static final String CUI_LOGGER_TYPE = "de.cuioss.tools.logging.CuiLogger";

    @Override
    public String getDisplayName() {
        return "CUI Logger Standards";
    }

    @Override
    public String getDescription() {
        return "Enforces CUI-specific logging standards including proper logger naming, " +
            "string substitution patterns, exception parameter position, parameter validation, " +
            "LogRecord pattern usage for INFO/WARN/ERROR levels, " +
            "and detection of System.out/System.err usage.";
    }

    @Override
    public Set<String> getTags() {
        return Set.of("CUI", "logging", "standards");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new CuiLoggerStandardsVisitor();
    }

    private static class CuiLoggerStandardsVisitor extends JavaIsoVisitor<ExecutionContext> {

        private UUID randomId() {
            return UUID.randomUUID();
        }

        @Override
        public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations variableDecls, ExecutionContext ctx) {
            J.VariableDeclarations vd = super.visitVariableDeclarations(variableDecls, ctx);

            if (!isLoggerField(vd)) {
                return vd;
            }

            if (RecipeSuppressionUtil.isSuppressed(vd, getCursor(), "CuiLoggerStandardsRecipe")) {
                return vd;
            }

            vd = checkLoggerNaming(vd);
            vd = checkLoggerModifiers(vd);

            return vd;
        }

        private boolean isLoggerField(J.VariableDeclarations vd) {
            return TypeUtils.isOfClassType(vd.getType(), CUI_LOGGER_TYPE);
        }

        private J.VariableDeclarations checkLoggerNaming(J.VariableDeclarations vd) {
            boolean needsRename = false;
            List<J.VariableDeclarations.NamedVariable> updatedVariables = new ArrayList<>();
            for (J.VariableDeclarations.NamedVariable variable : vd.getVariables()) {
                String name = variable.getSimpleName();
                if (!"LOGGER".equals(name)) {
                    needsRename = true;
                    // Rename the variable to LOGGER
                    J.Identifier newName = variable.getName().withSimpleName("LOGGER");
                    // Update type information if present
                    if (variable.getVariableType() != null) {
                        variable = variable.withVariableType(variable.getVariableType().withName("LOGGER"));
                    }
                    variable = variable.withName(newName);
                }
                updatedVariables.add(variable);
            }
            if (needsRename) {
                vd = vd.withVariables(updatedVariables);
                // Auto-fixed, don't mark it
            }
            return vd;
        }

        private J.VariableDeclarations checkLoggerModifiers(J.VariableDeclarations vd) {
            boolean hasPrivate = vd.hasModifier(J.Modifier.Type.Private);
            boolean hasStatic = vd.hasModifier(J.Modifier.Type.Static);
            boolean hasFinal = vd.hasModifier(J.Modifier.Type.Final);
            boolean hasPublic = vd.hasModifier(J.Modifier.Type.Public);
            boolean hasProtected = vd.hasModifier(J.Modifier.Type.Protected);

            if (!hasPrivate || !hasStatic || !hasFinal || hasPublic || hasProtected) {
                List<J.Modifier> newModifiers = new ArrayList<>();
                List<J.Modifier> otherModifiers = new ArrayList<>();

                // Collect non-visibility/static/final modifiers (like annotations)
                for (J.Modifier mod : vd.getModifiers()) {
                    if (mod.getType() != J.Modifier.Type.Public &&
                        mod.getType() != J.Modifier.Type.Protected &&
                        mod.getType() != J.Modifier.Type.Private &&
                        mod.getType() != J.Modifier.Type.Static &&
                        mod.getType() != J.Modifier.Type.Final) {
                        otherModifiers.add(mod);
                    }
                }

                // Add modifiers in correct order: private static final
                Space firstSpace = Space.EMPTY;
                if (!vd.getModifiers().isEmpty()) {
                    firstSpace = vd.getModifiers().getFirst().getPrefix();
                }

                // Always add private
                J.Modifier privateMod = new J.Modifier(
                    randomId(),
                    firstSpace,
                    Markers.EMPTY,
                    null,
                    J.Modifier.Type.Private,
                    Collections.emptyList()
                );
                newModifiers.add(privateMod);

                // Always add static
                J.Modifier staticMod = new J.Modifier(
                    randomId(),
                    Space.SINGLE_SPACE,
                    Markers.EMPTY,
                    null,
                    J.Modifier.Type.Static,
                    Collections.emptyList()
                );
                newModifiers.add(staticMod);

                // Always add final
                J.Modifier finalMod = new J.Modifier(
                    randomId(),
                    Space.SINGLE_SPACE,
                    Markers.EMPTY,
                    null,
                    J.Modifier.Type.Final,
                    Collections.emptyList()
                );
                newModifiers.add(finalMod);

                // Add back other modifiers (annotations, etc.)
                newModifiers.addAll(otherModifiers);

                vd = vd.withModifiers(newModifiers);
                // Auto-fixed, don't mark it
            }

            return vd;
        }


        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

            if (RecipeSuppressionUtil.isSuppressed(mi, getCursor(), "CuiLoggerStandardsRecipe")) {
                return mi;
            }

            // Check for System.out/err usage
            mi = checkSystemStreams(mi);
            if (mi.getMarkers().findFirst(SearchResult.class).isPresent()) {
                return mi;
            }

            // Check logger method invocations
            if (isLoggerMethod(mi)) {
                mi = validateLoggerMethodCall(mi);
            }

            return mi;
        }

        private J.MethodInvocation checkSystemStreams(J.MethodInvocation mi) {
            if (isSystemOutOrErr(mi)) {
                return SearchResult.found(mi);
            }
            return mi;
        }

        private J.MethodInvocation validateLoggerMethodCall(J.MethodInvocation mi) {
            List<Expression> args = mi.getArguments();
            if (args.isEmpty()) {
                return mi;
            }

            LoggerCallContext context = analyzeLoggerCall(mi);

            // Check placeholder patterns
            mi = checkPlaceholderPatterns(mi, context);

            // Validate parameter count
            mi = validateParameterCount(mi, context);
            if (mi.getMarkers().findFirst(SearchResult.class).isPresent()) {
                return mi;
            }

            // Check exception position for error/warn methods
            mi = checkExceptionPosition(mi);

            return mi;
        }

        private LoggerCallContext analyzeLoggerCall(J.MethodInvocation mi) {
            List<Expression> args = mi.getArguments();
            Expression messageArg = null;
            int messageArgIndex = 0;
            boolean hasException = false;

            // Check if first argument is an exception
            if (isExceptionType(args.getFirst())) {
                hasException = true;
                if (args.size() > 1) {
                    messageArg = args.get(1);
                    messageArgIndex = 1;
                }
            } else {
                messageArg = args.getFirst();
            }

            String message = extractMessageString(messageArg);
            return new LoggerCallContext(messageArg, messageArgIndex, hasException, message);
        }

        private String extractMessageString(Expression messageArg) {
            if (messageArg instanceof J.Literal literal) {
                Object value = literal.getValue();
                if (value instanceof String string) {
                    return string;
                }
            }
            return null;
        }

        private J.MethodInvocation checkPlaceholderPatterns(J.MethodInvocation mi, LoggerCallContext context) {
            if (context.message == null || context.messageArg == null) {
                return mi;
            }

            if (PlaceholderValidationUtil.hasIncorrectPlaceholders(context.message)) {
                String correctedMessage = PlaceholderValidationUtil.correctPlaceholders(context.message);
                J.Literal newLiteral = ((J.Literal) context.messageArg).withValue(correctedMessage)
                    .withValueSource("\"" + correctedMessage + "\"");
                List<Expression> newArgs = new ArrayList<>(mi.getArguments());
                newArgs.set(context.messageArgIndex, newLiteral);
                mi = mi.withArguments(newArgs);
                // Auto-fixed, don't mark it
            }

            return mi;
        }

        private J.MethodInvocation validateParameterCount(J.MethodInvocation mi, LoggerCallContext context) {
            if (context.message == null) {
                return mi;
            }

            int placeholderCount = PlaceholderValidationUtil.countPlaceholders(context.message);
            List<Expression> args = mi.getArguments();

            // Count actual substitution parameters (excluding message and exception)
            int paramCount = 0;
            for (int i = 0; i < args.size(); i++) {
                // Skip the message argument
                if (i == context.messageArgIndex) {
                    continue;
                }
                // Skip exception arguments (they don't count as substitution params)
                if (isExceptionType(args.get(i))) {
                    continue;
                }
                paramCount++;
            }

            if (placeholderCount != paramCount) {
                return SearchResult.found(mi);
            }

            return mi;
        }

        private J.MethodInvocation checkExceptionPosition(J.MethodInvocation mi) {
            String methodName = mi.getSimpleName();
            if (!isExceptionHandlingMethod(methodName)) {
                return mi;
            }

            List<Expression> args = mi.getArguments();
            if (args.size() <= 1) {
                return mi;
            }

            ExceptionPosition position = findExceptionPosition(args);
            if (position.needsReordering()) {
                // Move exception to first position
                List<Expression> reorderedArgs = new ArrayList<>();
                reorderedArgs.add(position.exception);
                for (int i = 0; i < args.size(); i++) {
                    if (i != position.index) {
                        reorderedArgs.add(args.get(i));
                    }
                }
                mi = mi.withArguments(reorderedArgs);
                // Auto-fixed, don't mark it
                return mi;
            }

            return mi;
        }

        private boolean isExceptionHandlingMethod(String methodName) {
            return "error".equals(methodName) || "warn".equals(methodName);
        }

        private ExceptionPosition findExceptionPosition(List<Expression> args) {
            for (int i = 0; i < args.size(); i++) {
                if (isExceptionType(args.get(i))) {
                    return new ExceptionPosition(i, args.get(i));
                }
            }
            return ExceptionPosition.notFound();
        }


        private static class LoggerCallContext {
            Expression messageArg;
            int messageArgIndex;
            boolean hasException;
            String message;

            LoggerCallContext(Expression messageArg, int messageArgIndex, boolean hasException, String message) {
                this.messageArg = messageArg;
                this.messageArgIndex = messageArgIndex;
                this.hasException = hasException;
                this.message = message;
            }
        }

        private static class ExceptionPosition {
            final int index;
            final Expression exception;

            ExceptionPosition(int index, Expression exception) {
                this.index = index;
                this.exception = exception;
            }

            static ExceptionPosition notFound() {
                return new ExceptionPosition(-1, null);
            }

            boolean needsReordering() {
                return index > 0;
            }
        }

        private boolean isLoggerMethod(J.MethodInvocation mi) {
            if (mi.getSelect() == null) {
                return false;
            }
            JavaType.Method methodType = mi.getMethodType();
            if (methodType != null && methodType.getDeclaringType() != null) {
                return TypeUtils.isOfClassType(methodType.getDeclaringType(), CUI_LOGGER_TYPE);
            }
            return false;
        }

        private boolean isExceptionType(Expression expr) {
            JavaType type = expr.getType();
            return type != null && TypeUtils.isAssignableTo("java.lang.Throwable", type);
        }


        private boolean isSystemOutOrErr(J.MethodInvocation mi) {
            if (mi.getSelect() instanceof J.FieldAccess fieldAccess) {
                if (fieldAccess.getTarget() instanceof J.Identifier target) {
                    String targetName = target.getSimpleName();
                    String fieldName = fieldAccess.getSimpleName();

                    if ("System".equals(targetName) &&
                        ("out".equals(fieldName) || "err".equals(fieldName))) {
                        String methodName = mi.getSimpleName();
                        return "print".equals(methodName) || "println".equals(methodName) ||
                            "printf".equals(methodName) || "format".equals(methodName);
                    }
                }
            }
            return false;
        }


    }
}