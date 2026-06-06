package io.github.javamodernizationlabs.jspecify.scan;

import io.github.javamodernizationlabs.jspecify.AnnotationCatalog;
import io.github.javamodernizationlabs.jspecify.AnnotationInventory;
import io.github.javamodernizationlabs.jspecify.Location;
import io.github.javamodernizationlabs.jspecify.ProjectModel;
import io.github.javamodernizationlabs.jspecify.config.JspecifyConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Walks a project's Java sources and counts known nullness annotation usages.
 *
 * <p>The scanner is intentionally text-based (regex over imports and annotation
 * markers): it avoids a Java parsing dependency for the MVP and is forgiving
 * for files that don't compile yet. It under-reports compared to a full parser
 * but is consistent and fast enough for {@code jml jspecify plan}.
 */
public final class AnnotationScanner {

    private static final Pattern IMPORT_PATTERN =
            Pattern.compile("^\\s*import\\s+(static\\s+)?([\\w.]+|[\\w.]+\\.\\*)\\s*;");

    private final AnnotationCatalog catalog;

    /**
     * Creates a scanner backed by the default annotation catalog.
     *
     * <p>Custom {@code annotationMappings} from configuration are ignored; prefer
     * {@link #forConfig(JspecifyConfig)} when configuration is available.
     */
    public AnnotationScanner() {
        this(AnnotationCatalog.defaults());
    }

    /**
     * Creates a scanner backed by the given annotation catalog.
     *
     * @param catalog the catalog of known nullness annotations to recognize
     */
    public AnnotationScanner(AnnotationCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * Creates a scanner whose catalog reflects the supplied configuration's
     * annotation mappings.
     *
     * <p>Use this factory at every entry point (CLI, Gradle, Maven) so that
     * custom {@code annotationMappings} declared in {@code jspecify.yml} are
     * honored consistently; constructing a bare {@link #AnnotationScanner()}
     * silently falls back to {@link AnnotationCatalog#defaults()} and ignores
     * user configuration.
     *
     * @param config the loaded JSpecify configuration; never {@code null}
     * @return a scanner backed by the configuration's annotation catalog
     */
    public static AnnotationScanner forConfig(JspecifyConfig config) {
        return new AnnotationScanner(new AnnotationCatalog(config.annotationMappings()));
    }

    /**
     * Scans every Java source file under the project's source roots and tallies the
     * known nullness annotations it finds.
     *
     * @param project the project describing source roots, excludes, and scan policy
     * @return an inventory of annotation usages and the number of files scanned
     * @throws IOException if a source root or file cannot be read
     */
    public AnnotationInventory scan(ProjectModel project) throws IOException {
        Map<String, Integer> totals = new LinkedHashMap<>();
        Map<String, List<Location>> locations = new LinkedHashMap<>();
        int files = 0;
        for (Path root : project.sourceRoots()) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> stream = project.walk(root)) {
                Iterable<Path> javaFiles = stream
                        .filter(Files::isRegularFile)
                        .filter(project::shouldScan)
                        .filter(p -> p.toString().endsWith(".java"))
                        ::iterator;
                for (Path file : javaFiles) {
                    files++;
                    scanFile(project.rootDirectory(), file, totals, locations);
                }
            }
        }
        return new AnnotationInventory(totals, locations, files);
    }

    void scanFile(Path projectRoot,
                  Path file,
                  Map<String, Integer> totals,
                  Map<String, List<Location>> locations) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        Map<String, String> shortToFqn = new HashMap<>();
        Set<String> ambiguousShortNames = new LinkedHashSet<>();
        Set<String> explicitShortNames = new LinkedHashSet<>();
        Set<String> wildcardPackages = new LinkedHashSet<>();
        Set<String> knownAnnotations = knownAnnotations();
        // First pass: resolve imports of known annotations and their short names.
        boolean inBlockComment = false;
        for (String line : lines) {
            CommentStripResult stripped = stripComments(line, inBlockComment);
            inBlockComment = stripped.inBlockComment();
            Matcher m = IMPORT_PATTERN.matcher(stripped.line());
            if (m.find()) {
                String fqn = m.group(2);
                if (fqn.endsWith(".*")) {
                    wildcardPackages.add(fqn.substring(0, fqn.length() - 2));
                } else if (knownAnnotations.contains(fqn)) {
                    String simple = fqn.substring(fqn.lastIndexOf('.') + 1);
                    explicitShortNames.add(simple);
                    addShortNameMapping(shortToFqn, ambiguousShortNames, simple, fqn);
                }
            }
        }
        // Explicit imports win over wildcard-resolved names: only add a wildcard
        // simple name when no explicit import already claimed it, and never let a
        // wildcard mark an explicit mapping ambiguous.
        for (String fqn : knownAnnotations) {
            String packageName = fqn.substring(0, fqn.lastIndexOf('.'));
            if (wildcardPackages.contains(packageName)) {
                String simple = fqn.substring(fqn.lastIndexOf('.') + 1);
                if (explicitShortNames.contains(simple)) {
                    continue;
                }
                addShortNameMapping(shortToFqn, ambiguousShortNames, simple, fqn);
            }
        }
        ambiguousShortNames.forEach(shortToFqn::remove);
        // Second pass: count annotation references by short name on this file's lines.
        inBlockComment = false;
        for (int i = 0; i < lines.size(); i++) {
            CommentStripResult stripped = stripComments(lines.get(i), inBlockComment);
            inBlockComment = stripped.inBlockComment();
            String line = stripped.line();
            for (var entry : shortToFqn.entrySet()) {
                String simple = entry.getKey();
                String fqn = entry.getValue();
                // Match @Simple as a token boundary; tolerate spaces and qualified usages.
                Pattern p = Pattern.compile("@" + Pattern.quote(simple) + "\\b");
                Matcher m = p.matcher(line);
                while (m.find()) {
                    int col = m.start() + 1;
                    addHit(projectRoot, file, totals, locations, fqn, i + 1, col,
                            col + simple.length() + 1);
                }
            }
            for (String fqn : knownAnnotations) {
                Pattern p = Pattern.compile("@" + Pattern.quote(fqn) + "\\b");
                Matcher m = p.matcher(line);
                while (m.find()) {
                    int col = m.start() + 1;
                    addHit(projectRoot, file, totals, locations, fqn, i + 1, col,
                            col + fqn.length() + 1);
                }
            }
        }
    }

