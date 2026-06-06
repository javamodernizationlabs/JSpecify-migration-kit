package io.github.javamodernizationlabs.jspecify.report;

import io.github.javamodernizationlabs.jspecify.Issue;
import io.github.javamodernizationlabs.jspecify.MigrationPlan;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Renders a {@link MigrationPlan} as a Markdown document.
 *
 * <p>The output uses Markdown tables and code fences and escapes free text so
 * that embedded delimiters cannot corrupt the surrounding structure.
 */
public final class MarkdownReportWriter {

    /**
     * Creates a {@code MarkdownReportWriter}.
     */
    public MarkdownReportWriter() {
    }

    /**
     * Renders the migration plan as a Markdown string.
     *
     * @param plan the migration plan to render
     * @return the report as Markdown text
     */
    public String render(MigrationPlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("# JSpecify Migration Plan\n\n");
        sb.append("- Estimated risk: **").append(plan.estimatedRisk()).append("**\n");
        sb.append("- Files scanned: ").append(plan.inventory().filesScanned()).append("\n");
        sb.append("- Annotations found: ").append(plan.inventory().totalAnnotations()).append("\n\n");

        if (!plan.inventory().totalByAnnotation().isEmpty()) {
            sb.append("## Current annotations found\n\n");
            sb.append("| Annotation | Count |\n|---|---:|\n");
            for (var e : plan.inventory().totalByAnnotation().entrySet()) {
                sb.append("| `").append(mdInlineCode(e.getKey())).append("` | ")
                        .append(e.getValue()).append(" |\n");
            }
            sb.append('\n');
        }

        for (var phase : plan.phases()) {
            sb.append("## ").append(phase.order()).append(". ")
                    .append(mdText(phase.title())).append("\n\n");
            sb.append(mdText(phase.description())).append("\n\n");
            for (String cmd : phase.commands()) {
                appendFencedCommand(sb, cmd);
            }
        }

        if (!plan.issues().isEmpty()) {
            sb.append("## Issues\n\n");
            for (Issue issue : plan.issues()) {
                sb.append("- **[").append(issue.severity()).append("]** `")
                        .append(mdInlineCode(String.valueOf(issue.ruleId()))).append("` ")
                        .append(mdText(issue.message())).append(" — `")
                        .append(mdInlineCode(String.valueOf(issue.location().path()))).append(':')
                        .append(issue.location().startLine()).append("`\n");
            }
        }

        return sb.toString();
    }

    /**
     * Renders the plan and writes it to the given file, creating parent
     * directories as needed.
     *
     * @param output the file path to write the Markdown report to
     * @param plan the migration plan to render
     * @throws IOException if the parent directories or file cannot be written
     */
    public void write(Path output, MigrationPlan plan) throws IOException {
        ReportFiles.writeString(output, render(plan));
    }

    /**
     * Appends a single command inside a fenced {@code bash} code block.
     *
     * <p>Embedded newlines are collapsed to spaces so the command stays on one
     * line, and the surrounding fence is widened to one more backtick than the
     * longest backtick run in the command so the command can never close the
     * fence prematurely.
     */
    private static void appendFencedCommand(StringBuilder sb, String cmd) {
        String oneLine = cmd == null ? "" : cmd.replace("\r", " ").replace("\n", " ");
        String fence = "`".repeat(Math.max(3, longestBacktickRun(oneLine) + 1));
        sb.append(fence).append("bash\n").append(oneLine).append('\n')
                .append(fence).append("\n\n");
    }

    /**
     * Returns the length of the longest consecutive run of backticks in the
     * given text, or {@code 0} if it contains none.
     */
    private static int longestBacktickRun(String s) {
        int longest = 0;
        int current = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '`') {
                current++;
                if (current > longest) {
                    longest = current;
                }
            } else {
                current = 0;
            }
        }
        return longest;
    }

    /**
     * Escapes free text so embedded newlines and table delimiters cannot break
     * the surrounding Markdown row or list item.
     */
    private static String mdText(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\r", " ").replace("\n", " ").replace("|", "\\|");
    }

    /**
     * Escapes text rendered inside an inline code span. Backticks would close
     * the span and pipes still delimit table cells even within code, so both are
     * neutralized along with newlines.
     */
    private static String mdInlineCode(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("`", "'").replace("\r", " ").replace("\n", " ")
                .replace("|", "\\|");
    }
}
