package io.github.javamodernizationlabs.jspecify.rewrite;

import io.github.javamodernizationlabs.jspecify.AnnotationCatalog;
import io.github.javamodernizationlabs.jspecify.ProjectModel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Applies source and build-file rewrites that move a project toward JSpecify.
 *
 * <p>Supported recipes include adding the JSpecify dependency, converting known
 * legacy nullness annotations, adding package-level {@code @NullMarked}, removing
 * obsolete annotation dependencies, and reporting ambiguous type-use placements.
 * Recipes can be previewed (dry run) or applied to disk.
 */
public final class JspecifyRewriter {

    private static final Set<String> UNSAFE_DEFAULT_ANNOTATIONS = Set.of(
            "ParametersAreNonnullByDefault",
            "NonNullApi",
            "NonNullFields",
            "TypeQualifierDefault",
            "DefaultAnnotation",
            "DefaultQualifier");
    private static final Pattern AMBIGUOUS_TYPE_USE = Pattern.compile(
            "@(?:[\\w.]+\\.)?Nullable\\s+[\\w.]+\\s*<[^>]+>");

    private final AnnotationCatalog catalog;

    /**
     * Creates a rewriter backed by the default annotation catalog.
     */
    public JspecifyRewriter() {
        this(AnnotationCatalog.defaults());
    }

