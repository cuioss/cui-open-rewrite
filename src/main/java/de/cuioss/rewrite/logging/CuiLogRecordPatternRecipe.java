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
import org.openrewrite.marker.SearchResult;

import java.time.Duration;
import java.util.Set;
import java.util.List;

public class CuiLogRecordPatternRecipe extends Recipe {

    private static final String CUI_LOGGER_TYPE = "de.cuioss.tools.logging.CuiLogger";
    private static final String LOG_RECORD_TYPE = "de.cuioss.tools.logging.LogRecord";
    private static final String PATTERN_DOC_URL = 
        "https://gitingest.com/github.com/cuioss/cui-llm-rules/tree/main/standards/logging/implementation-guide.adoc";

    @Override
    public String getDisplayName() {
        return "CUI LogRecord Pattern Validation";
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

            // Check if this is a CuiLogger method invocation
            if (!isLoggerMethod(mi)) {
                return mi;
            }

            String methodName = mi.getSimpleName();
            LogLevel level = LogLevel.fromMethodName(methodName);
            
            if (level == null) {
                return mi;
            }

            boolean usesLogRecord = checkUsesLogRecord(mi);

            // Validate based on level
            switch (level) {
                case INFO:
                case WARN:
                case ERROR:
                case FATAL:
                    if (!usesLogRecord) {
                        return SearchResult.found(mi);
                    }
                    break;
                    
                case DEBUG:
                case TRACE:
                    if (usesLogRecord) {
                        return SearchResult.found(mi);
                    }
                    break;
            }

            return mi;
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

        private boolean checkUsesLogRecord(J.MethodInvocation mi) {
            List<Expression> args = mi.getArguments();
            if (args.isEmpty()) {
                return false;
            }

            Expression firstArg = args.get(0);
            
            // Check for method reference like INFO.SOME_MESSAGE::format
            if (firstArg instanceof J.MemberReference) {
                J.MemberReference memberRef = (J.MemberReference) firstArg;
                if ("format".equals(memberRef.getReference().getSimpleName())) {
                    return isLogRecordExpression(memberRef.getContaining());
                }
            }
            
            // Check for method invocation like INFO.SOME_MESSAGE.format(...)
            if (firstArg instanceof J.MethodInvocation) {
                J.MethodInvocation formatCall = (J.MethodInvocation) firstArg;
                if ("format".equals(formatCall.getSimpleName())) {
                    Expression select = formatCall.getSelect();
                    if (select != null) {
                        return isLogRecordExpression(select);
                    }
                }
            }

            // Check if first argument is an exception (could be followed by LogRecord)
            if (isExceptionType(firstArg) && args.size() > 1) {
                Expression secondArg = args.get(1);
                
                // Check for method reference
                if (secondArg instanceof J.MemberReference) {
                    J.MemberReference memberRef = (J.MemberReference) secondArg;
                    if ("format".equals(memberRef.getReference().getSimpleName())) {
                        return isLogRecordExpression(memberRef.getContaining());
                    }
                }
                
                // Check for method invocation
                if (secondArg instanceof J.MethodInvocation) {
                    J.MethodInvocation formatCall = (J.MethodInvocation) secondArg;
                    if ("format".equals(formatCall.getSimpleName())) {
                        Expression select = formatCall.getSelect();
                        if (select != null) {
                            return isLogRecordExpression(select);
                        }
                    }
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
            
            // For identifier access (when imported statically)
            if (expr instanceof J.Identifier) {
                JavaType type = expr.getType();
                if (type != null) {
                    return TypeUtils.isAssignableTo(LOG_RECORD_TYPE, type);
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
                try {
                    return LogLevel.valueOf(methodName.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        }
    }
}