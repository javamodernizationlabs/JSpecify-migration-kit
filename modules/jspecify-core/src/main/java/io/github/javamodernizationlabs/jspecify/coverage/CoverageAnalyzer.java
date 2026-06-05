package io.github.javamodernizationlabs.jspecify.coverage;

import io.github.javamodernizationlabs.jspecify.AnnotationCatalog;
import io.github.javamodernizationlabs.jspecify.ProjectModel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Estimates JSpecify nullness coverage across a project's public API by
 * scanning Java source files.
 *
 * <p>The analyzer uses lightweight regular-expression heuristics rather than a
 * full parser. It counts public API elements, methods, parameters and generic
 * type uses, and how many of each carry an explicit nullness contract (directly
 * or via an enclosing {@code @NullMarked} scope), producing a
 * {@link CoverageSummary}.
 */
public final class CoverageAnalyzer {

    private static final Pattern PUBLIC_TYPE = Pattern.compile(
            "\\bpublic\\s+(class|interface|record|enum|@interface)\\s+\\w+");
    private static final Pattern PUBLIC_MEMBER = Pattern.compile(
            "\\b(public|protected)\\s+[^=;{}]+\\([^)]*\\)\\s*(throws\\s+[\\w.,\\s]+)?[;{]");
    private static final Pattern PUBLIC_FIELD = Pattern.compile(
            "\\b(public|protected)\\s+(static\\s+)?(final\\s+)?[^=;{}()]+\\s+\\w+\\s*(=|;)");
    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern AMBIGUOUS = Pattern.compile("@\\w*Nullable\\s+\\w+[<].*[>]");
    private static final Pattern EXPORTS = Pattern.compile("^\\s*exports\\s+([\\w.]+)\\s*;");

    /**
     * Creates a {@code CoverageAnalyzer}.
     */
    public CoverageAnalyzer() {
    }