    /**
     * Creates a rewriter backed by the given annotation catalog.
     *
     * @param catalog the catalog of legacy-to-JSpecify annotation mappings to apply
     */
    public JspecifyRewriter(AnnotationCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * Runs the requested rewrite recipes against the project.
     *
     * <p>When {@code apply} is {@code false}, changes are computed but not written,
     * producing a preview of what would change.
     *
     * @param project the project to rewrite
     * @param rawRecipes the recipe names or aliases to run; defaults to converting
     *        known annotations when {@code null} or empty
     * @param apply whether to write changes to disk ({@code true}) or only preview them
     * @return the aggregate result of the run
     * @throws IOException if a source or build file cannot be read or written
     */
    public RewriteResult rewrite(ProjectModel project, List<String> rawRecipes, boolean apply)
            throws IOException {
        Set<String> recipes = normalizeRecipes(rawRecipes);
        List<RewriteChange> changes = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (recipes.contains("add-dependency")) {
            addDependency(project, apply, changes, warnings);
        }
        if (recipes.contains("convert-known-annotations")) {
            convertKnownAnnotations(project, apply, changes, warnings);
        }
        if (recipes.contains("add-null-marked")) {
            addNullMarked(project, apply, changes, warnings);
        }
        if (recipes.contains("remove-old-annotation-dependencies")) {
            removeOldAnnotationDependencies(project, apply, changes, warnings);
        }
        if (recipes.contains("fix-type-use-annotation-placement")) {
            reportAmbiguousTypeUse(project, warnings);
        }
        return new RewriteResult(apply, changes, warnings);
    }

    private Set<String> normalizeRecipes(List<String> rawRecipes) {
        Set<String> recipes = new LinkedHashSet<>();
        for (String raw : rawRecipes == null || rawRecipes.isEmpty()
                ? List.of("convert-known-annotations")
                : rawRecipes) {
            for (String token : raw.split(",")) {
                String normalized = token.trim().toLowerCase(Locale.ROOT);
                if (normalized.isBlank()) {
                    continue;
                }
                if (normalized.endsWith(".migrate") || normalized.equals("migrate")) {
                    recipes.add("add-dependency");
                    recipes.add("fix-type-use-annotation-placement");
                    recipes.add("convert-known-annotations");
                } else if (normalized.endsWith(".springpreset")
                        || normalized.endsWith(".reactorpreset")
                        || normalized.endsWith(".micrometerpreset")
                        || normalized.equals("spring-preset")
                        || normalized.equals("reactor-preset")
                        || normalized.equals("micrometer-preset")) {
                    recipes.add("convert-known-annotations");
                } else if (normalized.endsWith(".addjspecifydependency")
                        || normalized.endsWith(".adddependency")
                        || normalized.equals("add-dependency")
                        || normalized.equals("add-jspecify-dependency")) {
                    recipes.add("add-dependency");
                } else if (normalized.endsWith(".convertknownannotations")
                        || normalized.equals("convert-known-annotations")) {
                    recipes.add("convert-known-annotations");
                } else if (normalized.endsWith(".addnullmarkedtopackage")
                        || normalized.equals("add-null-marked")) {
                    recipes.add("add-null-marked");
                } else if (normalized.endsWith(".removeoldannotationdependencies")
                        || normalized.equals("remove-old-annotation-dependencies")) {
                    recipes.add("remove-old-annotation-dependencies");
                } else if (normalized.endsWith(".fixtypeuseannotationplacement")
                        || normalized.equals("fix-type-use-annotation-placement")) {
                    recipes.add("fix-type-use-annotation-placement");
                } else {
                    recipes.add(normalized);
                }
            }
        }
        return recipes;
    }

    private void addDependency(ProjectModel project,
                               boolean apply,
                               List<RewriteChange> changes,
                               List<String> warnings) throws IOException {
        Path gradleKts = project.rootDirectory().resolve("build.gradle.kts");
        Path gradleGroovy = project.rootDirectory().resolve("build.gradle");
        Path pom = project.rootDirectory().resolve("pom.xml");

        if (Files.isRegularFile(gradleKts)) {
            addGradleDependency(gradleKts, apply, changes);
        } else if (Files.isRegularFile(gradleGroovy)) {
            addGradleDependency(gradleGroovy, apply, changes);
        } else if (Files.isRegularFile(pom)) {
            addMavenDependency(pom, apply, changes);
        } else {
            warnings.add("No supported Gradle or Maven build file found for add-dependency.");
        }
    }

    private void addGradleDependency(Path file, boolean apply, List<RewriteChange> changes)
            throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        if (content.contains("org.jspecify:jspecify")) {
            return;
        }
        String dependency = file.getFileName().toString().endsWith(".kts")
                ? "    compileOnly(\"org.jspecify:jspecify:1.0.0\")\n"
                : "    compileOnly 'org.jspecify:jspecify:1.0.0'\n";
        int dependenciesStart = topLevelBlockOpeningBrace(content, "dependencies");
        String updated;
        if (dependenciesStart >= 0) {
            int insert = dependenciesStart + 1;
            updated = content.substring(0, insert) + "\n" + dependency
                    + content.substring(insert);
        } else {
            updated = content + "\n\ndependencies {\n" + dependency + "}\n";
        }
        if (apply) {
            Files.writeString(file, updated, StandardCharsets.UTF_8);
        }
        changes.add(new RewriteChange(file, "Add compileOnly JSpecify dependency", 1, List.of()));
    }

