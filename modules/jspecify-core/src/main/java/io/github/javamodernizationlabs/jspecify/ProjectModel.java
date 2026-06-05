package io.github.javamodernizationlabs.jspecify;

import io.github.javamodernizationlabs.jspecify.config.JspecifyConfig;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * An immutable description of a project to be scanned or rewritten.
 *
 * <p>Captures the project root, the Java source roots to traverse, path-exclusion
 * globs, package-marking policy, public API selectors, and traversal options. The
 * {@code of(...)} factories discover source roots and apply configuration defaults.
 *
 * @param name a display name for the project, derived from the root directory name
 * @param rootDirectory the absolute, normalized project root directory
 * @param sourceRoots the Java source roots to traverse; defensively copied
 * @param excludedPathPatterns glob patterns for paths to skip; defensively copied
 * @param markPackages glob patterns for packages eligible for {@code @NullMarked};
 *        defensively copied
 * @param leaveUnmarkedPackages glob patterns for packages to leave unmarked;
 *        defensively copied
 * @param publicApiIncludes glob patterns selecting public API packages;
 *        defensively copied
 * @param publicApiExcludes glob patterns excluding packages from the public API;
 *        defensively copied
 * @param publicApiJpmsExportsOnly whether the public API is limited to JPMS-exported
 *        packages
 * @param followSymlinks whether symbolic links are followed during traversal
 */