    /**
     * Scans the project's source roots and computes its nullness-coverage
     * metrics.
     *
     * @param project the project whose source roots and public-API rules drive
     *     the scan
     * @return a summary of the public-API nullness coverage
     * @throws IOException if a source root cannot be walked or a file cannot be
     *     read
     */
    public CoverageSummary analyze(ProjectModel project) throws IOException {
        int publicApi = 0;
        int specified = 0;
        int ambiguous = 0;
        int publicMethods = 0;
        int returnSpecified = 0;
        int publicParameters = 0;
        int parameterSpecified = 0;
        int genericTypeUses = 0;
        int genericTypeUseSpecified = 0;
        Set<String> packagesSeen = new HashSet<>();
        Set<String> nullMarkedPackages = new HashSet<>();
        Set<String> exportedPackages = new HashSet<>();
        List<Path> javaFiles = new ArrayList<>();

        for (Path root : project.sourceRoots()) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> stream = project.walk(root)) {
                stream
                        .filter(Files::isRegularFile)
                        .filter(project::shouldScan)
                        .filter(p -> p.toString().endsWith(".java"))
                        .forEach(javaFiles::add);
            }
        }

        for (Path file : javaFiles) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (file.getFileName().toString().equals("module-info.java")) {
                exportedPackages.addAll(exportedPackages(lines));
            }
        }

        for (Path file : javaFiles) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            String packageName = packageName(lines);
            if (!packageName.isBlank() && isPublicApiPackage(project, exportedPackages, packageName)) {
                packagesSeen.add(packageName);
            }
            if (file.getFileName().toString().equals("package-info.java")
                    && hasNullMarked(lines) && !packageName.isBlank()
                    && isPublicApiPackage(project, exportedPackages, packageName)) {
                nullMarkedPackages.add(packageName);
            }
        }

        for (Path file : javaFiles) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            String packageName = packageName(lines);
            if (!isPublicApiPackage(project, exportedPackages, packageName)) {
                continue;
            }
            boolean fileNullMarked = hasNullMarked(lines) || nullMarkedPackages.contains(packageName);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (AMBIGUOUS.matcher(line).find()) {
                    ambiguous++;
                }
                boolean localNullness = hasLocalNullness(lines, i);
                boolean method = PUBLIC_MEMBER.matcher(line).find();
                boolean field = PUBLIC_FIELD.matcher(line).find();
                boolean type = PUBLIC_TYPE.matcher(line).find();
                boolean returnNullness = hasReturnNullness(lines, i);
                if (type || method || field) {
                    publicApi++;
                    if (fileNullMarked || localNullness) {
                        specified++;
                    }
                }
                if (method) {
                    publicMethods++;
                    if (fileNullMarked || returnNullness) {
                        returnSpecified++;
                    }
                    for (String parameter : parameters(line)) {
                        publicParameters++;
                        if (fileNullMarked || hasNullnessToken(parameter)) {
                            parameterSpecified++;
                        }
                    }
                }
                int generics = genericTypeUseCount(line);
                if ((type || method || field) && generics > 0) {
                    genericTypeUses += generics;
                    genericTypeUseSpecified += annotatedGenericTypeUseCount(line);
                }
            }
        }
        int kotlinWarnings = Math.max(0, publicMethods - returnSpecified);
        return new CoverageSummary(publicApi, specified, nullMarkedPackages.size(),
                packagesSeen.size(), ambiguous, publicMethods, returnSpecified,
                publicParameters, parameterSpecified, genericTypeUses,
                genericTypeUseSpecified, kotlinWarnings);
    }

    private String packageName(List<String> lines) {
        for (String line : lines) {
            var matcher = PACKAGE.matcher(line);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "";
    }

    private boolean hasNullMarked(List<String> lines) {
        return lines.stream().anyMatch(line -> line.contains("@NullMarked")
                || line.contains("@" + AnnotationCatalog.JSPECIFY_NULL_MARKED));
    }

    private boolean hasLocalNullness(List<String> lines, int index) {
        int start = Math.max(0, index - 2);
        for (int i = start; i <= index; i++) {
            String line = lines.get(i);
            if (hasNullnessToken(line)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasNullnessToken(String text) {
        return text.contains("@Nullable") || text.contains("@NonNull")
                || text.contains("@" + AnnotationCatalog.JSPECIFY_NULLABLE)
                || text.contains("@" + AnnotationCatalog.JSPECIFY_NON_NULL);
    }

    private boolean hasReturnNullness(List<String> lines, int index) {
        String returnPrefix = eraseGenericContents(returnPrefix(lines.get(index)));
        if (hasNullnessToken(returnPrefix)) {
            return true;
        }
        int start = Math.max(0, index - 2);
        for (int i = start; i < index; i++) {
            String previous = lines.get(i).trim();
            if (previous.startsWith("@") && hasNullnessToken(previous)) {
                return true;
            }
        }
        return false;
    }

    private String returnPrefix(String line) {
        int paren = line.indexOf('(');
        if (paren < 0) {
            return line;
        }
        String beforeParameters = line.substring(0, paren);
        int methodNameStart = beforeParameters.length() - 1;
        while (methodNameStart >= 0
                && Character.isJavaIdentifierPart(beforeParameters.charAt(methodNameStart))) {
            methodNameStart--;
        }
        return beforeParameters.substring(0, Math.max(0, methodNameStart + 1));
    }

    private String eraseGenericContents(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '<') {
                depth++;
                out.append(c);
            } else if (c == '>') {
                depth = Math.max(0, depth - 1);
                out.append(c);
            } else {
                out.append(depth == 0 ? c : ' ');
            }
        }
        return out.toString();
    }

    private Set<String> exportedPackages(List<String> lines) {
        Set<String> exported = new HashSet<>();
        for (String line : lines) {
            Matcher matcher = EXPORTS.matcher(line);
            if (matcher.find()) {
                exported.add(matcher.group(1));
            }
        }
        return exported;
    }

    private boolean isPublicApiPackage(ProjectModel project,
                                       Set<String> exportedPackages,
                                       String packageName) {
        if (packageName.isBlank()) {
            return !project.publicApiJpmsExportsOnly();
        }
        if (project.publicApiJpmsExportsOnly()
                && !exportedPackages.isEmpty()
                && !exportedPackages.contains(packageName)) {
            return false;
        }
        if (!project.publicApiIncludes().isEmpty()
                && project.publicApiIncludes().stream()
                .noneMatch(pattern -> project.packageMatches(pattern, packageName))) {
            return false;
        }
        return project.publicApiExcludes().stream()
                .noneMatch(pattern -> project.packageMatches(pattern, packageName));
    }

    private List<String> parameters(String line) {
        int start = line.indexOf('(');
        int end = line.indexOf(')', start + 1);
        if (start < 0 || end < 0 || end <= start + 1) {
            return List.of();
        }
        String body = line.substring(start + 1, end).trim();
        if (body.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth = Math.max(0, depth - 1);
            } else if (c == ',' && depth == 0) {
                result.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        if (!current.isEmpty()) {
            result.add(current.toString().trim());
        }
        return result.stream().filter(s -> !s.isBlank()).toList();
    }

    private int genericTypeUseCount(String line) {
        return genericRanges(line).size();
    }

    private int annotatedGenericTypeUseCount(String line) {
        int count = 0;
        for (Range range : genericRanges(line)) {
            if (genericTypeUseAnnotated(line, range)) {
                count++;
            }
        }
        return count;
    }

    private boolean genericTypeUseAnnotated(String line, Range range) {
        if (hasNullnessToken(prefixBeforeGenericBase(line, range.start()))) {
            return true;
        }
        String content = line.substring(range.start() + 1, range.end());
        int depth = 0;
        StringBuilder direct = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth = Math.max(0, depth - 1);
            } else if (depth == 0) {
                direct.append(c);
            }
        }
        return hasNullnessToken(direct.toString());
    }

    private String prefixBeforeGenericBase(String line, int genericStart) {
        int i = genericStart - 1;
        while (i >= 0 && Character.isWhitespace(line.charAt(i))) {
            i--;
        }
        while (i >= 0 && (Character.isJavaIdentifierPart(line.charAt(i))
                || line.charAt(i) == '.')) {
            i--;
        }
        int start = Math.max(0, i - 80);
        return line.substring(start, genericStart);
    }

    private List<Range> genericRanges(String line) {
        List<Range> ranges = new ArrayList<>();
        List<Integer> stack = new ArrayList<>();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '<' && looksLikeGenericStart(line, i)) {
                stack.add(i);
            } else if (c == '>' && !stack.isEmpty()) {
                int start = stack.remove(stack.size() - 1);
                ranges.add(new Range(start, i));
            }
        }
        return ranges;
    }

    private boolean looksLikeGenericStart(String line, int index) {
        int previous = index - 1;
        while (previous >= 0 && Character.isWhitespace(line.charAt(previous))) {
            previous--;
        }
        if (previous < 0) {
            return false;
        }
        char before = line.charAt(previous);
        if (!Character.isJavaIdentifierPart(before) && before != '.' && before != '>') {
            return false;
        }
        int next = index + 1;
        while (next < line.length() && Character.isWhitespace(line.charAt(next))) {
            next++;
        }
        return next < line.length()
                && (Character.isJavaIdentifierStart(line.charAt(next))
                || line.charAt(next) == '@'
                || line.charAt(next) == '?');
    }

    private record Range(int start, int end) {}
}
