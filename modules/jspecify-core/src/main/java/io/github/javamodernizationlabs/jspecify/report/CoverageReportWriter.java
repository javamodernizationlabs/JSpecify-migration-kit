package io.github.javamodernizationlabs.jspecify.report;

import io.github.javamodernizationlabs.jspecify.coverage.CoverageSummary;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Renders a {@link CoverageSummary} into Markdown, JSON and HTML reports.
 *
 * <p>Each format presents the same nullness-coverage metrics; the writer can
 * emit any subset of the formats to a directory.
 */
public final class CoverageReportWriter {

    /**
     * Creates a {@code CoverageReportWriter}.
     */
    public CoverageReportWriter() {
    }

    /**
     * Renders the coverage summary as a Markdown table.
     *
     * @param summary the coverage summary to render
     * @return the coverage report as Markdown text
     */
    public String markdown(CoverageSummary summary) {
        return String.format(Locale.ROOT, """
                # JSpecify Coverage

                | Metric | Value |
                |---|---:|
                | Public API elements | %d |
                | Specified public API elements | %d |
                | Public API coverage | %.1f%% |
                | Packages seen | %d |
                | @NullMarked packages | %d |
                | @NullMarked package coverage | %.1f%% |
                | Public methods | %d |
                | Return nullness specified | %d / %d = %.1f%% |
                | Public parameters | %d |
                | Parameter nullness specified | %d / %d = %.1f%% |
                | Generic type-use coverage | %d / %d = %.1f%% |
                | Ambiguous annotations | %d |
                | Kotlin interop warnings | %d |
                """,
                summary.publicApiElements(),
                summary.specifiedPublicApiElements(),
                summary.specifiedRatio() * 100.0d,
                summary.packagesSeen(),
                summary.nullMarkedPackages(),
                summary.nullMarkedPackageRatio() * 100.0d,
                summary.publicMethods(),
                summary.returnNullnessSpecified(),
                summary.publicMethods(),
                summary.returnNullnessRatio() * 100.0d,
                summary.publicParameters(),
                summary.parameterNullnessSpecified(),
                summary.publicParameters(),
                summary.parameterNullnessRatio() * 100.0d,
                summary.genericTypeUseNullnessSpecified(),
                summary.genericTypeUses(),
                summary.genericTypeUseRatio() * 100.0d,
                summary.ambiguousAnnotations(),
                summary.kotlinInteropWarnings());
    }

    /**
     * Renders the coverage summary as a JSON object.
     *
     * @param summary the coverage summary to render
     * @return the coverage report as a JSON string
     */
    public String json(CoverageSummary summary) {
        return "{"
                + Json.string("publicApiElements") + ":" + Json.number(summary.publicApiElements()) + ","
                + Json.string("specifiedPublicApiElements") + ":"
                + Json.number(summary.specifiedPublicApiElements()) + ","
                + Json.string("publicApiCoverage") + ":"
                + Double.toString(summary.specifiedRatio()) + ","
                + Json.string("packagesSeen") + ":" + Json.number(summary.packagesSeen()) + ","
                + Json.string("nullMarkedPackages") + ":" + Json.number(summary.nullMarkedPackages()) + ","
                + Json.string("nullMarkedPackageCoverage") + ":"
                + Double.toString(summary.nullMarkedPackageRatio()) + ","
                + Json.string("ambiguousAnnotations") + ":"
                + Json.number(summary.ambiguousAnnotations()) + ","
                + Json.string("publicMethods") + ":" + Json.number(summary.publicMethods()) + ","
                + Json.string("returnNullnessSpecified") + ":"
                + Json.number(summary.returnNullnessSpecified()) + ","
                + Json.string("returnNullnessCoverage") + ":"
                + Double.toString(summary.returnNullnessRatio()) + ","
                + Json.string("publicParameters") + ":" + Json.number(summary.publicParameters()) + ","
                + Json.string("parameterNullnessSpecified") + ":"
                + Json.number(summary.parameterNullnessSpecified()) + ","
                + Json.string("parameterNullnessCoverage") + ":"
                + Double.toString(summary.parameterNullnessRatio()) + ","
                + Json.string("genericTypeUses") + ":" + Json.number(summary.genericTypeUses()) + ","
                + Json.string("genericTypeUseNullnessSpecified") + ":"
                + Json.number(summary.genericTypeUseNullnessSpecified()) + ","
                + Json.string("genericTypeUseCoverage") + ":"
                + Double.toString(summary.genericTypeUseRatio()) + ","
                + Json.string("kotlinInteropWarnings") + ":"
                + Json.number(summary.kotlinInteropWarnings())
                + "}";
    }

