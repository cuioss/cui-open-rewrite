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
import org.openrewrite.java.tree.JLeftPadded;
import org.openrewrite.java.tree.JRightPadded;
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

public class CuiLogRecordPatternRecipe extends Recipe {

    private static final String CUI_LOGGER_TYPE = "de.cuioss.tools.logging.CuiLogger";
    private static final String LOG_RECORD_TYPE = "de.cuioss.tools.logging.LogRecord";
    private static final String FORMAT_METHOD_NAME = "format";
    private static final String PATTERN_DOC_URL =
        "https://gitingest.com/github.com/cuioss/cui-llm-rules/tree/main/standards/logging/implementation-guide.adoc";

    @Override
    public String getDisplayName() {
        return "CUI LogRecord pattern validation";
    }

    @Override
    public String getDescription() {
        return "Enforces proper usage of LogRecord pattern: " +
            "mandatory for INFO/WARN/ERROR/FATAL levels, " +
            "forbidden for DEBUG/TRACE levels. " +
            "See: " + PATTERN_DOC_URL + ".";
    }

    @Override
    public Set<String> getTags() {
        return Set.of("CUI", "logging", "LogRecord", "standards");
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(3);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new CuiLogRecordPatternVisitor();
    }

    private static class CuiLogRecordPatternVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

            // Check if suppressed
            if (RecipeSuppressionUtil.isSuppressed(mi, getCursor(), "CuiLogRecordPatternRecipe")) {
                return mi;
            }

            // Check if this is a LogRecordModel.builder().template(...) call
            J.MethodInvocation templateCheck = validateLogRecordTemplate(mi);
            if (templateCheck != mi) {
                return templateCheck;
            }

            // Check for zero-parameter format() calls that can be converted to method references
            J.MethodInvocation converted = convertZeroParamFormatToMethodReference(mi);
            if (converted != mi) {
                // If we made a conversion, return it with the marker
                return converted;
            }

            // Check if this is a CuiLogger method invocation
            if (isNotLoggerMethod(mi)) {
                return mi;
            }

            String methodName = mi.getSimpleName();
            LogLevel level = LogLevel.fromMethodName(methodName);

            // level will never be null here as we already checked isNotLoggerMethod()
            // which returned false, ensuring mi is a CuiLogger method, and we handle all log levels

            boolean usesLogRecord = checkUsesLogRecord(mi);

            // Validate based on level
            switch (level) {
                case INFO, WARN, ERROR, FATAL:
                    if (!usesLogRecord) {
                        return mi.withMarkers(mi.getMarkers().addIfAbsent(new SearchResult(UUID.randomUUID(), null)));
                    }
                    break;

                case DEBUG, TRACE:
                    if (usesLogRecord) {
                        return mi.withMarkers(mi.getMarkers().addIfAbsent(new SearchResult(UUID.randomUUID(), null)));
                    }
                    break;
            }