public record ProjectModel(
        String name,
        Path rootDirectory,
        List<Path> sourceRoots,
        List<String> excludedPathPatterns,
        List<String> markPackages,
        List<String> leaveUnmarkedPackages,
        List<String> publicApiIncludes,
        List<String> publicApiExcludes,
        boolean publicApiJpmsExportsOnly,
        boolean followSymlinks
) {
    /**
     * Canonical constructor that normalizes the root directory and defensively
     * copies the list components, substituting empty lists for {@code null}.
     */
    public ProjectModel {
        rootDirectory = rootDirectory.toAbsolutePath().normalize();
        sourceRoots = sourceRoots == null ? List.of() : List.copyOf(sourceRoots);
        excludedPathPatterns = excludedPathPatterns == null
                ? List.of()
                : List.copyOf(excludedPathPatterns);
        markPackages = markPackages == null ? List.of() : List.copyOf(markPackages);
        leaveUnmarkedPackages = leaveUnmarkedPackages == null
                ? List.of()
                : List.copyOf(leaveUnmarkedPackages);
        publicApiIncludes = publicApiIncludes == null ? List.of() : List.copyOf(publicApiIncludes);
        publicApiExcludes = publicApiExcludes == null ? List.of() : List.copyOf(publicApiExcludes);
    }

    /**
     * Creates a project model rooted at the given directory using default configuration.
     *
     * @param rootDirectory the project root directory
     * @return a project model with discovered source roots and default settings
     */
    public static ProjectModel of(Path rootDirectory) {
        return of(rootDirectory, JspecifyConfig.defaults());
    }

    /**
     * Creates a project model rooted at the given directory using the supplied configuration.
     *
     * @param rootDirectory the project root directory
     * @param config the configuration supplying source roots, excludes, and policy
     * @return a project model populated from the configuration
     */
    public static ProjectModel of(Path rootDirectory, JspecifyConfig config) {
        Path root = rootDirectory.toAbsolutePath().normalize();
        return new ProjectModel(rootDirectory.getFileName() == null
                ? rootDirectory.toString()
                : rootDirectory.getFileName().toString(),
                root,
                discoverSourceRoots(root, config.sourceRoots()),
                config.generatedCodeExcludes(),
                config.markPackages(),
                config.leaveUnmarkedPackages(),
                config.publicApiIncludes(),
                config.publicApiExcludes(),
                config.publicApiJpmsExportsOnly(),
                config.followSymlinks());
    }

    /**
     * Creates a project model with explicit source roots and path excludes.
     *
     * @param rootDirectory the project root directory
     * @param sourceRoots the source roots to traverse, or {@code null} to discover them
     * @param excludedPathPatterns glob patterns for paths to skip
     * @param followSymlinks whether symbolic links are followed during traversal
     * @return a project model with empty package and public API selectors
     */
    public static ProjectModel of(Path rootDirectory,
                                  List<Path> sourceRoots,
                                  List<String> excludedPathPatterns,
                                  boolean followSymlinks) {
        return of(rootDirectory, sourceRoots, excludedPathPatterns, List.of(), List.of(),
                List.of(), List.of(), false, followSymlinks);
    }

    /**
     * Creates a project model with explicit source roots, excludes, and public API selectors.
     *
     * @param rootDirectory the project root directory
     * @param sourceRoots the source roots to traverse, or {@code null} to discover them
     * @param excludedPathPatterns glob patterns for paths to skip
     * @param publicApiIncludes glob patterns selecting public API packages
     * @param publicApiExcludes glob patterns excluding packages from the public API
     * @param publicApiJpmsExportsOnly whether the public API is limited to JPMS-exported packages
     * @param followSymlinks whether symbolic links are followed during traversal
     * @return a project model with empty package-marking selectors
     */
    public static ProjectModel of(Path rootDirectory,
                                  List<Path> sourceRoots,
                                  List<String> excludedPathPatterns,
                                  List<String> publicApiIncludes,
                                  List<String> publicApiExcludes,
                                  boolean publicApiJpmsExportsOnly,
                                  boolean followSymlinks) {
        return of(rootDirectory, sourceRoots, excludedPathPatterns, List.of(), List.of(),
                publicApiIncludes, publicApiExcludes, publicApiJpmsExportsOnly,
                followSymlinks);
    }

    /**
     * Creates a fully specified project model.
     *
     * @param rootDirectory the project root directory
     * @param sourceRoots the source roots to traverse, or {@code null} to discover them
     * @param excludedPathPatterns glob patterns for paths to skip
     * @param markPackages glob patterns for packages eligible for {@code @NullMarked}
     * @param leaveUnmarkedPackages glob patterns for packages to leave unmarked
     * @param publicApiIncludes glob patterns selecting public API packages
     * @param publicApiExcludes glob patterns excluding packages from the public API
     * @param publicApiJpmsExportsOnly whether the public API is limited to JPMS-exported packages
     * @param followSymlinks whether symbolic links are followed during traversal
     * @return a project model populated from the given selectors
     */
    public static ProjectModel of(Path rootDirectory,
                                  List<Path> sourceRoots,
                                  List<String> excludedPathPatterns,
                                  List<String> markPackages,
                                  List<String> leaveUnmarkedPackages,
                                  List<String> publicApiIncludes,
                                  List<String> publicApiExcludes,
                                  boolean publicApiJpmsExportsOnly,
                                  boolean followSymlinks) {
        Path root = rootDirectory.toAbsolutePath().normalize();
        List<Path> normalizedRoots = sourceRoots == null
                ? discoverSourceRoots(root, List.of())
                : sourceRoots.stream()
                .map(p -> p.isAbsolute() ? p : root.resolve(p))
                .map(Path::normalize)
                .toList();
        return new ProjectModel(root.getFileName() == null
                ? root.toString()
                : root.getFileName().toString(),
                root,
                normalizedRoots,
                excludedPathPatterns,
                markPackages,
                leaveUnmarkedPackages,
                publicApiIncludes,
                publicApiExcludes,
                publicApiJpmsExportsOnly,
                followSymlinks);
    }

    private static List<Path> discoverSourceRoots(Path root, List<Path> configuredRoots) {
        if (configuredRoots != null && !configuredRoots.isEmpty()) {
            return configuredRoots.stream()
                    .map(p -> p.isAbsolute() ? p : root.resolve(p))
                    .map(Path::normalize)
                    .toList();
        }

        Set<Path> roots = new LinkedHashSet<>();
        addIfDirectory(roots, root.resolve("src/main/java"));
        addIfDirectory(roots, root.resolve("src/test/java"));

        if (Files.isDirectory(root)) {
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(Files::isDirectory)
                        .filter(ProjectModel::isConventionalJavaSourceRoot)
                        .filter(p -> !isBuildOutput(root, p))
                        .map(Path::normalize)
                        .forEach(roots::add);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to discover Java source roots under " + root, e);
            }
        }

        return new ArrayList<>(roots);
    }

    private static void addIfDirectory(Set<Path> roots, Path candidate) {
        if (Files.isDirectory(candidate)) {
            roots.add(candidate.normalize());
        }
    }

    /**
     * Determines whether a file should be scanned, honoring symlink policy, the
     * project root boundary, and the configured path-exclusion patterns.
     *
     * @param file the candidate file
     * @return {@code true} if the file should be scanned, {@code false} otherwise
     */
    public boolean shouldScan(Path file) {
        if (Files.isSymbolicLink(file) && !followSymlinks) {
            return false;
        }
        Path normalized = file.toAbsolutePath().normalize();
        if (!followSymlinks && !normalized.startsWith(rootDirectory)) {
            return false;
        }
        Path relative = normalized.startsWith(rootDirectory)
                ? rootDirectory.relativize(normalized)
                : normalized;
        String normalizedRelative = relative.toString().replace('\\', '/');
        for (String pattern : excludedPathPatterns) {
            if (pathGlobMatch(pattern, normalizedRelative)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Walks a source root using this model's symbolic-link policy.
     *
     * @param root the root to walk
     * @return a stream of paths under {@code root}
     * @throws IOException if the root cannot be walked
     */
    public Stream<Path> walk(Path root) throws IOException {
        return followSymlinks
                ? Files.walk(root, FileVisitOption.FOLLOW_LINKS)
                : Files.walk(root);
    }

    /**
     * Tests whether a package name matches a glob pattern.
     *
     * <p>The pattern supports a trailing {@code .**} to match a package and all of
     * its sub-packages, {@code **} to match any characters including dots, and
     * {@code *} to match any characters except a dot.
     *
     * @param pattern the package glob pattern
     * @param packageName the fully qualified package name to test
     * @return {@code true} if {@code packageName} matches {@code pattern}
     */
    public boolean packageMatches(String pattern, String packageName) {
        if (pattern.endsWith(".**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return packageName.equals(prefix) || packageName.startsWith(prefix + ".");
        }
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '*' && i + 1 < pattern.length() && pattern.charAt(i + 1) == '*') {
                regex.append(".*");
                i++;
            } else if (c == '*') {
                regex.append("[^.]*");
            } else {
                if ("\\.[]{}()+-^$?|".indexOf(c) >= 0) {
                    regex.append('\\');
                }
                regex.append(c);
            }
        }
        return packageName.matches(regex.toString());
    }

    private boolean pathGlobMatch(String pattern, String relativePath) {
        String normalizedPattern = pattern.replace('\\', '/');
        if (normalizedPattern.startsWith("**/")
                && pathGlobMatch(normalizedPattern.substring(3), relativePath)) {
            return true;
        }
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < normalizedPattern.length(); i++) {
            char c = normalizedPattern.charAt(i);
            if (c == '*' && i + 1 < normalizedPattern.length()
                    && normalizedPattern.charAt(i + 1) == '*') {
                regex.append(".*");
                i++;
            } else if (c == '*') {
                regex.append("[^/]*");
            } else {
                if ("\\.[]{}()+-^$?|".indexOf(c) >= 0) {
                    regex.append('\\');
                }
                regex.append(c);
            }
        }
        return relativePath.matches(regex.toString());
    }

    private static boolean isConventionalJavaSourceRoot(Path path) {
        int count = path.getNameCount();
        if (count < 3) {
            return false;
        }
        return path.getName(count - 3).toString().equals("src")
                && (path.getName(count - 2).toString().equals("main")
                || path.getName(count - 2).toString().equals("test"))
                && path.getName(count - 1).toString().equals("java");
    }

    private static boolean isBuildOutput(Path root, Path path) {
        Path rel = root.relativize(path.toAbsolutePath().normalize());
        for (Path part : rel) {
            String name = part.toString();
            if (name.equals("build") || name.equals("target") || name.equals(".gradle")
                    || name.equals(".git")) {
                return true;
            }
        }
        return false;
    }
}
