package io.github.javamodernizationlabs.jspecify.kotlin;

import io.github.javamodernizationlabs.jspecify.ProjectModel;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Verifies how a project's public Java API surfaces in Kotlin under JSpecify
 * nullness contracts.
 *
 * <p>The verifier scans public types, infers the Kotlin-visible nullness of
 * their no-argument methods, and generates a Kotlin sample file that exercises
 * the API. Methods without an explicit nullness contract are reported as
 * platform-type leaks. The samples can optionally be compiled with
 * {@code kotlinc} when it is available on the {@code PATH}.
 */
public final class KotlinInteropVerifier {

    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern PUBLIC_TYPE = Pattern.compile(
            "\\bpublic\\s+(?:class|interface|record|enum)\\s+(\\w+)");
    private static final Pattern PUBLIC_NO_ARG_METHOD = Pattern.compile(
            "\\bpublic\\s+(.+?)\\s+(\\w+)\\s*\\(\\s*\\)\\s*(?:throws\\s+[\\w.,\\s]+)?[;{]");
    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+([\\w.]+)\\s*;");

    /**
     * Creates a {@code KotlinInteropVerifier}.
     */
    public KotlinInteropVerifier() {
    }

    /**
     * Generates and optionally compiles Kotlin interop samples for the
     * project's public API and writes a verification report.
     *
     * @param project the project whose public API is verified
     * @param outputDirectory the directory to write generated samples and the
     *     report into; created if it does not exist
     * @param generateSamples whether to write the generated Kotlin sample file
     * @param compileSamples whether to compile the samples with {@code kotlinc}
     *     (samples are generated first if needed)
     * @param classpath additional classpath entries passed to {@code kotlinc};
     *     may be {@code null} or empty
     * @return the result describing what was generated, the compile status and
     *     any warnings
     * @throws IOException if the output directory or any file cannot be written
     */
    public KotlinVerificationResult verify(ProjectModel project,
                                           Path outputDirectory,
                                           boolean generateSamples,
                                           boolean compileSamples,
                                           List<Path> classpath) throws IOException {
        Files.createDirectories(outputDirectory);
        List<String> warnings = new ArrayList<>();
        Path sampleFile = outputDirectory.resolve("KotlinInteropSamples.kt");
        if (generateSamples) {
            Files.writeString(sampleFile, sampleSource(project, warnings), StandardCharsets.UTF_8);
        }
        String compileStatus = "not requested";
        if (compileSamples) {
            if (!generateSamples) {
                Files.writeString(sampleFile, sampleSource(project, warnings), StandardCharsets.UTF_8);
            }
            compileStatus = compile(sampleFile, outputDirectory, classpath, warnings);
        }
        writeReport(outputDirectory, sampleFile, generateSamples || compileSamples,
                compileSamples, compileStatus, warnings);
        return new KotlinVerificationResult(outputDirectory, sampleFile,
                generateSamples || compileSamples, compileSamples, compileStatus, warnings);
    }

    private String sampleSource(ProjectModel project, List<String> warnings) throws IOException {
        List<ApiType> apiTypes = publicApi(project);
        StringBuilder out = new StringBuilder();
        out.append("package jspecify.verification\n\n");
        out.append("@Suppress(\"UNUSED_PARAMETER\", \"UNUSED_VARIABLE\")\n");
        out.append("object KotlinInteropSamples {\n");
        for (ApiType type : apiTypes) {
            out.append("    fun verify").append(type.simpleName()).append("(api: ")
                    .append(type.qualifiedName()).append(") {\n");
            if (type.noArgMethods().isEmpty()) {
                out.append("        api.toString()\n");
            } else {
                for (MethodAssertion method : type.noArgMethods()) {
                    method.warning().ifPresent(warnings::add);
                    out.append("        val ").append(method.name()).append("Value");
                    method.kotlinType().ifPresent(kotlinType -> out.append(": ").append(kotlinType));
                    out.append(" = api.").append(method.name()).append("()\n");
                }
            }
            out.append("    }\n\n");
        }
        if (apiTypes.isEmpty()) {
            out.append("    fun noPublicApiDetected() = Unit\n");
        }
        out.append("}\n");
        return out.toString();
    }