    private void addMavenDependency(Path file, boolean apply, List<RewriteChange> changes)
            throws IOException {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            var document = factory.newDocumentBuilder().parse(file.toFile());
            var projectElement = document.getDocumentElement();
            org.w3c.dom.Element dependencies = directChild(projectElement, "dependencies");
            if (dependencies != null && hasDependency(dependencies, "org.jspecify", "jspecify")) {
                return;
            }
            if (dependencies == null) {
                dependencies = document.createElement("dependencies");
                projectElement.appendChild(document.createTextNode("\n  "));
                projectElement.appendChild(dependencies);
                projectElement.appendChild(document.createTextNode("\n"));
            }
            org.w3c.dom.Element dependency = document.createElement("dependency");
            appendTextElement(document, dependency, "groupId", "org.jspecify");
            appendTextElement(document, dependency, "artifactId", "jspecify");
            appendTextElement(document, dependency, "version", "1.0.0");
            appendTextElement(document, dependency, "scope", "provided");
            dependency.appendChild(document.createTextNode("\n    "));
            dependencies.appendChild(document.createTextNode("\n    "));
            dependencies.appendChild(dependency);
            dependencies.appendChild(document.createTextNode("\n  "));
            if (apply) {
                var transformer = TransformerFactory.newInstance().newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
                transformer.transform(new DOMSource(document), new StreamResult(file.toFile()));
            }
            changes.add(new RewriteChange(file, "Add provided-scope JSpecify dependency",
                    1, List.of()));
        } catch (Exception e) {
            throw new IOException("Unable to add Maven JSpecify dependency to " + file, e);
        }
    }

    private void convertKnownAnnotations(ProjectModel project,
                                         boolean apply,
                                         List<RewriteChange> changes,
                                         List<String> warnings) throws IOException {
        for (Path root : project.sourceRoots()) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> stream = project.walk(root)) {
                Iterable<Path> files = stream
                        .filter(Files::isRegularFile)
                        .filter(project::shouldScan)
                        .filter(p -> p.toString().endsWith(".java"))
                        ::iterator;
                for (Path file : files) {
                    convertFile(file, apply, changes, warnings);
                }
            }
        }
    }

    private void convertFile(Path file,
                             boolean apply,
                             List<RewriteChange> changes,
                             List<String> warnings) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        List<String> localWarnings = unsafeWarnings(file, content);
        String updated = content;
        int replacements = 0;

        for (var mapping : catalog.mappings().entrySet()) {
            String source = mapping.getKey();
            String target = mapping.getValue();
            boolean sourceImported = importsAnnotation(content, source);
            Pattern sourcePattern = Pattern.compile("\\b" + Pattern.quote(source) + "\\b");
            int before = occurrences(sourcePattern, updated);
            if (before > 0) {
                updated = sourcePattern.matcher(updated)
                        .replaceAll(Matcher.quoteReplacement(target));
                replacements += before;
            }
            String sourceSimple = source.substring(source.lastIndexOf('.') + 1);
            String targetSimple = target.substring(target.lastIndexOf('.') + 1);
            Pattern importPattern = Pattern.compile("import\\s+"
                    + Pattern.quote(target) + "\\s*;");
            if (sourceImported && importPattern.matcher(updated).find()) {
                Pattern simplePattern = Pattern.compile("@" + Pattern.quote(sourceSimple) + "\\b");
                int simpleBefore = occurrences(simplePattern, updated);
                if (simpleBefore > 0) {
                    updated = simplePattern.matcher(updated)
                            .replaceAll(Matcher.quoteReplacement("@" + targetSimple));
                    replacements += simpleBefore;
                }
            }
        }
        updated = deduplicateImports(updated);

        if (replacements > 0) {
            if (apply) {
                Files.writeString(file, updated, StandardCharsets.UTF_8);
            }
            changes.add(new RewriteChange(file,
                    "Convert known legacy nullness annotations to JSpecify",
                    replacements,
                    localWarnings));
        } else {
            warnings.addAll(localWarnings);
        }
    }

    private List<String> unsafeWarnings(Path file, String content) {
        List<String> warnings = new ArrayList<>();
        for (String annotation : UNSAFE_DEFAULT_ANNOTATIONS) {
            if (content.contains("@" + annotation)) {
                warnings.add(file + ": default/meta annotation @" + annotation
                        + " requires manual JSpecify package policy review.");
            }
        }
        for (String annotation : List.of(
                "org.springframework.lang.NonNullApi",
                "org.springframework.lang.NonNullFields",
                "reactor.util.annotation.NonNullApi",
                "reactor.util.annotation.NonNullFields",
                "io.micrometer.common.lang.NonNullApi",
                "io.micrometer.common.lang.NonNullFields")) {
            if (content.contains("@" + annotation)) {
                warnings.add(file + ": default/meta annotation @" + annotation
                        + " requires manual JSpecify package policy review.");
            }
        }
        return warnings;
    }

    private int occurrences(Pattern pattern, String text) {
        int count = 0;
        var matcher = pattern.matcher(text);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private void addNullMarked(ProjectModel project,
                               boolean apply,
                               List<RewriteChange> changes,
                               List<String> warnings) throws IOException {
        Map<String, Path> packages = discoverPackages(project);
        for (var entry : packages.entrySet()) {
            String packageName = entry.getKey();
            if (!shouldMarkPackage(project, packageName)) {
                continue;
            }
            Path packageDirectory = entry.getValue();
            Path packageInfo = packageDirectory.resolve("package-info.java");
            if (Files.isRegularFile(packageInfo)) {
                String content = Files.readString(packageInfo, StandardCharsets.UTF_8);
                if (content.contains("@NullMarked")
                        || content.contains("@" + AnnotationCatalog.JSPECIFY_NULL_MARKED)) {
                    continue;
                }
                List<String> unsafe = unsafeWarnings(packageInfo, content);
                if (!unsafe.isEmpty()) {
                    warnings.addAll(unsafe);
                    continue;
                }
                String updated = content.replaceFirst("package\\s+" + Pattern.quote(packageName)
                                + "\\s*;",
                        Matcher.quoteReplacement("@NullMarked\npackage " + packageName
                                + ";\n\nimport org.jspecify.annotations.NullMarked;"));
                if (apply) {
                    Files.writeString(packageInfo, updated, StandardCharsets.UTF_8);
                }
                changes.add(new RewriteChange(packageInfo, "Add package-level @NullMarked",
                        1, List.of()));
            } else {
                String created = "@NullMarked\npackage " + packageName
                        + ";\n\nimport org.jspecify.annotations.NullMarked;\n";
                if (apply) {
                    Files.writeString(packageInfo, created, StandardCharsets.UTF_8);
                }
                changes.add(new RewriteChange(packageInfo, "Create package-info.java with @NullMarked",
                        1, List.of()));
            }
        }
    }

    private boolean shouldMarkPackage(ProjectModel project, String packageName) {
        if (project.leaveUnmarkedPackages().stream()
                .anyMatch(pattern -> project.packageMatches(pattern, packageName))) {
            return false;
        }
        return project.markPackages().isEmpty()
                || project.markPackages().stream()
                .anyMatch(pattern -> project.packageMatches(pattern, packageName));
    }

    private Map<String, Path> discoverPackages(ProjectModel project) throws IOException {
        Map<String, Path> packages = new java.util.LinkedHashMap<>();
        for (Path root : project.sourceRoots()) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> stream = project.walk(root)) {
                Iterable<Path> files = stream
                        .filter(Files::isRegularFile)
                        .filter(project::shouldScan)
                        .filter(p -> p.toString().endsWith(".java"))
                        ::iterator;
                for (Path file : files) {
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    var matcher = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;")
                            .matcher(content);
                    if (matcher.find()) {
                        packages.putIfAbsent(matcher.group(1), file.getParent());
                    }
                }
            }
        }
        return packages;
    }

    private void removeOldAnnotationDependencies(ProjectModel project,
                                                 boolean apply,
                                                 List<RewriteChange> changes,
                                                 List<String> warnings) throws IOException {
        if (hasLegacyAnnotationUsages(project)) {
            warnings.add("Old annotation dependencies were not removed because legacy usages remain.");
            return;
        }
        Path gradleKts = project.rootDirectory().resolve("build.gradle.kts");
        if (Files.isRegularFile(gradleKts)) {
            removeGradleLegacyDependencies(gradleKts, apply, changes);
        }
        Path gradleGroovy = project.rootDirectory().resolve("build.gradle");
        if (Files.isRegularFile(gradleGroovy)) {
            removeGradleLegacyDependencies(gradleGroovy, apply, changes);
        }
        Path pom = project.rootDirectory().resolve("pom.xml");
        if (Files.isRegularFile(pom)) {
            removeMavenLegacyDependencies(pom, apply, changes);
        }
    }

    private boolean hasLegacyAnnotationUsages(ProjectModel project) throws IOException {
        for (Path root : project.sourceRoots()) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> stream = project.walk(root)) {
                Iterable<Path> files = stream
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".java"))
                        ::iterator;
                for (Path file : files) {
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    for (String legacy : catalog.knownLegacyAnnotations()) {
                        if (content.contains(legacy)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private void removeGradleLegacyDependencies(Path buildFile,
                                                boolean apply,
                                                List<RewriteChange> changes) throws IOException {
        String content = Files.readString(buildFile, StandardCharsets.UTF_8);
        Matcher lines = Pattern.compile(".*(?:\\R|\\z)").matcher(content);
        StringBuilder kept = new StringBuilder(content.length());
        int removed = 0;
        while (lines.find()) {
            String line = lines.group();
            if (line.isEmpty()) {
                continue;
            }
            if (line.contains("org.jetbrains:annotations")
                    || line.contains("com.google.code.findbugs:jsr305")
                    || line.contains("javax.annotation:javax.annotation-api")
                    || line.contains("jakarta.annotation:jakarta.annotation-api")) {
                removed++;
            } else {
                kept.append(line);
            }
        }
        if (removed > 0) {
            if (apply) {
                Files.writeString(buildFile, kept.toString(), StandardCharsets.UTF_8);
            }
            changes.add(new RewriteChange(buildFile, "Remove legacy annotation dependencies",
                    removed, List.of()));
        }
    }

    private void removeMavenLegacyDependencies(Path pom,
                                               boolean apply,
                                               List<RewriteChange> changes) throws IOException {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            var document = factory.newDocumentBuilder().parse(pom.toFile());
            var dependencies = document.getElementsByTagName("dependency");
            List<org.w3c.dom.Node> toRemove = new ArrayList<>();
            for (int i = 0; i < dependencies.getLength(); i++) {
                org.w3c.dom.Node dependency = dependencies.item(i);
                String groupId = childText(dependency, "groupId");
                String artifactId = childText(dependency, "artifactId");
                if (isLegacyAnnotationDependency(groupId, artifactId)) {
                    toRemove.add(dependency);
                }
            }
            if (toRemove.isEmpty()) {
                return;
            }
            for (org.w3c.dom.Node dependency : toRemove) {
                dependency.getParentNode().removeChild(dependency);
            }
            if (apply) {
                var transformer = TransformerFactory.newInstance().newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
                transformer.transform(new DOMSource(document), new StreamResult(pom.toFile()));
            }
            changes.add(new RewriteChange(pom, "Remove legacy Maven annotation dependencies",
                    toRemove.size(), List.of()));
        } catch (Exception e) {
            throw new IOException("Unable to remove Maven legacy annotation dependencies from "
                    + pom, e);
        }
    }

    private String childText(org.w3c.dom.Node node, String name) {
        var children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                    && child.getNodeName().equals(name)) {
                return child.getTextContent().trim();
            }
        }
        return "";
    }

    private int topLevelBlockOpeningBrace(String content, String blockName) {
        Pattern block = Pattern.compile("\\b" + Pattern.quote(blockName) + "\\s*\\{");
        Matcher matcher = block.matcher(content);
        while (matcher.find()) {
            if (braceDepth(content, matcher.start()) == 0) {
                return matcher.end() - 1;
            }
        }
        return -1;
    }

    private int braceDepth(String content, int endExclusive) {
        int depth = 0;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inString = false;
        char stringDelimiter = '\0';
        for (int i = 0; i < endExclusive; i++) {
            char c = content.charAt(i);
            char next = i + 1 < endExclusive ? content.charAt(i + 1) : '\0';
            if (inLineComment) {
                if (c == '\n' || c == '\r') {
                    inLineComment = false;
                }
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (inString) {
                if (c == '\\' && i + 1 < endExclusive) {
                    i++;
                } else if (c == stringDelimiter) {
                    inString = false;
                }
                continue;
            }
            if (c == '/' && next == '/') {
                inLineComment = true;
                i++;
            } else if (c == '/' && next == '*') {
                inBlockComment = true;
                i++;
            } else if (c == '"' || c == '\'') {
                inString = true;
                stringDelimiter = c;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth = Math.max(0, depth - 1);
            }
        }
        return depth;
    }

    private boolean importsAnnotation(String content, String annotation) {
        String packageName = annotation.substring(0, annotation.lastIndexOf('.'));
        return Pattern.compile("(?m)^\\s*import\\s+" + Pattern.quote(annotation) + "\\s*;")
                .matcher(content).find()
                || Pattern.compile("(?m)^\\s*import\\s+" + Pattern.quote(packageName)
                + "\\.\\*\\s*;").matcher(content).find();
    }

    private String deduplicateImports(String content) {
        Set<String> seenImports = new LinkedHashSet<>();
        StringBuilder out = new StringBuilder(content.length());
        Matcher lines = Pattern.compile(".*(?:\\R|\\z)").matcher(content);
        while (lines.find()) {
            String line = lines.group();
            if (line.isEmpty()) {
                continue;
            }
            Matcher importMatcher = Pattern.compile(
                    "^\\s*import\\s+(org\\.jspecify\\.annotations\\.[\\w]+)\\s*;")
                    .matcher(line.stripTrailing());
            if (importMatcher.find() && !seenImports.add(importMatcher.group(1))) {
                continue;
            }
            out.append(line);
        }
        return out.toString();
    }

    private org.w3c.dom.Element directChild(org.w3c.dom.Element parent, String name) {
        var children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                    && child.getNodeName().equals(name)) {
                return (org.w3c.dom.Element) child;
            }
        }
        return null;
    }

    private boolean hasDependency(org.w3c.dom.Element dependencies,
                                  String groupId,
                                  String artifactId) {
        var children = dependencies.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE
                    && child.getNodeName().equals("dependency")
                    && childText(child, "groupId").equals(groupId)
                    && childText(child, "artifactId").equals(artifactId)) {
                return true;
            }
        }
        return false;
    }

    private void appendTextElement(org.w3c.dom.Document document,
                                   org.w3c.dom.Element parent,
                                   String name,
                                   String text) {
        parent.appendChild(document.createTextNode("\n      "));
        org.w3c.dom.Element child = document.createElement(name);
        child.setTextContent(text);
        parent.appendChild(child);
    }

    private boolean isLegacyAnnotationDependency(String groupId, String artifactId) {
        return (groupId.equals("org.jetbrains") && artifactId.equals("annotations"))
                || (groupId.equals("com.google.code.findbugs") && artifactId.equals("jsr305"))
                || (groupId.equals("com.google.code.findbugs") && artifactId.equals("annotations"))
                || (groupId.equals("javax.annotation") && artifactId.equals("javax.annotation-api"))
                || (groupId.equals("jakarta.annotation") && artifactId.equals("jakarta.annotation-api"));
    }

    private void reportAmbiguousTypeUse(ProjectModel project, List<String> warnings)
            throws IOException {
        for (Path root : project.sourceRoots()) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> stream = project.walk(root)) {
                Iterable<Path> files = stream
                        .filter(Files::isRegularFile)
                        .filter(project::shouldScan)
                        .filter(p -> p.toString().endsWith(".java"))
                        ::iterator;
                for (Path file : files) {
                    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                    for (int i = 0; i < lines.size(); i++) {
                        if (AMBIGUOUS_TYPE_USE.matcher(lines.get(i)).find()) {
                            warnings.add(file + ":" + (i + 1)
                                    + ": Ambiguous annotation migration: "
                                    + lines.get(i).trim()
                                    + " Possible meanings: nullable container or nullable element. "
                                    + "Manual review required.");
                        }
                    }
                }
            }
        }
    }
}
