# Command Configuration

## ./mvnw -Ppre-commit clean install

### Last Execution Duration
- **Duration**: 40000ms (40 seconds)
- **Last Updated**: 2025-10-14

### Acceptable Warnings
- None configured yet

### OpenRewrite TODO Markers (Intentional Test Cases)

The following OpenRewrite TODO markers are **intentional test fixtures** and should NOT be fixed:

#### Test Files with Intentional Markers
All markers in the following test files are part of the test suite to verify that recipes correctly identify issues and suggest appropriate suppressions:

1. **InvalidExceptionUsageRecipeTest.java** - Tests exception handling recipe behavior
   - Tests for catching generic Exception, RuntimeException, Throwable
   - Tests for throwing generic exceptions
   - Tests for exception instantiation patterns
   - Located at: `src/test/java/de/cuioss/rewrite/logging/`

2. **InvalidExceptionUsageRecipeSuppressionTest.java** - Tests suppression mechanism
   - Located at: `src/test/java/de/cuioss/rewrite/logging/`

3. **InvalidExceptionUsageRecipeIssue5Test.java** - Regression test for issue #5
   - Located at: `src/test/java/de/cuioss/rewrite/logging/`

4. **CuiLoggerStandardsRecipeTest.java** - Tests logger standards enforcement
   - Located at: `src/test/java/de/cuioss/rewrite/logging/`

5. **CuiLogRecordPatternRecipeTest.java** - Tests LogRecord pattern detection
   - Located at: `src/test/java/de/cuioss/rewrite/logging/`

6. **CuiLogRecordPatternRecipeReproduceTest.java** - Reproduces specific scenarios
   - Located at: `src/test/java/de/cuioss/rewrite/logging/`

7. **RecipeMarkerUtilTest.java** - Tests marker utility functions
   - Located at: `src/test/java/de/cuioss/rewrite/util/`

#### Marker Patterns in Test Files
- `/*~~(TODO: Catch specific not Exception. Suppress: // cui-rewrite:disable InvalidExceptionUsageRecipe)~~>*/`
- `/*~~(TODO: Throw specific not Exception. Suppress: // cui-rewrite:disable InvalidExceptionUsageRecipe)~~>*/`
- `/*~~(TODO: INFO needs LogRecord. Suppress: // cui-rewrite:disable CuiLogRecordPatternRecipe)~~>*/`
- And similar patterns for RuntimeException, Throwable, etc.

These markers demonstrate the expected output of the OpenRewrite recipes and are essential for validating recipe functionality.