    private List<ApiType> publicApi(ProjectModel project) throws IOException {
        Set<String> nullMarkedPackages = nullMarkedPackages(project);
        List<ApiType> types = new ArrayList<>();
        for (Path root : project.sourceRoots()) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> stream = project.walk(root)) {
                Iterable<Path> javaFiles = stream
                        .filter(Files::isRegularFile)
                        .filter(project::shouldScan)
                        .filter(path -> path.toString().endsWith(".java"))
                        ::iterator;
                for (Path file : javaFiles) {
                    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                    String packageName = packageName(lines);
                    Optional<String> typeName = publicTypeName(lines);
                    if (typeName.isEmpty()) {
                        continue;
                    }
                    Map<String, String> imports = imports(lines);
                    String qualifiedName = packageName.isBlank()
                            ? typeName.get()
                            : packageName + "." + typeName.get();
                    boolean nullMarked = hasNullMarked(lines) || nullMarkedPackages.contains(packageName);
                    types.add(new ApiType(qualifiedName, typeName.get(),
                            publicNoArgMethods(qualifiedName, lines, imports, nullMarked)));
                }
            }
        }
        return types;
    }

    private Set<String> nullMarkedPackages(ProjectModel project) throws IOException {
        Set<String> packages = new HashSet<>();
        for (Path root : project.sourceRoots()) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> stream = project.walk(root)) {
                Iterable<Path> files = stream
                        .filter(Files::isRegularFile)
                        .filter(project::shouldScan)
                        .filter(path -> path.getFileName().toString().equals("package-info.java"))
                        ::iterator;
                for (Path file : files) {
                    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                    if (hasNullMarked(lines)) {
                        String packageName = packageName(lines);
                        if (!packageName.isBlank()) {
                            packages.add(packageName);
                        }
                    }
                }
            }
        }
        return packages;
    }

    private String packageName(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = PACKAGE.matcher(line);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "";
    }

    private Optional<String> publicTypeName(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = PUBLIC_TYPE.matcher(line);
            if (matcher.find()) {
                return Optional.of(matcher.group(1));
            }
        }
        return Optional.empty();
    }

    private Map<String, String> imports(List<String> lines) {
        Map<String, String> imports = new HashMap<>();
        for (String line : lines) {
            Matcher matcher = IMPORT.matcher(line);
            if (matcher.find()) {
                String fqn = matcher.group(1);
                imports.put(fqn.substring(fqn.lastIndexOf('.') + 1), fqn);
            }
        }
        return imports;
    }

    private boolean hasNullMarked(List<String> lines) {
        return lines.stream().anyMatch(line -> line.contains("@NullMarked")
                || line.contains("@org.jspecify.annotations.NullMarked"));
    }

    private List<MethodAssertion> publicNoArgMethods(String qualifiedTypeName,
                                                     List<String> lines,
                                                     Map<String, String> imports,
                                                     boolean nullMarked) {
        List<MethodAssertion> methods = new ArrayList<>();
        boolean inPublicType = false;
        int braceDepth = 0;
        for (String line : lines) {
            if (!inPublicType && PUBLIC_TYPE.matcher(line).find()) {
                inPublicType = true;
            }
            Matcher matcher = PUBLIC_NO_ARG_METHOD.matcher(line);
            if (inPublicType && braceDepth == 1 && matcher.find()) {
                String method = matcher.group(2);
                if (!method.equals("if") && !method.equals("for") && !method.equals("while")) {
                    String returnType = matcher.group(1).trim();
                    if (!hasMethodTypeParameters(returnType)) {
                        methods.add(methodAssertion(qualifiedTypeName, method, returnType,
                                imports, nullMarked));
                    }
                }
            }
            if (inPublicType) {
                braceDepth += braceDelta(line);
            }
        }
        return methods;
    }

    private boolean hasMethodTypeParameters(String javaReturnType) {
        // A generic method such as {@code public <T> T identity()} captures a
        // leading type-parameter declaration in the return type. The unbound
        // identifier cannot be expressed in a Kotlin sample, so skip the method.
        return cleanReturnType(javaReturnType).startsWith("<");
    }

    private int braceDelta(String line) {
        int delta = 0;
        boolean inString = false;
        boolean inChar = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && (inString || inChar) && i + 1 < line.length()) {
                i++;
                continue;
            }
            if (!inChar && c == '"') {
                inString = !inString;
            } else if (!inString && c == '\'') {
                inChar = !inChar;
            } else if (!inString && !inChar && c == '{') {
                delta++;
            } else if (!inString && !inChar && c == '}') {
                delta--;
            }
        }
        return delta;
    }

    private MethodAssertion methodAssertion(String qualifiedTypeName,
                                            String methodName,
                                            String javaReturnType,
                                            Map<String, String> imports,
                                            boolean nullMarked) {
        Nullness nullness = nullness(javaReturnType, nullMarked);
        String cleaned = cleanReturnType(javaReturnType);
        if (cleaned.equals("void")) {
            return new MethodAssertion(methodName, Optional.empty(), Optional.empty());
        }
        String kotlinType = kotlinType(cleaned, imports);
        if (nullness == Nullness.NULLABLE) {
            return new MethodAssertion(methodName, Optional.of(kotlinType + "?"), Optional.empty());
        }
        if (nullness == Nullness.NON_NULL) {
            return new MethodAssertion(methodName, Optional.of(kotlinType), Optional.empty());
        }
        String warning = "KOTLIN_PLATFORM_TYPE_LEAK Method: " + qualifiedTypeName + "#"
                + methodName + "() Expected: explicit nullness contract Observed: platform type "
                + kotlinType + "! Recommendation: add @NullMarked to package or annotate return type.";
        return new MethodAssertion(methodName, Optional.empty(), Optional.of(warning));
    }

    private Nullness nullness(String javaReturnType, boolean nullMarked) {
        if (javaReturnType.contains("@Nullable")
                || javaReturnType.contains("@org.jspecify.annotations.Nullable")) {
            return Nullness.NULLABLE;
        }
        if (javaReturnType.contains("@NonNull")
                || javaReturnType.contains("@org.jspecify.annotations.NonNull")
                || nullMarked
                || primitive(cleanReturnType(javaReturnType))) {
            return Nullness.NON_NULL;
        }
        return Nullness.UNSPECIFIED;
    }

    private String cleanReturnType(String javaReturnType) {
        return javaReturnType
                .replaceAll("@[\\w.]+\\s*", "")
                .replaceAll("\\b(static|final|synchronized|native|strictfp|default|abstract)\\b\\s*", "")
                .trim();
    }

    private String kotlinType(String javaType, Map<String, String> imports) {
        javaType = javaType.trim();
        int dimensions = 0;
        while (javaType.endsWith("[]")) {
            dimensions++;
            javaType = javaType.substring(0, javaType.length() - 2).trim();
        }
        StringBuilder mapped = new StringBuilder(kotlinNonArrayType(javaType, imports));
        if (dimensions == 0) {
            return mapped.toString();
        }
        if (dimensions == 1 && primitive(javaType)) {
            return switch (javaType) {
                case "boolean" -> "BooleanArray";
                case "byte" -> "ByteArray";
                case "short" -> "ShortArray";
                case "int" -> "IntArray";
                case "long" -> "LongArray";
                case "float" -> "FloatArray";
                case "double" -> "DoubleArray";
                case "char" -> "CharArray";
                default -> mapped.toString();
            };
        }
        for (int i = 0; i < dimensions; i++) {
            mapped = new StringBuilder("Array<" + mapped + ">");
        }
        return mapped.toString();
    }

    private String kotlinNonArrayType(String javaType, Map<String, String> imports) {
        int genericStart = javaType.indexOf('<');
        if (genericStart >= 0 && javaType.endsWith(">")) {
            String base = javaType.substring(0, genericStart).trim();
            String args = javaType.substring(genericStart + 1, javaType.length() - 1);
            String mappedBase = kotlinSimpleType(base, imports);
            String mappedArgs = splitTopLevel(args).stream()
                    .map(String::trim)
                    .filter(arg -> !arg.isBlank())
                    .map(arg -> kotlinType(normalizeWildcard(arg), imports))
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("");
            return mappedBase + "<" + mappedArgs + ">";
        }
        return kotlinSimpleType(javaType, imports);
    }

    private String kotlinSimpleType(String javaType, Map<String, String> imports) {
        return switch (javaType) {
            case "boolean" -> "Boolean";
            case "byte" -> "Byte";
            case "short" -> "Short";
            case "int" -> "Int";
            case "long" -> "Long";
            case "float" -> "Float";
            case "double" -> "Double";
            case "char" -> "Char";
            case "String", "java.lang.String" -> "String";
            case "Object", "java.lang.Object" -> "Any";
            default -> imports.getOrDefault(javaType, javaType);
        };
    }

    private List<String> splitTopLevel(String body) {
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
                result.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        if (!current.isEmpty()) {
            result.add(current.toString());
        }
        return result;
    }

    private String normalizeWildcard(String javaType) {
        String trimmed = javaType.trim();
        if (trimmed.equals("?")) {
            return "Any";
        }
        if (trimmed.startsWith("? extends ")) {
            return trimmed.substring("? extends ".length()).trim();
        }
        if (trimmed.startsWith("? super ")) {
            return trimmed.substring("? super ".length()).trim();
        }
        return trimmed;
    }

    private boolean primitive(String javaType) {
        return Set.of("boolean", "byte", "short", "int", "long", "float", "double", "char")
                .contains(javaType);
    }

    private String compile(Path sampleFile,
                           Path outputDirectory,
                           List<Path> classpath,
                           List<String> warnings) {
        if (!commandAvailable()) {
            warnings.add("kotlinc was not found on PATH; generated samples were not compiled.");
            return "skipped: kotlinc not found";
        }
        try {
            List<String> command = new ArrayList<>();
            command.add("kotlinc");
            command.add(sampleFile.toString());
            if (classpath != null && !classpath.isEmpty()) {
                command.add("-classpath");
                command.add(String.join(File.pathSeparator,
                        classpath.stream().map(Path::toString).toList()));
            }
            command.add("-d");
            command.add(outputDirectory.resolve("kotlin-verification.jar").toString());
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            CompletableFuture<String> outputFuture = readOutputAsync(process);
            boolean exited = process.waitFor(Duration.ofSeconds(30).toMillis(),
                    TimeUnit.MILLISECONDS);
            if (!exited) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                warnings.add("kotlinc timed out after 30 seconds.");
                return "failed: timeout";
            }
            String output = outputFuture.get(5, TimeUnit.SECONDS);
            if (process.exitValue() != 0) {
                warnings.add(output.isBlank() ? "kotlinc failed." : output.strip());
                return "failed";
            }
            return "compiled";
        } catch (IOException e) {
            warnings.add("Unable to run kotlinc: " + e.getMessage());
            return "failed";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            warnings.add("kotlinc was interrupted.");
            return "failed";
        } catch (ExecutionException | TimeoutException e) {
            warnings.add("Unable to read kotlinc output: " + e.getMessage());
            return "failed";
        }
    }

    private boolean commandAvailable() {
        try {
            Process process = new ProcessBuilder("kotlinc", "-version")
                    .redirectErrorStream(true)
                    .start();
            CompletableFuture<String> outputFuture = readOutputAsync(process);
            boolean available = process.waitFor(10, TimeUnit.SECONDS)
                    && process.exitValue() == 0;
            if (!available && process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            outputFuture.get(5, TimeUnit.SECONDS);
            return available;
        } catch (IOException | ExecutionException | TimeoutException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private CompletableFuture<String> readOutputAsync(Process process) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "";
            }
        });
    }

    private void writeReport(Path outputDirectory,
                             Path sampleFile,
                             boolean samplesGenerated,
                             boolean compileRequested,
                             String compileStatus,
                             List<String> warnings) throws IOException {
        StringBuilder report = new StringBuilder();
        report.append("# Kotlin interop verification\n\n");
        report.append("Samples generated: `").append(samplesGenerated).append("`\n");
        report.append("Sample file: `").append(sampleFile.getFileName()).append("`\n");
        report.append("Compile requested: `").append(compileRequested).append("`\n");
        report.append("Compile status: `").append(compileStatus).append("`\n");
        if (!warnings.isEmpty()) {
            report.append("\n## Warnings\n\n");
            for (String warning : warnings) {
                report.append("- ").append(warning.replace('\n', ' ')).append('\n');
            }
        }
        Files.writeString(outputDirectory.resolve("kotlin-verification.md"),
                report.toString(), StandardCharsets.UTF_8);
    }

    private enum Nullness { NON_NULL, NULLABLE, UNSPECIFIED }

    private record MethodAssertion(
            String name,
            Optional<String> kotlinType,
            Optional<String> warning
    ) {}

    private record ApiType(
            String qualifiedName,
            String simpleName,
            List<MethodAssertion> noArgMethods
    ) {}
}
