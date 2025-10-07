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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvSource(delimiterString = "|", textBlock = """
        target/generated-sources/annotations/GeneratedCode.java                                                   | false | Target directory exclusion
        target/generated-test-sources/test-annotations/TestBenchmark_jmhTest.java                                | false | Maven target test sources exclusion
        build/generated/sources/annotationProcessor/java/main/BuildGenerated.java                                | false | Gradle build directory exclusion
        src/main/java/SourceCode.java                                                                            | true  | Regular source files inclusion
        src/test/java/SourceTest.java                                                                            | true  | Test source files inclusion
        module-a/target/classes/ModuleGenerated.java                                                             | false | Nested target directories exclusion
        target/generated-sources/annotations/WindowsGenerated.java                                               | false | Windows-style paths exclusion
        benchmarking/cui-benchmarking-common/target/generated-test-sources/test-annotations/BenchmarkGenerated.java | false | Deep nested target paths exclusion
        """)
    void shouldHandlePathExclusions(String pathString, boolean shouldBeMarked, String description) {
        String source = "class TestClass {}";
        Path sourcePath = Path.of(pathString);
        SourceFile sourceFile = parseWithPath(source, sourcePath);

        SourceFile result = (SourceFile) visitor.visit(sourceFile, ctx);
        boolean isMarked = result.getMarkers().findFirst(SearchResult.class).isPresent();

        if (shouldBeMarked) {
            assertTrue(isMarked, description + ": should be marked for processing");
        } else {
            assertFalse(isMarked, description + ": should NOT be marked (excluded)");
        }
    }

    private SourceFile parseWithPath(String source, Path sourcePath) {
        List<SourceFile> sources = parser.parse(source).toList();
        J.CompilationUnit cu = (J.CompilationUnit) sources.getFirst();
        return cu.withSourcePath(sourcePath);
    }
}
