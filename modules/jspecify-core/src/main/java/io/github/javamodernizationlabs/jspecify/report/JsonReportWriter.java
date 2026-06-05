package io.github.javamodernizationlabs.jspecify.report;

import io.github.javamodernizationlabs.jspecify.Issue;
import io.github.javamodernizationlabs.jspecify.Location;
import io.github.javamodernizationlabs.jspecify.MigrationPlan;
import io.github.javamodernizationlabs.jspecify.Recommendation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Serializes a {@link MigrationPlan} to a JSON document.
 *
 * <p>The output captures the estimated risk, scan counts, recommended phases
 * and the full issue list, and can be written directly to a file.
 */
public final class JsonReportWriter {

    /**
     * Creates a {@code JsonReportWriter}.
     */
    public JsonReportWriter() {
    }

    /**
     * Renders the migration plan as a JSON string.
     *
     * @param plan the migration plan to serialize
     * @return the plan encoded as a JSON object
     */
    public String render(MigrationPlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append(Json.string("tool")).append(':')
                .append(Json.string("jspecify-migration-kit")).append(',');
        sb.append(Json.string("estimatedRisk")).append(':')
                .append(Json.string(plan.estimatedRisk().name())).append(',');
        sb.append(Json.string("filesScanned")).append(':')
                .append(Json.number(plan.inventory().filesScanned())).append(',');
        sb.append(Json.string("annotationsFound")).append(':')
                .append(renderAnnotationCounts(plan.inventory().totalByAnnotation())).append(',');
        sb.append(Json.string("phases")).append(':')
                .append(renderPhases(plan.phases())).append(',');
        sb.append(Json.string("issues")).append(':')
                .append(renderIssues(plan.issues()));
        sb.append('}');
        return sb.toString();
    }

    /**
     * Renders the plan and writes it to the given file, creating parent
     * directories as needed.
     *
     * @param output the file path to write the JSON report to
     * @param plan the migration plan to serialize
     * @throws IOException if the parent directories or file cannot be written
     */
    public void write(Path output, MigrationPlan plan) throws IOException {
        ReportFiles.writeString(output, render(plan));
    }

    private String renderAnnotationCounts(Map<String, Integer> counts) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (var e : counts.entrySet()) {
            if (!first) sb.append(',');
            Map<String, String> obj = new LinkedHashMap<>();
            obj.put("annotation", Json.string(e.getKey()));
            obj.put("count", Json.number(e.getValue()));
            sb.append(Json.object(obj));
            first = false;
        }
        sb.append(']');
        return sb.toString();
    }

    private String renderPhases(List<MigrationPlan.Phase> phases) {
        return "[" + phases.stream().map(p -> {
            Map<String, String> obj = new LinkedHashMap<>();
            obj.put("order", Json.number(p.order()));
            obj.put("title", Json.string(p.title()));
            obj.put("description", Json.string(p.description()));
            obj.put("commands", Json.arrayOfStrings(p.commands()));
            return Json.object(obj);
        }).collect(Collectors.joining(",")) + "]";
    }

    private String renderIssues(List<Issue> issues) {
        return "[" + issues.stream().map(this::renderIssue)
                .collect(Collectors.joining(",")) + "]";
    }

    String renderIssue(Issue issue) {
        Map<String, String> obj = new LinkedHashMap<>();
        obj.put("tool", Json.string(issue.tool()));
        obj.put("ruleId", Json.string(issue.ruleId().value()));
        obj.put("severity", Json.string(issue.severity().name()));
        obj.put("title", Json.string(issue.title()));
        obj.put("message", Json.string(issue.message()));
        obj.put("location", renderLocation(issue.location()));
        obj.put("evidence", Json.arrayOfStrings(issue.evidence()));
        obj.put("recommendation", renderRecommendation(issue.recommendation()));
        obj.put("fingerprint", Json.string(issue.fingerprint()));
        return Json.object(obj);
    }

    private String renderLocation(Location loc) {
        Map<String, String> obj = new LinkedHashMap<>();
        obj.put("path", Json.string(loc.path().toString().replace('\\', '/')));
        obj.put("startLine", Json.number(loc.startLine()));
        obj.put("startColumn", Json.number(loc.startColumn()));
        obj.put("endLine", Json.number(loc.endLine()));
        obj.put("endColumn", Json.number(loc.endColumn()));
        return Json.object(obj);
    }

    private String renderRecommendation(Recommendation r) {
        Map<String, String> obj = new LinkedHashMap<>();
        obj.put("summary", Json.string(r.summary()));
        obj.put("links", Json.arrayOfStrings(r.links()));
        return Json.object(obj);
    }
}
