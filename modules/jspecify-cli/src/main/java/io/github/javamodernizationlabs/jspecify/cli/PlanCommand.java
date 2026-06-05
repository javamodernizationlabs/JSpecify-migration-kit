package io.github.javamodernizationlabs.jspecify.cli;

import io.github.javamodernizationlabs.jspecify.AnnotationInventory;
import io.github.javamodernizationlabs.jspecify.MigrationPlan;
import io.github.javamodernizationlabs.jspecify.MigrationPlanner;
import io.github.javamodernizationlabs.jspecify.ProjectModel;
import io.github.javamodernizationlabs.jspecify.Severity;
import io.github.javamodernizationlabs.jspecify.baseline.BaselineStore;
import io.github.javamodernizationlabs.jspecify.config.JspecifyConfig;
import io.github.javamodernizationlabs.jspecify.config.JspecifyConfigLoader;
import io.github.javamodernizationlabs.jspecify.report.ConsoleReportWriter;
import io.github.javamodernizationlabs.jspecify.report.HtmlReportWriter;
import io.github.javamodernizationlabs.jspecify.report.JsonReportWriter;
import io.github.javamodernizationlabs.jspecify.report.JunitXmlReportWriter;
import io.github.javamodernizationlabs.jspecify.report.MarkdownReportWriter;
import io.github.javamodernizationlabs.jspecify.report.SarifReportWriter;
import io.github.javamodernizationlabs.jspecify.scan.AnnotationScanner;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * The {@code plan} command, which inventories legacy nullness annotations and emits a
 * JSpecify migration plan.
 *
 * <p>It scans the project rooted at {@code --project}, builds a migration plan from the
 * discovered annotations, and writes the requested report formats (one or more of console,
 * JSON, Markdown, SARIF, HTML and JUnit XML) selected with {@code --format} into the
 * directory chosen by {@code --output-dir}. The command also supports CI gating: it can
 * record current issue fingerprints with {@code --baseline-write} and compare against an
 * existing baseline given by {@code --baseline}, failing when new issues at or above the
 * {@code --fail-on} severity are found unless {@code --allow-new-issues} is set.</p>
 */
@Command(
        name = "plan",
        description = "Inventory legacy nullness annotations and emit a migration plan.",
        mixinStandardHelpOptions = true
)
public class PlanCommand implements Callable<Integer> {

    /**
     * Creates a {@code PlanCommand}.
     */
    public PlanCommand() {
    }

    @Option(names = {"--project"}, description = "Project root (default: current directory)",
            defaultValue = ".")
    Path project;

    @Option(names = {"--format"}, description = "Output formats: console,json,markdown,sarif,html,junit",
            split = ",")
    List<String> formats;

    @Option(names = {"--output-dir"},
            description = "Directory for non-console reports")
    Path outputDir;

    @Option(names = {"--baseline"}, description = "Existing baseline file for CI gating")
    Path baseline;

    @Option(names = {"--baseline-write"}, description = "Write current issue fingerprints")
    Path baselineWrite;

    @Option(names = {"--fail-on"}, description = "Fail on new issues at or above severity",
            defaultValue = "high")
    String failOn;

    @Option(names = {"--allow-new-issues"}, description = "Do not fail on new issues")
    boolean allowNewIssues;

    /**
     * Scans the project, builds the migration plan, writes the requested reports and applies
     * baseline gating.
     *
     * <p>Returns {@code 2} when an unknown report format is requested, {@code 1} when baseline
     * gating finds new issues at or above the configured severity, and {@code 0} otherwise.</p>
     *
     * @return the process exit code: {@code 0} on success, {@code 1} on failing new issues,
     *         {@code 2} on an unknown report format
     * @throws Exception if scanning, planning, baseline handling or report writing fails
     */
    @Override
    public Integer call() throws Exception {
        Path projectRoot = project.toAbsolutePath().normalize();
        JspecifyConfig config = JspecifyConfigLoader.load(projectRoot);
        Severity threshold;
        try {
            threshold = Severity.parse(failOn);
        } catch (IllegalArgumentException e) {
            System.err.println("Unknown severity for --fail-on: " + failOn
                    + ". Expected info, low, medium, high, or critical.");
            return 2;
        }
        ProjectModel model = ProjectModel.of(projectRoot, config);
        AnnotationInventory inventory = AnnotationScanner.forConfig(config).scan(model);
        MigrationPlan plan = new MigrationPlanner().plan(inventory);
        List<String> effectiveFormats = formats == null || formats.isEmpty()
                ? config.reportFormats()
                : formats;
        Path effectiveOutputDir = outputDir == null
                ? config.resolveReportsOutputDirectory(projectRoot)
                : outputDir.toAbsolutePath().normalize();
        BaselineStore baselineStore = new BaselineStore();
        if (baselineWrite != null) {
            baselineStore.write(baselineWrite.toAbsolutePath().normalize(), plan.issues());
        }

        boolean unknownFormat = false;
        for (String format : effectiveFormats) {
            switch (format.trim().toLowerCase()) {
                case "console" -> new ConsoleReportWriter().write(System.out, plan);
                case "json" -> new JsonReportWriter()
                        .write(effectiveOutputDir.resolve("plan.json"), plan);
                case "markdown" -> new MarkdownReportWriter()
                        .write(effectiveOutputDir.resolve("plan.md"), plan);
                case "sarif" -> new SarifReportWriter()
                        .write(effectiveOutputDir.resolve("plan.sarif"), plan);
                case "html" -> new HtmlReportWriter()
                        .write(effectiveOutputDir.resolve("index.html"), plan);
                case "junit", "junit-xml" -> new JunitXmlReportWriter()
                        .write(effectiveOutputDir.resolve("TEST-jspecify-migration.xml"), plan);
                default -> {
                    System.err.println("Unknown format: " + format);
                    unknownFormat = true;
                }
            }
        }
        // Fail fast on a typo'd format so CI does not pass with no report written.
        if (unknownFormat) {
            return 2;
        }
        if (allowNewIssues || baseline == null) {
            return 0;
        }
        boolean hasFailingNewIssues = baselineStore
                .newIssues(plan.issues(), baseline.toAbsolutePath().normalize())
                .stream()
                .anyMatch(issue -> issue.severity().atLeast(threshold));
        return hasFailingNewIssues ? 1 : 0;
    }
}
