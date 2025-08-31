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
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.ChangeType;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.marker.Markers;

import java.time.Duration;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class CuiLoggerStandardsRecipe extends Recipe {

    private static final String CUI_LOGGER_TYPE = "de.cuioss.tools.logging.CuiLogger";
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%s");
    private static final Pattern INCORRECT_PLACEHOLDER_PATTERN = Pattern.compile("\\{\\}|%[dfiobxXeEgG]");

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
            for (J.VariableDeclarations.NamedVariable variable : vd.getVariables()) {
                if (!"LOGGER".equals(variable.getSimpleName())) {
                    return SearchResult.found(vd);
                }
            }
            return vd;
        }

        private J.VariableDeclarations checkLoggerModifiers(J.VariableDeclarations vd) {
            boolean hasCorrectModifiers =
                vd.hasModifier(J.Modifier.Type.Private) &&
                vd.hasModifier(J.Modifier.Type.Static) &&
                vd.hasModifier(J.Modifier.Type.Final) &&
                !vd.hasModifier(J.Modifier.Type.Public) &&
                !vd.hasModifier(J.Modifier.Type.Protected);

            if (!hasCorrectModifiers) {
                return SearchResult.found(vd);
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
            if (isExceptionType(args.get(0))) {
                hasException = true;
                if (args.size() > 1) {
                    messageArg = args.get(1);
                    messageArgIndex = 1;
                }
            } else {
                messageArg = args.get(0);
            }

            String message = extractMessageString(messageArg);
            return new LoggerCallContext(messageArg, messageArgIndex, hasException, message);
        }

        private String extractMessageString(Expression messageArg) {
            if (messageArg instanceof J.Literal) {
                Object value = ((J.Literal) messageArg).getValue();
                if (value instanceof String) {
                    return (String) value;
                }
            }
            return null;
        }

        private J.MethodInvocation checkPlaceholderPatterns(J.MethodInvocation mi, LoggerCallContext context) {
            if (context.message == null || context.messageArg == null) {
                return mi;
            }

            if (INCORRECT_PLACEHOLDER_PATTERN.matcher(context.message).find()) {
                return SearchResult.found(mi);
            }

            return mi;
        }

        private J.MethodInvocation validateParameterCount(J.MethodInvocation mi, LoggerCallContext context) {
            if (context.message == null) {
                return mi;
            }

            int placeholderCount = countPlaceholders(context.message);
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
                return SearchResult.found(mi);
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

        private int countPlaceholders(String message) {
            Matcher matcher = PLACEHOLDER_PATTERN.matcher(message);
            int count = 0;
            while (matcher.find()) {
                count++;
            }
            return count;
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