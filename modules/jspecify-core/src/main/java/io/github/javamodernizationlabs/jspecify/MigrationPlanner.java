package io.github.javamodernizationlabs.jspecify;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a staged {@link MigrationPlan} from an {@link AnnotationInventory}.
 *
 * <p>The planner emits a fixed sequence of phases (add dependency, convert
 * annotations, mark packages, enable NullAway) and estimates overall risk from the
 * volume and variety of legacy annotations found.
 */
public final class MigrationPlanner {

    /**
     * Creates a {@code MigrationPlanner}.
     */
    public MigrationPlanner() {
    }

    /**
     * Produces a migration plan for the given annotation inventory.
     *
     * @param inventory the inventory of legacy annotations found in the project
     * @return a plan describing the phases, estimated risk, and surfaced issues
     */
    public MigrationPlan plan(AnnotationInventory inventory) {
        List<MigrationPlan.Phase> phases = List.of(
                new MigrationPlan.Phase(1,
                        "Add JSpecify dependency",
                        "Add org.jspecify:jspecify (compileOnly / provided).",
                        List.of("jml jspecify rewrite --recipe add-dependency --apply")),
                new MigrationPlan.Phase(2,
                        "Convert existing Nullable/NonNull annotations",
                        "Rewrite known legacy nullness annotations to JSpecify equivalents.",
                        List.of("jml jspecify rewrite --recipe convert-known-annotations --dry-run",
                                "jml jspecify rewrite --recipe convert-known-annotations --apply")),
                new MigrationPlan.Phase(3,
                        "Add @NullMarked to internal packages",
                        "Mark low-risk packages first; review API boundaries separately.",
                        List.of("jml jspecify rewrite --recipe add-null-marked --scope internal --apply")),
                new MigrationPlan.Phase(4,
                        "Add @NullMarked to public API packages after review",
                        "Generate coverage report and review remaining unspecified contracts.",
                        List.of("jml jspecify coverage --scope public-api")),
                new MigrationPlan.Phase(5,
                        "Enable NullAway in warn mode",
                        "Configure NullAway/Error Prone to emit warnings, not errors.",
                        List.of("jml jspecify nullaway-config --mode warn --apply")),
                new MigrationPlan.Phase(6,
                        "Enable NullAway as CI gate",
                        "Promote NullAway findings to errors and add baseline for legacy issues.",
                        List.of("jml jspecify nullaway-config --mode error --apply"))
        );

        MigrationPlan.Risk risk = estimateRisk(inventory);
        List<Issue> issues = buildInventoryIssues(inventory);
        return new MigrationPlan(inventory, phases, risk, issues);
    }

    private MigrationPlan.Risk estimateRisk(AnnotationInventory inventory) {
        int total = inventory.totalByAnnotation().entrySet().stream()
                .filter(entry -> !entry.getKey().startsWith("org.jspecify."))
                .mapToInt(entry -> entry.getValue())
                .sum();
        int distinct = (int) inventory.totalByAnnotation().keySet().stream()
                .filter(annotation -> !annotation.startsWith("org.jspecify."))
                .count();
        if (total == 0) {
            return MigrationPlan.Risk.LOW;
        }
        if (total > 200 || distinct >= 4) {
            return MigrationPlan.Risk.HIGH;
        }
        if (total > 40 || distinct >= 2) {
            return MigrationPlan.Risk.MEDIUM;
        }
        return MigrationPlan.Risk.LOW;
    }

    private List<Issue> buildInventoryIssues(AnnotationInventory inventory) {
        List<Issue> issues = new ArrayList<>();
        for (var entry : inventory.locationsByAnnotation().entrySet()) {
            String annotation = entry.getKey();
            if (annotation.startsWith("org.jspecify.")) {
                continue;
            }
            for (Location loc : entry.getValue()) {
                issues.add(Issue.builder()
                        .ruleId("jspecify.old-nullness-annotation")
                        .severity(Severity.MEDIUM)
                        .title("Legacy nullness annotation in use")
                        .message("Annotation " + annotation
                                + " should be migrated to a JSpecify equivalent.")
                        .location(loc)
                        .evidence(List.of(annotation))
                        .recommendation(Recommendation.of(
                                "Run `jml jspecify rewrite --recipe convert-known-annotations`.",
                                List.of("https://jspecify.dev/docs/using/")))
                        .build());
            }
        }
        return issues;
    }
}
