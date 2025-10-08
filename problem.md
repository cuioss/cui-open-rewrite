# Problem: AnnotationNewlineFormat Cannot Handle Classes Without Modifiers

## Verification Process

**CRITICAL**: This module tests itself using its own snapshot artifact. Changes MUST be tested using this two-step process:

### Step 1: Install Module Locally
```bash
./mvnw clean install
```

This builds the module and installs version `1.1-SNAPSHOT` into the local Maven repository (`~/.m2/repository/`).

### Step 2: Run Pre-Commit Profile
```bash
./mvnw -Ppre-commit clean verify
```

This runs the OpenRewrite recipes (including `AnnotationNewlineFormat`) from the locally installed artifact against the project's own source code.

### Why This Two-Step Process?

The project has a **self-referential dependency**:

1. **Build artifact**: `pom.xml` defines version `1.1-SNAPSHOT` (line 36)
2. **Pre-commit profile**: References `de.cuioss.rewrite:cui-open-rewrite:1.1-SNAPSHOT` in its recipe configuration
3. **Testing**: The pre-commit profile applies the recipes to the project's own code

**Interdependencies**:
- Step 1 (`install`) must complete before Step 2 (`pre-commit`) or Maven will use an outdated version
- Any code changes to `AnnotationNewlineFormat` require reinstalling before testing
- Unit tests (`./mvnw test`) verify recipe behavior in isolation
- Pre-commit profile verifies recipe behavior on real code (this project)

### Verification Workflow

```bash
# 1. Make code changes to AnnotationNewlineFormat.java
# 2. Run unit tests
./mvnw test

# 3. Install updated module locally
./mvnw clean install

# 4. Run pre-commit to see how changes affect the codebase
./mvnw -Ppre-commit clean verify

# 5. Review changes with git diff
git diff
```

---

## Current Issue

The `AnnotationNewlineFormat` recipe fails to add newlines between annotations and class declarations when the class has **no modifiers** (e.g., package-private classes).

### Failing Test Case

```java
// Input
@Deprecated class TestClass {
    void method() {
    }
}

// Expected Output
@Deprecated
class TestClass {
    void method() {
    }
}

// Actual Output (NO CHANGE)
@Deprecated class TestClass {
    void method() {
    }
}
```

**Test Location**: `AnnotationNewlineFormatNormalizeConflictTest.shouldSplitClassAnnotationAndSurviveNormalization()`

**Current Status**: Test is disabled with `@Disabled("Classes without modifiers not supported - see comment")`

## Root Cause Analysis

### Why It Fails

The `AnnotationNewlineFormat` recipe works by manipulating the `prefix` (whitespace) of AST nodes that follow annotations. The current implementation:

1. **For classes WITH modifiers** (e.g., `public class`, `final class`):
   - Finds the first modifier AST node
   - Adds newline to the modifier's prefix
   - ✅ **This works**

2. **For classes WITHOUT modifiers** (e.g., `class TestClass`):
   - No modifier AST nodes exist
   - The "class" keyword is NOT a separate AST node in OpenRewrite
   - Current code returns early without making changes (lines 170-172, 291-292)
   - ❌ **This fails**

### Current Implementation (Simplified)

```java
private boolean needsFormatting(J.ClassDeclaration cd) {
    // ... annotation spacing checks ...

    // Check if there needs to be a newline after annotations
    if (!cd.getModifiers().isEmpty()) {
        return !cd.getModifiers().getFirst().getPrefix().getWhitespace().contains("\n");
    }

    // NOTE: Classes without modifiers are not supported due to OpenRewrite AST limitations
    // The "class" keyword is not a separate AST node we can manipulate
    return false; // <-- GIVES UP HERE
}

private J.ClassDeclaration ensureProperSpacingAfterAnnotations(J.ClassDeclaration cd) {
    if (!cd.getModifiers().isEmpty()) {
        // ... add newline to first modifier's prefix ...
    }
    // NOTE: Classes without modifiers cannot be formatted due to OpenRewrite AST limitations
    return cd; // <-- NO CHANGES MADE
}
```

## OpenRewrite AST Structure

For a class without modifiers:

```
J.ClassDeclaration
├── leadingAnnotations: [@Deprecated]
├── modifiers: [] (EMPTY!)
├── name: J.Identifier("TestClass") <-- Has its own prefix Space
└── body: J.Block { ... }
```

The "class" keyword is **not** represented as a separate AST node.

## Potential Solutions to Investigate

### Option 1: Manipulate Class Name Prefix (UNTESTED)

The class name is a `J.Identifier` which has its own `prefix` Space. Could we add the newline there?

```java
private J.ClassDeclaration ensureProperSpacingAfterAnnotations(J.ClassDeclaration cd) {
    if (!cd.getModifiers().isEmpty()) {
        // Existing code for classes with modifiers
        // ...
    } else {
        // NEW: Try manipulating the class name's prefix
        J.Identifier name = cd.getName();
        String currentWhitespace = name.getPrefix().getWhitespace();

        if (!currentWhitespace.contains("\n")) {
            String indent = getProperIndentation();
            cd = cd.withName(name.withPrefix(
                Space.build("\n" + indent, name.getPrefix().getComments())
            ));
        }
    }
    return cd;
}
```

**Question**: Would this add whitespace before the class name or before the "class" keyword?

### Option 2: Accept the Limitation

Document that classes without modifiers are not supported, as:
- Package-private classes are relatively rare
- Most classes have at least one modifier (`public`, `final`, `abstract`, etc.)
- The test can remain disabled with proper documentation

### Option 3: Use NormalizeFormat Differently

Investigate if there's a way to prevent `NormalizeFormat` from removing the newlines, or if the recipes need to run in a different order.

## Questions to Answer

1. **Does `J.Identifier.withPrefix()` affect the space before "class" or before "TestClass"?**
   - Need to test or examine OpenRewrite source code
   - Could use `TreeVisitingPrinter` to inspect the AST structure

2. **Has anyone solved this problem in other OpenRewrite recipes?**
   - Search for similar formatting recipes
   - Check OpenRewrite issue tracker

3. **Is there another AST node we can manipulate?**
   - Perhaps `J.ClassDeclaration.padding` or other internal structures?

## Impact

**Scope**: This affects:
- Package-private classes with annotations (no `public`, `private`, `protected`)
- Package-private classes with no other modifiers (no `final`, `abstract`, etc.)

**Severity**: Low - most Java classes have explicit modifiers, especially in production code

## Next Steps

1. ✅ Document the problem (this file)
2. ⏳ Test Option 1: Try manipulating class name prefix
3. ⏳ If Option 1 fails, decide between accepting limitation (Option 2) or further investigation
4. ⏳ Update implementation based on findings
5. ⏳ Update or remove test accordingly
