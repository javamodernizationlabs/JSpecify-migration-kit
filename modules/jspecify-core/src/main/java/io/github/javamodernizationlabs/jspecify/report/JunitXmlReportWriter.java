package io.github.javamodernizationlabs.jspecify.report;

import io.github.javamodernizationlabs.jspecify.Issue;
import io.github.javamodernizationlabs.jspecify.MigrationPlan;
import io.github.javamodernizationlabs.jspecify.Severity;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Renders a {@link MigrationPlan} as a JUnit-style XML report.
 *
 * <p>Each issue becomes a {@code testcase}; issues at or above
 * {@link Severity#HIGH} are emitted as failures so CI systems that consume
 * JUnit XML can surface them. Text is XML-escaped before output.
 */
public final class JunitXmlReportWriter {

    /**
     * Creates a {@code JunitXmlReportWriter}.
     */
    public JunitXmlReportWriter() {
    }

    /**
     * Renders the migration plan as a JUnit XML string.
     *
     * @param plan the migration plan to render
     * @return the report as a JUnit XML document
     */
    public String render(MigrationPlan plan) {
        int failures = (int) plan.issues().stream()
                .filter(i -> i.severity().atLeast(Severity.HIGH))
                .count();
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<testsuite name=\"jspecify-migration\" tests=\"")
                .append(plan.issues().size())
                .append("\" failures=\"").append(failures)
                .append("\" errors=\"0\" skipped=\"0\">\n");
        for (Issue issue : plan.issues()) {
            sb.append("  <testcase classname=\"")
                    .append(escape(issue.ruleId().value()))
                    .append("\" name=\"")
                    .append(escape(issue.location().path() + ":" + issue.location().startLine()))
                    .append("\">");
            if (issue.severity().atLeast(Severity.HIGH)) {
                sb.append("<failure message=\"").append(escape(issue.message())).append("\">")
                        .append(escape(issue.fingerprint()))
                        .append("</failure>");
            } else {
                sb.append("<system-out>").append(escape(issue.message())).append("</system-out>");
            }
            sb.append("</testcase>\n");
        }
        sb.append("</testsuite>\n");
        return sb.toString();
    }

    /**
     * Renders the plan and writes it to the given file, creating parent
     * directories as needed.
     *
     * @param output the file path to write the JUnit XML report to
     * @param plan the migration plan to render
     * @throws IOException if the parent directories or file cannot be written
     */
    public void write(Path output, MigrationPlan plan) throws IOException {
        ReportFiles.writeString(output, render(plan));
    }

    private String escape(String raw) {
        return sanitizeXml(raw)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String sanitizeXml(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\t' || c == '\n' || c == '\r'
                    || (c >= 0x20 && c <= 0xD7FF)
                    || (c >= 0xE000 && c <= 0xFFFD)) {
                out.append(c);
            }
        }
        return out.toString();
    }
}
