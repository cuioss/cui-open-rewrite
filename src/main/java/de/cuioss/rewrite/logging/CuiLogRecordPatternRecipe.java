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

import de.cuioss.rewrite.util.BaseSuppressionVisitor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.search.IsLikelyNotTest;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class CuiLogRecordPatternRecipe extends Recipe {

    private static final String CUI_LOGGER_TYPE = "de.cuioss.tools.logging.CuiLogger";
    private static final String LOG_RECORD_TYPE = "de.cuioss.tools.logging.LogRecord";
    private static final String FORMAT_METHOD_NAME = "format";
    private static final String PATTERN_DOC_URL =
        "https://gitingest.com/github.com/cuioss/cui-llm-rules/tree/main/standards/logging/implementation-guide.adoc";
    public static final String RECIPE_NAME = "CuiLogRecordPatternRecipe";

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
        return Preconditions.check(new IsLikelyNotTest(), new CuiLogRecordPatternVisitor());
    }

    @Override
    public List<Recipe> getRecipeList() {
        return List.of();
    }

    private static class CuiLogRecordPatternVisitor extends BaseSuppressionVisitor {

        private static final String SUPPRESSION_HINT = ". Suppress: // cui-rewrite:disable " + RECIPE_NAME;

        public CuiLogRecordPatternVisitor() {
            super(RECIPE_NAME);
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

            // Check if suppressed
            if (isSuppressed()) {
                return mi;
            }

            // Check if this is a LogRecordModel.builder().template(...) call
            J.MethodInvocation templateCheck = validateLogRecordTemplate(mi);
            if (templateCheck != mi) {
                return templateCheck;
            }

            // Transform .format() calls to direct LogRecord usage
            J.MethodInvocation transformed = transformFormatCallToDirectLogRecord(mi);
            if (transformed != mi) {
                // Return the transformed version (auto-fix)
                return transformed;
            }

            // Check if this is a CuiLogger method invocation
            if (isNotLoggerMethod(mi)) {
                return mi;
            }

            String methodName = mi.getSimpleName();
            Optional<LogLevel> levelOpt = LogLevel.fromMethodName(methodName);
            if (levelOpt.isEmpty()) {
                // Method name is not a valid log level, skip processing
                return mi;
            }
            LogLevel level = levelOpt.get();

            boolean usesLogRecord = checkUsesLogRecord(mi);

            // Check for string concatenation with LogRecord (always wrong)
            if (usesLogRecord) {
                J.MethodInvocation flagged = flagStringConcatenationWithLogRecord(mi);
                if (flagged != mi) {
                    return flagged;
                }
            }

            // Validate based on level
            switch (level) {
                case INFO, WARN, ERROR, FATAL:
                    if (!usesLogRecord) {
                        String message = "TODO: " + level + " needs LogRecord" + SUPPRESSION_HINT;
                        return mi.withMarkers(mi.getMarkers().addIfAbsent(new SearchResult(UUID.randomUUID(), message)));
                    }
                    break;

                case DEBUG, TRACE:
                    if (usesLogRecord) {
                        String message = "TODO: " + level + " no LogRecord" + SUPPRESSION_HINT;
                        return mi.withMarkers(mi.getMarkers().addIfAbsent(new SearchResult(UUID.randomUUID(), message)));
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
                // Auto-fixed, no marker needed
                return mi;
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

        private J.MethodInvocation transformFormatCallToDirectLogRecord(J.MethodInvocation mi) {
            // Check if this is a CuiLogger method invocation
            if (isNotLoggerMethod(mi)) {
                return mi;
            }

            // Don't transform if this is DEBUG or TRACE (they shouldn't use LogRecord at all)
            String methodName = mi.getSimpleName();
            if ("debug".equals(methodName) || "trace".equals(methodName)) {
                return mi;  // Will be flagged by LogRecord validation instead
            }

            List<Expression> args = mi.getArguments();
            if (args.isEmpty()) {
                return mi;
            }

            Expression firstArg = args.getFirst();

            // Check if first argument is a method reference ::format
            if (firstArg instanceof J.MemberReference memberRef && isFormatMethodReference(memberRef)) {
                return transformMethodReference(mi, memberRef, 0);
            }

            // Check if first argument is a .format() call
            if (firstArg instanceof J.MethodInvocation formatCall && isFormatCall(formatCall)) {
                return transformFormatCall(mi, formatCall, 0);
            }

            // Check for exception followed by method reference ::format
            if (isExceptionType(firstArg) && args.size() > 1) {
                Expression secondArg = args.get(1);
                if (secondArg instanceof J.MemberReference memberRef && isFormatMethodReference(memberRef)) {
                    return transformMethodReference(mi, memberRef, 1);
                }
                if (secondArg instanceof J.MethodInvocation formatCall && isFormatCall(formatCall)) {
                    return transformFormatCall(mi, formatCall, 1);
                }
            }

            return mi;
        }

        private boolean isFormatCall(J.MethodInvocation methodInvocation) {
            Expression select = methodInvocation.getSelect();
            return FORMAT_METHOD_NAME.equals(methodInvocation.getSimpleName()) &&
                select != null &&
                isLogRecordExpression(select);
        }

        private boolean isFormatMethodReference(J.MemberReference memberRef) {
            return FORMAT_METHOD_NAME.equals(memberRef.getReference().getSimpleName()) &&
                isLogRecordExpression(memberRef.getContaining());
        }

        private J.MethodInvocation transformMethodReference(J.MethodInvocation loggerCall,
            J.MemberReference memberRef,
            int memberRefIndex) {
            // Extract the LogRecord from method reference's containing expression
            Expression logRecord = memberRef.getContaining();
            if (logRecord == null) {
                return loggerCall;
            }

            // Preserve the prefix from the member reference on the LogRecord
            logRecord = logRecord.withPrefix(memberRef.getPrefix());

            // Build new argument list for logger call
            List<Expression> newLoggerArgs = new ArrayList<>(loggerCall.getArguments());
            newLoggerArgs.set(memberRefIndex, logRecord);

            return loggerCall.withArguments(newLoggerArgs);
        }

        private J.MethodInvocation transformFormatCall(J.MethodInvocation loggerCall,
            J.MethodInvocation formatCall,
            int formatCallIndex) {
            // Extract the LogRecord from format() call's select
            Expression logRecord = formatCall.getSelect();
            if (logRecord == null) {
                return loggerCall;
            }

            // Preserve the prefix from the format call on the LogRecord
            logRecord = logRecord.withPrefix(formatCall.getPrefix());

            // Get format() method's arguments
            List<Expression> formatArgs = formatCall.getArguments();
            // Filter out J.Empty instances
            List<Expression> actualFormatArgs = formatArgs.stream()
                .filter(arg -> !(arg instanceof J.Empty))
                .toList();

            // Build new argument list for logger call
            List<Expression> newLoggerArgs = new ArrayList<>(loggerCall.getArguments());
            newLoggerArgs.set(formatCallIndex, logRecord);

            // Append format() arguments to logger call, ensuring proper spacing
            if (!actualFormatArgs.isEmpty()) {
                // Ensure the first appended argument has a space prefix
                List<Expression> argsToAdd = new ArrayList<>();
                for (int i = 0; i < actualFormatArgs.size(); i++) {
                    Expression arg = actualFormatArgs.get(i);
                    if (i == 0) {
                        arg = arg.withPrefix(Space.build(" ", Collections.emptyList()));
                    }
                    argsToAdd.add(arg);
                }
                newLoggerArgs.addAll(formatCallIndex + 1, argsToAdd);
            }

            return loggerCall.withArguments(newLoggerArgs);
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

            // Check for plain LogRecord reference (new direct pattern)
            // e.g., LOGGER.info(INFO.SOME_MESSAGE, param1, param2)
            return isLogRecordExpression(expr);
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

        /**
         * Flags string concatenation when used with LogRecord parameters.
         * String concatenation with LogRecord is ALWAYS wrong because:
         * - LogRecord parameters should be passed separately for proper formatting
         * - Concatenation defeats the purpose of parameterized logging
         *
         * Example of WRONG usage:
         * LOGGER.info(INFO.MESSAGE, "text" + variable)
         *
         * Should be:
         * LOGGER.info(INFO.MESSAGE, "text", variable)
         */
        private J.MethodInvocation flagStringConcatenationWithLogRecord(J.MethodInvocation mi) {
            List<Expression> args = mi.getArguments();
            if (args.isEmpty()) {
                return mi;
            }

            // Determine the starting index for parameter checking
            // If first arg is exception, LogRecord should be second, params start at index 2
            // Otherwise, LogRecord is first, params start at index 1
            int logRecordIndex = 0;
            int paramStartIndex = 1;

            Expression firstArg = args.getFirst();
            if (isExceptionType(firstArg)) {
                logRecordIndex = 1;
                paramStartIndex = 2;
            }

            // Verify we have a LogRecord at the expected index
            if (logRecordIndex >= args.size()) {
                return mi;
            }

            Expression logRecordArg = args.get(logRecordIndex);
            if (!isLogRecordFormatExpression(logRecordArg)) {
                return mi;
            }

            // Check remaining arguments for string concatenation
            for (int i = paramStartIndex; i < args.size(); i++) {
                Expression arg = args.get(i);
                if (containsStringConcatenation(arg)) {
                    String message = "TODO: String concatenation with LogRecord parameter is always wrong. " +
                        "Use separate parameters instead" + SUPPRESSION_HINT;
                    return mi.withMarkers(mi.getMarkers().addIfAbsent(new SearchResult(UUID.randomUUID(), message)));
                }
            }

            return mi;
        }

        /**
         * Checks if an expression contains string concatenation.
         * This includes + operators on String types.
         */
        private boolean containsStringConcatenation(Expression expr) {
            if (expr instanceof J.Binary binary) {
                // Check if this is a + operator
                if (binary.getOperator() == J.Binary.Type.Addition) {
                    // Check if at least one operand is a String type
                    JavaType leftType = binary.getLeft().getType();
                    JavaType rightType = binary.getRight().getType();

                    if (isStringType(leftType) || isStringType(rightType)) {
                        return true;
                    }
                }

                // Recursively check operands
                return containsStringConcatenation(binary.getLeft()) ||
                       containsStringConcatenation(binary.getRight());
            }

            return false;
        }

        private boolean isStringType(JavaType type) {
            return type != null && TypeUtils.isString(type);
        }

        private enum LogLevel {
            TRACE, DEBUG, INFO, WARN, ERROR, FATAL;

            static Optional<LogLevel> fromMethodName(String methodName) {
                // Convert method name to uppercase and try to match
                String upperMethodName = methodName.toUpperCase();
                try {
                    return Optional.of(LogLevel.valueOf(upperMethodName));
                } catch (IllegalArgumentException e) {
                    return Optional.empty();
                }
            }
        }
    }
}