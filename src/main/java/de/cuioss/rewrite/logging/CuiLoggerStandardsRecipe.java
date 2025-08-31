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
            
            // Check if this is a CuiLogger field
            if (TypeUtils.isOfClassType(vd.getType(), CUI_LOGGER_TYPE)) {
                // Check if suppressed
                if (RecipeSuppressionUtil.isSuppressed(vd, getCursor(), "CuiLoggerStandardsRecipe")) {
                    return vd;
                }
                
                // Fix logger naming convention
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
                    vd = SearchResult.found(vd, "Renamed logger field to 'LOGGER'");
                }
                
                // Fix modifiers to private static final
                boolean hasPrivate = vd.hasModifier(J.Modifier.Type.Private);
                boolean hasStatic = vd.hasModifier(J.Modifier.Type.Static);
                boolean hasFinal = vd.hasModifier(J.Modifier.Type.Final);
                boolean hasPublic = vd.hasModifier(J.Modifier.Type.Public);
                boolean hasProtected = vd.hasModifier(J.Modifier.Type.Protected);
                
                if (!hasPrivate || !hasStatic || !hasFinal || hasPublic || hasProtected) {
                    List<J.Modifier> newModifiers = new ArrayList<>();
                    
                    // Add modifiers in correct order: private static final
                    boolean addedPrivate = false;
                    boolean addedStatic = false;
                    boolean addedFinal = false;
                    
                    for (J.Modifier mod : vd.getModifiers()) {
                        // Skip visibility modifiers we don't want
                        if (mod.getType() == J.Modifier.Type.Public || 
                            mod.getType() == J.Modifier.Type.Protected) {
                            continue;
                        }
                        
                        // Keep or add private
                        if (mod.getType() == J.Modifier.Type.Private) {
                            if (!addedPrivate) {
                                newModifiers.add(mod);
                                addedPrivate = true;
                            }
                        }
                        // Keep or add static
                        else if (mod.getType() == J.Modifier.Type.Static) {
                            if (!addedStatic) {
                                newModifiers.add(mod);
                                addedStatic = true;
                            }
                        }
                        // Keep or add final
                        else if (mod.getType() == J.Modifier.Type.Final) {
                            if (!addedFinal) {
                                newModifiers.add(mod);
                                addedFinal = true;
                            }
                        }
                        // Keep other modifiers (annotations, etc.)
                        else {
                            newModifiers.add(mod);
                        }
                    }
                    
                    // Add missing modifiers
                    if (!addedPrivate) {
                        // Get proper spacing from existing modifiers or default
                        Space space = Space.EMPTY;
                        if (!vd.getModifiers().isEmpty()) {
                            space = vd.getModifiers().get(0).getPrefix();
                        }
                        J.Modifier privateMod = new J.Modifier(
                            randomId(),
                            space,
                            Markers.EMPTY,
                            null,
                            J.Modifier.Type.Private,
                            Collections.emptyList()
                        );
                        newModifiers.add(0, privateMod);
                    }
                    if (!addedStatic) {
                        J.Modifier staticMod = new J.Modifier(
                            randomId(),
                            Space.SINGLE_SPACE,
                            Markers.EMPTY,
                            null,
                            J.Modifier.Type.Static,
                            Collections.emptyList()
                        );
                        // Insert in correct position (after private)
                        int insertPos = addedPrivate ? 1 : 0;
                        if (insertPos <= newModifiers.size()) {
                            newModifiers.add(insertPos, staticMod);
                            addedStatic = true;
                        }
                    }
                    if (!addedFinal) {
                        J.Modifier finalMod = new J.Modifier(
                            randomId(),
                            Space.SINGLE_SPACE,
                            Markers.EMPTY,
                            null,
                            J.Modifier.Type.Final,
                            Collections.emptyList()
                        );
                        // Add at position after static (or after private if no static)
                        int insertPos = (addedPrivate ? 1 : 0) + (addedStatic ? 1 : 0);
                        if (insertPos <= newModifiers.size()) {
                            newModifiers.add(insertPos, finalMod);
                        }
                    }
                    
                    vd = vd.withModifiers(newModifiers);
                    vd = SearchResult.found(vd, "Fixed logger modifiers to 'private static final'");
                }
            }
            
            return vd;
        }
        
        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
            
            // Check if suppressed
            if (RecipeSuppressionUtil.isSuppressed(mi, getCursor(), "CuiLoggerStandardsRecipe")) {
                return mi;
            }
            
            // Check if this is System.out or System.err method call
            if (isSystemOutOrErr(mi)) {
                String stream = getSystemStream(mi);
                return SearchResult.found(mi,
                    String.format("Inappropriate use of %s detected. Use proper logging framework instead.", stream));
            }
            
            // Check if this is a CuiLogger method invocation
            if (isLoggerMethod(mi)) {
                String methodName = mi.getSimpleName();
                List<Expression> args = mi.getArguments();
                
                if (!args.isEmpty()) {
                    // Check for string message argument
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
                    
                    if (messageArg instanceof J.Literal && ((J.Literal) messageArg).getValue() instanceof String) {
                        String message = (String) ((J.Literal) messageArg).getValue();
                        String originalMessage = message;
                        
                        // Check for incorrect placeholder patterns and replace them
                        if (INCORRECT_PLACEHOLDER_PATTERN.matcher(message).find()) {
                            String correctedMessage = message.replaceAll("\\{\\}", "%s")
                                           .replaceAll("%[dfiobxXeEgG]", "%s");
                            J.Literal newLiteral = ((J.Literal) messageArg).withValue(correctedMessage)
                                                                           .withValueSource("\"" + correctedMessage + "\"");
                            List<Expression> newArgs = new ArrayList<>(args);
                            newArgs.set(messageArgIndex, newLiteral);
                            mi = mi.withArguments(newArgs);
                            mi = SearchResult.found(mi,
                                "Replaced incorrect placeholder pattern with %s");
                            // Update message for further checks
                            message = correctedMessage;
                        }
                        
                        // Validate parameter count
                        int placeholderCount = countPlaceholders(message);
                        int paramCount = args.size() - messageArgIndex - 1;
                        if (hasException) {
                            paramCount = args.size() - 2; // Exclude exception and message
                        }
                        
                        if (placeholderCount != paramCount) {
                            return SearchResult.found(mi,
                                String.format("Parameter count mismatch: %d placeholders but %d parameters", 
                                            placeholderCount, paramCount));
                        }
                    }
                    
                    // Fix exception parameter position for error/warn methods
                    if ((methodName.equals("error") || methodName.equals("warn")) && args.size() > 1) {
                        int exceptionIndex = -1;
                        Expression exceptionArg = null;
                        
                        // Find exception parameter
                        for (int i = 0; i < args.size(); i++) {
                            if (isExceptionType(args.get(i))) {
                                exceptionIndex = i;
                                exceptionArg = args.get(i);
                                break;
                            }
                        }
                        
                        // If exception exists but not at first position, move it
                        if (exceptionIndex > 0) {
                            List<Expression> reorderedArgs = new ArrayList<>();
                            reorderedArgs.add(exceptionArg);
                            for (int i = 0; i < args.size(); i++) {
                                if (i != exceptionIndex) {
                                    reorderedArgs.add(args.get(i));
                                }
                            }
                            mi = mi.withArguments(reorderedArgs);
                            return SearchResult.found(mi,
                                "Moved exception parameter to first position");
                        }
                    }
                }
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
            if (mi.getSelect() instanceof J.FieldAccess) {
                J.FieldAccess fieldAccess = (J.FieldAccess) mi.getSelect();
                if (fieldAccess.getTarget() instanceof J.Identifier) {
                    J.Identifier target = (J.Identifier) fieldAccess.getTarget();
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
        
        private String getSystemStream(J.MethodInvocation mi) {
            if (mi.getSelect() instanceof J.FieldAccess) {
                J.FieldAccess fieldAccess = (J.FieldAccess) mi.getSelect();
                String fieldName = fieldAccess.getSimpleName();
                return "System." + fieldName;
            }
            return "System.out/err";
        }
    }
}