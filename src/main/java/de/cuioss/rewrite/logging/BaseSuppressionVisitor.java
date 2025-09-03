package de.cuioss.rewrite.logging;

import de.cuioss.rewrite.util.RecipeSuppressionUtil;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

/**
 * Base visitor that provides common suppression handling for recipe visitors.
 */
public abstract class BaseSuppressionVisitor extends JavaIsoVisitor<ExecutionContext> {
    
    private final String recipeName;
    
    protected BaseSuppressionVisitor(String recipeName) {
        this.recipeName = recipeName;
    }
    
    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
        // Check for class-level suppression
        if (RecipeSuppressionUtil.isSuppressed(getCursor(), recipeName)) {
            // Skip the entire class and its children by not calling super
            return classDecl;
        }
        return super.visitClassDeclaration(classDecl, ctx);
    }
    
    @Override
    public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
        // Check for method-level suppression
        if (RecipeSuppressionUtil.isSuppressed(getCursor(), recipeName)) {
            // Skip the entire method without visiting children
            return method;
        }
        return super.visitMethodDeclaration(method, ctx);
    }
}