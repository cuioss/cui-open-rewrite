# CUI Custom OpenRewrite Recipes

## Overview

This document describes the custom OpenRewrite recipe project for CUI-OSS that addresses specific formatting and code transformation requirements that cannot be satisfied by existing OpenRewrite recipes.

## Motivation

The primary driver for this project is the need to enforce specific annotation formatting rules:
- Type-level annotations (`@UtilityClass`, `@Slf4j`, etc.) must be on separate lines
- Method-level annotations (`@Override`, `@Test`, etc.) must be on separate lines
- Field-level annotations must be on separate lines
- No existing OpenRewrite recipe or Java formatter provides this capability without excessive complexity

## Current Implementation Status

### ✅ Completed: Annotation Newline Format Recipe

The `AnnotationNewlineFormat` recipe has been implemented and is functional with the following features:

- **Class-level annotations**: Successfully formats annotations to be on separate lines
- **Method-level annotations**: Partially working (see known issues)
- **Field-level annotations**: Partially working (see known issues)
- **Idempotency**: Recipe does not make unnecessary changes on repeated runs
- **Build integration**: Successfully integrated with Maven build

#### Known Issues

Currently, 6 tests are disabled due to indentation preservation issues in nested contexts:
1. Method annotations lose proper indentation in some cases
2. Field annotations formatting has edge cases
3. Nested class annotations don't preserve parent indentation correctly

See [GitHub Issue #1](https://github.com/cuioss/cui-open-rewrite/issues/1) for detailed tracking of these issues.

## Future Recipes

### CUI Logger Standards Recipe (Not Yet Implemented)

#### Requirements and Specifications

This recipe will enforce CUI-specific logging standards with the following strict requirements:

1. **Logger Type Detection**
   - Recipe MUST only apply to `de.cuioss.tools.logging.CuiLogger` instances
   - Skip transformation for SLF4J, JUL, Log4j, or any other logging framework

2. **Logger Naming Convention**
   - Logger field MUST be named `LOGGER` (all uppercase)
   - Logger MUST be declared as `private static final`

3. **String Substitution Pattern**
   - MUST use `%s` for all string substitutions
   - Replace incorrect patterns: `{}` (SLF4J style) or `%d`, `%f` (printf style)

4. **Exception Parameter Position**
   - Exception parameters MUST always come first in logging method calls
   - Example: `LOGGER.error(exception, "Error message with %s", parameter)`

5. **Parameter-Placeholder Validation**
   - Count `%s` placeholders in the message string
   - Count actual parameters passed (excluding the exception if present)
   - Emit WARNING if counts don't match

6. **System.out/System.err Detection**
   - Detect all usages of `System.out.print()`, `System.out.println()`, `System.out.printf()`, and `System.out.format()`
   - Detect all usages of `System.err.print()`, `System.err.println()`, `System.err.printf()`, and `System.err.format()`
   - Log a WARNING: `Inappropriate use of System.out/System.err detected. Use proper logging framework instead.`
   - Include file path, line number, and the specific System stream being used in the warning

7. **Suppression Mechanism**
   - Use the existing `cui-rewrite:disable` comment-based suppression (already implemented in RecipeSuppressionUtil)
   - Supports: `// cui-rewrite:disable` - suppresses all recipes for the next element
   - Supports: `// cui-rewrite:disable RecipeName` - suppresses specific recipe for the next element
   - Suppression applies to all logging recipe validations including System.out/err detection

## Benefits

1. **Clean Integration**: Single dependency in parent POM
2. **Testability**: Full unit test coverage for recipes
3. **Maintainability**: Centralized custom rules
4. **Extensibility**: Easy to add new CUI-specific recipes
5. **Performance**: Recipes run as part of build pipeline
6. **Consistency**: Same formatting across all CUI projects

## Best Practices

1. **Recipe Design**
   - Keep recipes focused on single responsibility
   - Provide clear descriptions and examples
   - Use tags for categorization
   - Ensure idempotency

2. **Testing**
   - Test both positive and negative cases
   - Include edge cases
   - Verify idempotency (running twice produces same result)
   - Test with real CUI project code

3. **Documentation**
   - Document each recipe's purpose
   - Provide before/after examples
   - Track known issues in GitHub
   - Include troubleshooting guide

## Troubleshooting

### Recipe Not Found
```
Error: Recipe 'de.cuioss.rewrite.format.AnnotationNewlineFormat' not found
```
**Solution**: Ensure cui-open-rewrite JAR is in plugin dependencies

### Formatting Not Applied
**Check**:
1. Recipe is listed in activeRecipes
2. Recipe JAR is available
3. Run with `-X` for debug output
4. Verify recipe visitor logic

### Test Failures
**Debug Steps**:
1. Run individual tests with `-Dtest=TestName`
2. Check parser configuration
3. Verify classpath dependencies
4. Review actual vs expected output

## References

- [OpenRewrite Documentation](https://docs.openrewrite.org/)
- [Writing Custom Recipes](https://docs.openrewrite.org/authoring-recipes/writing-a-java-refactoring-recipe)
- [Recipe Testing](https://docs.openrewrite.org/authoring-recipes/recipe-testing)
- [Recipe Distribution](https://docs.openrewrite.org/authoring-recipes/recipe-distribution)
- [CUI Standards](https://github.com/cuioss/cui-llm-rules)