    private Set<String> knownAnnotations() {
        Set<String> known = new LinkedHashSet<>(catalog.knownLegacyAnnotations());
        known.add(AnnotationCatalog.JSPECIFY_NULLABLE);
        known.add(AnnotationCatalog.JSPECIFY_NON_NULL);
        known.add(AnnotationCatalog.JSPECIFY_NULL_MARKED);
        known.add(AnnotationCatalog.JSPECIFY_NULL_UNMARKED);
        return known;
    }

    private void addShortNameMapping(Map<String, String> shortToFqn,
                                     Set<String> ambiguousShortNames,
                                     String simple,
                                     String fqn) {
        String existing = shortToFqn.putIfAbsent(simple, fqn);
        if (existing != null && !existing.equals(fqn)) {
            ambiguousShortNames.add(simple);
        }
    }

    private void addHit(Path projectRoot,
                        Path file,
                        Map<String, Integer> totals,
                        Map<String, List<Location>> locations,
                        String fqn,
                        int line,
                        int startColumn,
                        int endColumn) {
        Path normalizedFile = file.toAbsolutePath().normalize();
        Path reportPath = normalizedFile.startsWith(projectRoot)
                ? projectRoot.relativize(normalizedFile)
                : normalizedFile;
        totals.merge(fqn, 1, Integer::sum);
        locations.computeIfAbsent(fqn, k -> new ArrayList<>())
                .add(new Location(reportPath, line, startColumn, line, endColumn));
    }

    private CommentStripResult stripComments(String line, boolean inBlockComment) {
        StringBuilder out = new StringBuilder(line.length());
        int i = 0;
        boolean inString = false;
        boolean inChar = false;
        while (i < line.length()) {
            if (inBlockComment) {
                if (i + 1 < line.length() && line.charAt(i) == '*' && line.charAt(i + 1) == '/') {
                    out.append("  ");
                    i += 2;
                    inBlockComment = false;
                } else {
                    out.append(' ');
                    i++;
                }
                continue;
            }

            char c = line.charAt(i);
            char next = i + 1 < line.length() ? line.charAt(i + 1) : '\0';
            if (inString) {
                out.append(' ');
                if (c == '\\' && i + 1 < line.length()) {
                    out.append(' ');
                    i += 2;
                    continue;
                }
                if (c == '"') {
                    inString = false;
                }
                i++;
                continue;
            }
            if (inChar) {
                out.append(' ');
                if (c == '\\' && i + 1 < line.length()) {
                    out.append(' ');
                    i += 2;
                    continue;
                }
                if (c == '\'') {
                    inChar = false;
                }
                i++;
                continue;
            }
            if (c == '/' && next == '/') {
                out.append(" ".repeat(line.length() - i));
                break;
            }
            if (c == '/' && next == '*') {
                inBlockComment = true;
                out.append("  ");
                i += 2;
                continue;
            }
            if (c == '"') {
                inString = true;
                out.append(' ');
                i++;
                continue;
            } else if (c == '\'') {
                inChar = true;
                out.append(' ');
                i++;
                continue;
            }
            out.append(c);
            i++;
        }
        return new CommentStripResult(out.toString(), inBlockComment);
    }

    private record CommentStripResult(String line, boolean inBlockComment) {}
}
