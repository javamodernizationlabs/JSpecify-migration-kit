package io.github.javamodernizationlabs.jspecify.report;

import io.github.javamodernizationlabs.jspecify.Issue;
import io.github.javamodernizationlabs.jspecify.MigrationPlan;
import io.github.javamodernizationlabs.jspecify.Severity;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Renders a {@link MigrationPlan} as a SARIF 2.1.0 document.
 *
 * <p>Issues are emitted as SARIF results with physical locations, rule
 * metadata and stable fingerprints so the output can be consumed by code
 * scanning platforms. Severities are mapped to SARIF levels.
 */
public final class SarifReportWriter {

    /**
     * Creates a {@code SarifReportWriter}.
     */
    public SarifReportWriter() {
    }

    /**
     * Renders the migration plan as a SARIF JSON string.
     *
     * @param plan the migration plan to render
     * @return the report as a SARIF 2.1.0 document
     */
    public String render(MigrationPlan plan) {
        Set<String> ruleIds = plan.issues().stream()
                .map(i -> i.ruleId().value())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        StringBuilder rules = new StringBuilder("[");
        boolean first = true;
        for (String ruleId : ruleIds) {
            if (!first) rules.append(',');
            Map<String, String> rule = new LinkedHashMap<>();
            rule.put("id", Json.string(ruleId));
            rule.put("name", Json.string(ruleId));
            Map<String, String> shortDesc = new LinkedHashMap<>();
            shortDesc.put("text", Json.string(ruleId));
            rule.put("shortDescription", Json.object(shortDesc));
            rules.append(Json.object(rule));
            first = false;
        }
        rules.append(']');

        String resultsJson = "[" + plan.issues().stream()
                .map(this::renderResult)
                .collect(Collectors.joining(",")) + "]";

        Map<String, String> driver = new LinkedHashMap<>();
        driver.put("name", Json.string("jspecify-migration-kit"));
        driver.put("informationUri",
                Json.string("https://github.com/java-modernization-labs/jspecify-migration-kit"));
        driver.put("rules", rules.toString());

        Map<String, String> tool = new LinkedHashMap<>();
        tool.put("driver", Json.object(driver));

        Map<String, String> run = new LinkedHashMap<>();
        run.put("tool", Json.object(tool));
        run.put("results", resultsJson);

        Map<String, String> root = new LinkedHashMap<>();
        root.put("$schema", Json.string(
                "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/main/Schemata/sarif-schema-2.1.0.json"));
        root.put("version", Json.string("2.1.0"));
        root.put("runs", "[" + Json.object(run) + "]");
        return Json.object(root);
    }

    private String renderResult(Issue issue) {
        Map<String, String> message = new LinkedHashMap<>();
        message.put("text", Json.string(issue.message()));

        Map<String, String> artifact = new LinkedHashMap<>();
        artifact.put("uri", Json.string(uri(issue.location().path())));

        Map<String, String> region = new LinkedHashMap<>();
        region.put("startLine", Json.number(Math.max(1, issue.location().startLine())));
        region.put("startColumn", Json.number(Math.max(1, issue.location().startColumn())));
        region.put("endLine", Json.number(Math.max(1, issue.location().endLine())));
        region.put("endColumn", Json.number(Math.max(1, issue.location().endColumn())));

        Map<String, String> physicalLocation = new LinkedHashMap<>();
        physicalLocation.put("artifactLocation", Json.object(artifact));
        physicalLocation.put("region", Json.object(region));

        Map<String, String> location = new LinkedHashMap<>();
        location.put("physicalLocation", Json.object(physicalLocation));

        Map<String, String> result = new LinkedHashMap<>();
        result.put("ruleId", Json.string(issue.ruleId().value()));
        result.put("level", Json.string(toSarifLevel(issue.severity())));
        result.put("message", Json.object(message));
        result.put("locations", "[" + Json.object(location) + "]");
        result.put("fingerprints", renderFingerprints(issue));
        return Json.object(result);
    }

    private String renderFingerprints(Issue issue) {
        Map<String, String> fp = new LinkedHashMap<>();
        fp.put("jml/v1", Json.string(issue.fingerprint()));
        return Json.object(fp);
    }

    private String uri(Path path) {
        String normalized = path.toString().replace('\\', '/');
        return normalized.isBlank() ? "unknown" : normalized;
    }

    private String toSarifLevel(Severity severity) {
        return switch (severity) {
            case CRITICAL, HIGH -> "error";
            case MEDIUM -> "warning";
            case LOW, INFO -> "note";
        };
    }

    /**
     * Renders the plan and writes it to the given file, creating parent
     * directories as needed.
     *
     * @param output the file path to write the SARIF report to
     * @param plan the migration plan to render
     * @throws IOException if the parent directories or file cannot be written
     */
    public void write(Path output, MigrationPlan plan) throws IOException {
        ReportFiles.writeString(output, render(plan));
    }
}