            return mi;
        }

        private J.MethodInvocation validateLogRecordTemplate(J.MethodInvocation mi) {
            // Check if this is a .template("...") method call
            if (!"template".equals(mi.getSimpleName())) {
                return mi;
            }

            // Check if it's called on a LogRecordModel.Builder
            Expression select = mi.getSelect();
            if (select == null || !isLogRecordBuilderType(select)) {
                return mi;
            }

            // Get the template string argument
            List<Expression> args = mi.getArguments();
            if (args.size() != 1) {
                return mi;
            }

            Expression templateArg = args.getFirst();
            if (templateArg instanceof J.Literal literal &&
                literal.getValue() instanceof String template &&
                PlaceholderValidationUtil.hasIncorrectPlaceholders(template)) {
                // Check for incorrect placeholders and auto-fix them
                String correctedTemplate = PlaceholderValidationUtil.correctPlaceholders(template);
                J.Literal newLiteral = literal.withValue(correctedTemplate)
                    .withValueSource("\"" + correctedTemplate + "\"");
                List<Expression> newArgs = new ArrayList<>(args);
                newArgs.set(0, newLiteral);
                mi = mi.withArguments(newArgs);
                return mi.withMarkers(mi.getMarkers().addIfAbsent(new SearchResult(UUID.randomUUID(), "Fixed incorrect placeholder pattern in LogRecord template")));
            }

            return mi;
        }

        private boolean isLogRecordBuilderType(Expression expr) {
            JavaType type = expr.getType();
            if (type == null) {
                return false;
            }
            // Check if it's LogRecordModel.Builder type
            return TypeUtils.isOfClassType(type, "de.cuioss.tools.logging.LogRecordModel$Builder") ||
                TypeUtils.isOfClassType(type, "de.cuioss.tools.logging.LogRecordModel.Builder");
        }

        private J.MethodInvocation convertZeroParamFormatToMethodReference(J.MethodInvocation mi) {
            // Check if this is a CuiLogger method invocation
            if (isNotLoggerMethod(mi)) {
                return mi;
            }

            // Don't convert if this is DEBUG or TRACE (they shouldn't use LogRecord at all)
            String methodName = mi.getSimpleName();
            if ("debug".equals(methodName) || "trace".equals(methodName)) {
                return mi;  // Will be flagged by LogRecord validation instead
            }

            List<Expression> args = mi.getArguments();
            if (args.isEmpty()) {
                return mi;
            }

            Expression firstArg = args.getFirst();

            // Check if first argument is a zero-parameter format() call
            if (firstArg instanceof J.MethodInvocation formatCall && isZeroParamFormatCall(formatCall)) {
                J.MemberReference methodRef = createFormatMethodReference(formatCall, true);
                List<Expression> newArgs = new ArrayList<>(args);
                newArgs.set(0, methodRef);
                mi = mi.withArguments(newArgs);
                return mi.withMarkers(mi.getMarkers().addIfAbsent(new SearchResult(UUID.randomUUID(), "Converted zero-parameter format() call to method reference")));
            }

            // Check for exception followed by zero-parameter format() call
            if (isExceptionType(firstArg) && args.size() > 1) {
                Expression secondArg = args.get(1);
                if (secondArg instanceof J.MethodInvocation formatCall && isZeroParamFormatCall(formatCall)) {
                    J.MemberReference methodRef = createFormatMethodReference(formatCall, false);
                    List<Expression> newArgs = new ArrayList<>(args);
                    newArgs.set(1, methodRef);
                    mi = mi.withArguments(newArgs);
                    return mi.withMarkers(mi.getMarkers().addIfAbsent(new SearchResult(UUID.randomUUID(), "Converted zero-parameter format() call to method reference")));
                }
            }

            return mi;
        }

        private boolean isZeroParamFormatCall(J.MethodInvocation formatCall) {
            boolean hasNoArgs = formatCall.getArguments().isEmpty() ||
                (formatCall.getArguments().size() == 1 && formatCall.getArguments().getFirst() instanceof J.Empty);
            return FORMAT_METHOD_NAME.equals(formatCall.getSimpleName()) &&
                hasNoArgs &&
                formatCall.getSelect() != null;
        }

        private J.MemberReference createFormatMethodReference(J.MethodInvocation formatCall, boolean preservePrefix) {
            Expression selectBase = formatCall.getSelect();
            // We already checked that getSelect() != null in isZeroParamFormatCall
            // but add null check to satisfy static analysis
            if (selectBase == null) {
                throw new IllegalStateException("formatCall.getSelect() should not be null");
            }
            Expression select = preservePrefix ?
                selectBase.withPrefix(formatCall.getPrefix()) :
                selectBase;

            return new J.MemberReference(
                UUID.randomUUID(),
                formatCall.getPrefix(),
                formatCall.getMarkers(),
                JRightPadded.build(select),
                null, // No type parameters
                JLeftPadded.build(new J.Identifier(
                    UUID.randomUUID(),
                    Space.EMPTY,
                    Markers.EMPTY,
                    Collections.emptyList(),
                    FORMAT_METHOD_NAME,
                    null,
                    null
                )),
                formatCall.getType(),
                formatCall.getMethodType(),
                null // No variable
            );
        }

        private boolean isNotLoggerMethod(J.MethodInvocation mi) {
            if (mi.getSelect() == null) {
                return true;
            }
            JavaType.Method methodType = mi.getMethodType();
            if (methodType != null) {
                JavaType declaringType = methodType.getDeclaringType();
                return !TypeUtils.isOfClassType(declaringType, CUI_LOGGER_TYPE);
            }
            return true;
        }

        private boolean checkUsesLogRecord(J.MethodInvocation mi) {
            List<Expression> args = mi.getArguments();
            if (args.isEmpty()) {
                return false;
            }

            Expression firstArg = args.getFirst();

            // Check if first argument is a LogRecord pattern
            if (isLogRecordFormatExpression(firstArg)) {
                return true;
            }

            // Check if first argument is an exception followed by LogRecord
            if (isExceptionType(firstArg) && args.size() > 1) {
                Expression secondArg = args.get(1);
                return isLogRecordFormatExpression(secondArg);
            }

            return false;
        }

        private boolean isLogRecordFormatExpression(Expression expr) {
            // Check for method reference like INFO.SOME_MESSAGE::format
            if (expr instanceof J.MemberReference memberRef &&
                FORMAT_METHOD_NAME.equals(memberRef.getReference().getSimpleName())) {
                return isLogRecordExpression(memberRef.getContaining());
            }

            // Check for method invocation like INFO.SOME_MESSAGE.format(...)
            if (expr instanceof J.MethodInvocation formatCall &&
                FORMAT_METHOD_NAME.equals(formatCall.getSimpleName())) {
                Expression select = formatCall.getSelect();
                if (select != null) {
                    return isLogRecordExpression(select);
                }
            }

            return false;
        }

        private boolean isLogRecordExpression(Expression expr) {
            // This checks if the expression is accessing a LogRecord constant
            // It could be a field access like INFO.SOME_MESSAGE
            if (expr instanceof J.FieldAccess) {
                JavaType type = expr.getType();
                if (type != null) {
                    return TypeUtils.isAssignableTo(LOG_RECORD_TYPE, type);
                }
            }

            // For identifier access (when imported statically or local variable)
            if (expr instanceof J.Identifier) {
                JavaType type = expr.getType();
                if (type != null) {
                    // Check both LogRecord interface and LogRecordModel implementation
                    return TypeUtils.isAssignableTo(LOG_RECORD_TYPE, type) ||
                        TypeUtils.isAssignableTo("de.cuioss.tools.logging.LogRecordModel", type);
                }
            }

            return false;
        }

        private boolean isExceptionType(Expression expr) {
            JavaType type = expr.getType();
            return type != null && TypeUtils.isAssignableTo("java.lang.Throwable", type);
        }

        private enum LogLevel {
            TRACE, DEBUG, INFO, WARN, ERROR, FATAL;

            static LogLevel fromMethodName(String methodName) {
                // This should not throw as we already checked isNotLoggerMethod
                // which ensures this is a valid CuiLogger method
                return LogLevel.valueOf(methodName.toUpperCase());
            }
        }
    }
}