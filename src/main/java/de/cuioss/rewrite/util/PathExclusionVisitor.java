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

import de.cuioss.tools.logging.CuiLogger;
import org.openrewrite.ExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.marker.SearchResult;

/**
 * TreeVisitor that acts as a precondition to exclude source files matching specific path patterns.
 * This visitor marks files that should NOT be processed by returning them without any marker.
 * Only files that don't match exclusion patterns are marked for processing.
 *
 * <p>Default exclusions include target and build directories.</p>
 *
 * <p>Usage: Use with Preconditions.check() to filter files before applying recipes.</p>
 */
public class PathExclusionVisitor extends TreeVisitor<Tree, ExecutionContext> {

    private static final CuiLogger LOGGER = new CuiLogger(PathExclusionVisitor.class);

    /**
     * Default exclusion patterns for common build output directories.
     */
    private static final String[] DEFAULT_EXCLUSIONS = {
        "**/target/**",
        "**/build/**"
    };

    private final String[] exclusionPatterns;

    /**
     * Creates a PathExclusionVisitor with default exclusions (target and build directories).
     */
    public PathExclusionVisitor() {
        this(DEFAULT_EXCLUSIONS);
    }

    /**
     * Creates a PathExclusionVisitor with custom exclusion patterns.
     *
     * @param exclusionPatterns glob patterns for paths to exclude
     */
    public PathExclusionVisitor(String... exclusionPatterns) {
        this.exclusionPatterns = exclusionPatterns;
    }

    @Override
    public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
        if (tree instanceof SourceFile sourceFile) {
            String sourcePath = sourceFile.getSourcePath().toString();

            // Normalize path separators to forward slashes for consistent pattern matching
            sourcePath = sourcePath.replace('\\', '/');

            LOGGER.trace("Checking path: %s", sourcePath);

            for (String pattern : exclusionPatterns) {
                LOGGER.trace("Against pattern: %s", pattern);
                boolean matches = matchesGlobPattern(sourcePath, pattern);
                LOGGER.trace("Matches: %s", matches);
                if (matches) {
                    // File matches exclusion pattern - return it unmarked so precondition fails
                    LOGGER.debug("Excluding file from processing: %s", sourcePath);
                    return sourceFile;
                }
            }

            // File doesn't match any exclusion pattern - mark it so precondition passes
            LOGGER.trace("Including file for processing: %s", sourcePath);
            return SearchResult.found(sourceFile);
        }
        return super.visit(tree, ctx);
    }

    /**
     * Simple glob pattern matching for paths.
     * Supports ** for any number of directories and * for any characters within a segment.
     *
     * @param path the path to check
     * @param pattern the glob pattern
     * @return true if the path matches the pattern
     */
    private boolean matchesGlobPattern(String path, String pattern) {
        // Simple implementation: For patterns like "**/target/**" or "**/build/**"
        // check if the directory name appears as a path component
        if (pattern.startsWith("**/") && pattern.endsWith("/**")) {
            String dirName = pattern.substring(3, pattern.length() - 3);

            // Check if path starts with "dirName/" or contains "/dirName/"
            boolean matches = path.startsWith(dirName + "/") || path.contains("/" + dirName + "/");

            LOGGER.trace("Path '%s' %s pattern '%s'", path, matches ? "matches" : "does not match", pattern);
            return matches;
        }

        LOGGER.trace("Pattern '%s' not in expected format, skipping path '%s'", pattern, path);
        return false;
    }
}
