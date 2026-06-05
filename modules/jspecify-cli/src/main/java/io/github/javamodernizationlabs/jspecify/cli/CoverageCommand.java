package io.github.javamodernizationlabs.jspecify.cli;

import io.github.javamodernizationlabs.jspecify.ProjectModel;
import io.github.javamodernizationlabs.jspecify.config.JspecifyConfig;
import io.github.javamodernizationlabs.jspecify.config.JspecifyConfigLoader;
import io.github.javamodernizationlabs.jspecify.coverage.CoverageAnalyzer;
import io.github.javamodernizationlabs.jspecify.report.CoverageReportWriter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * The {@code coverage} command, which reports how much of a project's public API carries an
 * explicit JSpecify nullness contract.
 *
 * <p>It analyses the project rooted at {@code --project} for the requested {@code --scope}
 * (currently only {@code public-api} is supported) and writes coverage reports in the formats
 * chosen by {@code --format} into the directory selected by {@code --output-dir}. The overall
 * specified ratio is also printed to standard output.</p>
 */
@Command(
        name = "coverage",
        description = "Generate a public API nullness coverage report.",
        mixinStandardHelpOptions = true
)
public class CoverageCommand implements Callable<Integer> {

    /**
     * Creates a {@code CoverageCommand}.
     */
    public CoverageCommand() {
    }

    @Option(names = {"--project"}, defaultValue = ".") Path project;
    @Option(names = {"--scope"}, defaultValue = "public-api") String scope = "public-api";
    @Option(names = {"--format"}, split = ",") List<String> formats;
    @Option(names = {"--output-dir"}) Path output;

    /**
     * Analyses public API nullness coverage and writes the requested coverage reports.
     *
     * <p>Returns {@code 2} when an unsupported {@code --scope} is requested, and {@code 0}
     * once the analysis has completed and the reports have been written.</p>
     *
     * @return the process exit code: {@code 0} on success, {@code 2} on an unsupported scope
     * @throws Exception if loading the project, analysing coverage or writing the reports fails
     */
    @Override
    public Integer call() throws Exception {
        if (!scope.equalsIgnoreCase("public-api")) {
            System.err.println("Unsupported coverage scope: " + scope
                    + ". The JSpecify MVP currently supports public-api.");
            return 2;
        }
        Path projectRoot = project.toAbsolutePath().normalize();
        JspecifyConfig config = JspecifyConfigLoader.load(projectRoot);
        var summary = new CoverageAnalyzer().analyze(ProjectModel.of(projectRoot, config));
        Path out = output == null
                ? config.resolveReportsOutputDirectory(projectRoot)
                : output.toAbsolutePath().normalize();
        List<String> requestedFormats = normalizeFormats(formats == null || formats.isEmpty()
                ? config.reportFormats()
                : formats);
        Set<String> unknownFormats = unknownFormats(requestedFormats);
        if (!unknownFormats.isEmpty()) {
            System.err.println("Unknown coverage format(s): " + String.join(",", unknownFormats));
            return 2;
        }
        List<String> fileFormats = requestedFormats.stream()
                .filter(format -> !format.equals("console"))
                .toList();
        if (!fileFormats.isEmpty()) {
            new CoverageReportWriter().write(out, summary, fileFormats);
            System.out.println("Coverage reports written to " + out);
        }
        System.out.printf(Locale.ROOT, "JSpecify coverage: %.1f%%%n",
                summary.specifiedRatio() * 100.0d);
        return 0;
    }

    private List<String> normalizeFormats(List<String> rawFormats) {
        return rawFormats.stream()
                .filter(format -> format != null && !format.isBlank())
                .map(format -> format.trim().toLowerCase(Locale.ROOT))
                .toList();
    }

    private Set<String> unknownFormats(List<String> requestedFormats) {
        Set<String> unknown = new LinkedHashSet<>();
        for (String format : requestedFormats) {
            if (!Set.of("console", "markdown", "md", "json", "html").contains(format)) {
                unknown.add(format);
            }
        }
        return unknown;
    }
}