    /**
     * Writes the coverage summary to the given directory in all supported
     * formats (Markdown, JSON and HTML).
     *
     * @param outputDirectory the directory to write the report files into
     * @param summary the coverage summary to render
     * @throws IOException if the directory or any report file cannot be written
     */
    public void write(Path outputDirectory, CoverageSummary summary) throws IOException {
        write(outputDirectory, summary, List.of("markdown", "json", "html"));
    }

    /**
     * Writes the coverage summary to the given directory in the requested
     * formats.
     *
     * <p>Recognized format names are {@code markdown} (or {@code md}),
     * {@code json} and {@code html}; comparison is case-insensitive and
     * unrecognized names are ignored. A {@code null} list defaults to all
     * formats, while an empty list produces no report files.
     *
     * @param outputDirectory the directory to write the report files into
     * @param summary the coverage summary to render
     * @param formats the report formats to emit
     * @throws IOException if the directory or any report file cannot be written
     */
    public void write(Path outputDirectory, CoverageSummary summary, List<String> formats)
            throws IOException {
        Files.createDirectories(outputDirectory);
        Set<String> requested = normalize(formats);
        if (requested.contains("markdown") || requested.contains("md")) {
            Files.writeString(outputDirectory.resolve("coverage.md"), markdown(summary),
                    StandardCharsets.UTF_8);
        }
        if (requested.contains("json")) {
            Files.writeString(outputDirectory.resolve("coverage.json"), json(summary),
                    StandardCharsets.UTF_8);
        }
        if (requested.contains("html")) {
            Files.writeString(outputDirectory.resolve("coverage.html"), html(summary),
                    StandardCharsets.UTF_8);
        }
    }

    /**
     * Renders the coverage summary as a self-contained HTML document.
     *
     * @param summary the coverage summary to render
     * @return the coverage report as an HTML string
     */
    public String html(CoverageSummary summary) {
        return String.format(Locale.ROOT, """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <title>JSpecify Coverage</title>
                  <style>
                    body { font-family: system-ui, sans-serif; margin: 2rem; color: #172026; }
                    table { border-collapse: collapse; min-width: 42rem; }
                    th, td { border: 1px solid #d5dde5; padding: .5rem .65rem; }
                    th { background: #edf2f7; text-align: left; }
                    td:last-child { text-align: right; font-variant-numeric: tabular-nums; }
                  </style>
                </head>
                <body>
                  <h1>JSpecify Coverage</h1>
                  <table>
                    <tr><th>Metric</th><th>Value</th></tr>
                    <tr><td>Public API coverage</td><td>%.1f%%</td></tr>
                    <tr><td>@NullMarked package coverage</td><td>%.1f%%</td></tr>
                    <tr><td>Return nullness coverage</td><td>%.1f%%</td></tr>
                    <tr><td>Parameter nullness coverage</td><td>%.1f%%</td></tr>
                    <tr><td>Generic type-use coverage</td><td>%.1f%%</td></tr>
                    <tr><td>Ambiguous annotations</td><td>%d</td></tr>
                    <tr><td>Kotlin interop warnings</td><td>%d</td></tr>
                  </table>
                </body>
                </html>
                """,
                summary.specifiedRatio() * 100.0d,
                summary.nullMarkedPackageRatio() * 100.0d,
                summary.returnNullnessRatio() * 100.0d,
                summary.parameterNullnessRatio() * 100.0d,
                summary.genericTypeUseRatio() * 100.0d,
                summary.ambiguousAnnotations(),
                summary.kotlinInteropWarnings());
    }

    private Set<String> normalize(List<String> formats) {
        Set<String> normalized = new LinkedHashSet<>();
        if (formats == null) {
            normalized.addAll(List.of("markdown", "json", "html"));
            return normalized;
        }
        for (String format : formats) {
            if (format == null || format.isBlank()) {
                continue;
            }
            normalized.add(format.trim().toLowerCase(Locale.ROOT));
        }
        return normalized;
    }
}
