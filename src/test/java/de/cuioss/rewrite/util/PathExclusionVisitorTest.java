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
package de.cuioss.rewrite.util;

import org.junit.jupiter.api.Test;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link PathExclusionVisitor} to ensure build output directories
 * and generated code are properly excluded from recipe processing.
 */
class PathExclusionVisitorTest {

    private final JavaParser parser = JavaParser.fromJavaVersion().build();
    private final ExecutionContext ctx = new InMemoryExecutionContext();
    private final PathExclusionVisitor visitor = new PathExclusionVisitor();

    @Test
    void shouldExcludeTargetDirectory() {
        String source = "class GeneratedCode {}";
        Path sourcePath = Path.of("target/generated-sources/annotations/GeneratedCode.java");
        SourceFile sourceFile = parseWithPath(source, sourcePath);

        SourceFile result = (SourceFile) visitor.visit(sourceFile, ctx);

        assertFalse(result.getMarkers().findFirst(SearchResult.class).isPresent(),
            "Files in target directory should NOT be marked (excluded)");
    }

    @Test
    void shouldExcludeMavenTargetTestSources() {
        String source = "class TestBenchmark_jmhTest {}";
        Path sourcePath = Path.of("target/generated-test-sources/test-annotations/TestBenchmark_jmhTest.java");
        SourceFile sourceFile = parseWithPath(source, sourcePath);

        SourceFile result = (SourceFile) visitor.visit(sourceFile, ctx);

        assertFalse(result.getMarkers().findFirst(SearchResult.class).isPresent(),
            "JMH generated test sources should NOT be marked (excluded)");
    }

    @Test
    void shouldExcludeGradleBuildDirectory() {
        String source = "class BuildGenerated {}";
        Path sourcePath = Path.of("build/generated/sources/annotationProcessor/java/main/BuildGenerated.java");
        SourceFile sourceFile = parseWithPath(source, sourcePath);

        SourceFile result = (SourceFile) visitor.visit(sourceFile, ctx);

        assertFalse(result.getMarkers().findFirst(SearchResult.class).isPresent(),
            "Files in build directory should NOT be marked (excluded)");
    }

    @Test
    void shouldProcessRegularSourceFiles() {
        String source = "class SourceCode {}";
        Path sourcePath = Path.of("src/main/java/SourceCode.java");
        SourceFile sourceFile = parseWithPath(source, sourcePath);

        SourceFile result = (SourceFile) visitor.visit(sourceFile, ctx);

        assertTrue(result.getMarkers().findFirst(SearchResult.class).isPresent(),
            "Regular source files should be marked for processing");
    }

    @Test
    void shouldProcessTestSourceFiles() {
        String source = "class SourceTest {}";
        Path sourcePath = Path.of("src/test/java/SourceTest.java");
        SourceFile sourceFile = parseWithPath(source, sourcePath);

        SourceFile result = (SourceFile) visitor.visit(sourceFile, ctx);

        assertTrue(result.getMarkers().findFirst(SearchResult.class).isPresent(),
            "Test source files should be marked for processing");
    }

    @Test
    void shouldExcludeNestedTargetDirectories() {
        String source = "class ModuleGenerated {}";
        Path sourcePath = Path.of("module-a/target/classes/ModuleGenerated.java");
        SourceFile sourceFile = parseWithPath(source, sourcePath);

        SourceFile result = (SourceFile) visitor.visit(sourceFile, ctx);

        assertFalse(result.getMarkers().findFirst(SearchResult.class).isPresent(),
            "Nested target directories should NOT be marked (excluded)");
    }

    @Test
    void shouldHandleWindowsStylePaths() {
        String source = "class WindowsGenerated {}";
        // Windows-style path
        Path sourcePath = Path.of("target", "generated-sources", "annotations", "WindowsGenerated.java");
        SourceFile sourceFile = parseWithPath(source, sourcePath);

        SourceFile result = (SourceFile) visitor.visit(sourceFile, ctx);

        assertFalse(result.getMarkers().findFirst(SearchResult.class).isPresent(),
            "Windows-style paths in target should NOT be marked (excluded)");
    }

    @Test
    void shouldExcludeTargetInAnyPosition() {
        String source = "class BenchmarkGenerated {}";
        Path sourcePath = Path.of("benchmarking/cui-benchmarking-common/target/generated-test-sources/test-annotations/BenchmarkGenerated.java");
        SourceFile sourceFile = parseWithPath(source, sourcePath);

        SourceFile result = (SourceFile) visitor.visit(sourceFile, ctx);

        assertFalse(result.getMarkers().findFirst(SearchResult.class).isPresent(),
            "Deep nested target paths should NOT be marked (excluded)");
    }

    private SourceFile parseWithPath(String source, Path sourcePath) {
        List<SourceFile> sources = parser.parse(source).toList();
        J.CompilationUnit cu = (J.CompilationUnit) sources.getFirst();
        return cu.withSourcePath(sourcePath);
    }
}
