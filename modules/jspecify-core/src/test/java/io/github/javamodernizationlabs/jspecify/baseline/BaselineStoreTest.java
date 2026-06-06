package io.github.javamodernizationlabs.jspecify.baseline;

import io.github.javamodernizationlabs.jspecify.Issue;
import io.github.javamodernizationlabs.jspecify.Location;
import io.github.javamodernizationlabs.jspecify.Recommendation;
import io.github.javamodernizationlabs.jspecify.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BaselineStoreTest {

    @Test
    void writesAndFiltersFingerprints(@TempDir Path tmp) throws Exception {
        Issue issue = Issue.builder()
                .ruleId("jspecify.old-nullness-annotation")
                .severity(Severity.MEDIUM)
                .title("Old annotation")
                .message("Old annotation")
                .location(Location.of(Path.of("Api.java"), 1))
                .recommendation(Recommendation.of("Convert it."))
                .build();
        Path baseline = tmp.resolve("baseline.json");
        BaselineStore store = new BaselineStore();

        store.write(baseline, List.of(issue));

        assertEquals(1, store.read(baseline).size());
        assertEquals(0, store.newIssues(List.of(issue), baseline).size());
    }

    @Test
    void rejectsInvalidBaselineShape(@TempDir Path tmp) throws Exception {
        Path baseline = tmp.resolve("baseline.json");
        java.nio.file.Files.writeString(baseline, "{\"fingerprint\":\"sha256:old\"}");

        assertThrows(java.io.IOException.class, () -> new BaselineStore().read(baseline));
    }

    @Test
    void coLocatedIssuesGetDistinctFingerprints(@TempDir Path tmp) throws Exception {
        Issue col1 = issueAt(new Location(Path.of("Api.java"), 7, 3, 7, 3));
        Issue col2 = issueAt(new Location(Path.of("Api.java"), 7, 12, 7, 12));
        Path baseline = tmp.resolve("baseline.json");
        BaselineStore store = new BaselineStore();

        store.write(baseline, List.of(col1));

        // Same line, different column must not collapse: col2 stays "new".
        assertEquals(List.of(col2), store.newIssues(List.of(col1, col2), baseline));
    }

    @Test
    void pathSeparatorDoesNotAffectFingerprint(@TempDir Path tmp) throws Exception {
        Issue forwardSlash = issueAt(Location.of(Path.of("src/Api.java"), 5));
        Issue backSlash = issueAt(Location.of(Path.of("src\\Api.java"), 5));
        Path baseline = tmp.resolve("baseline.json");
        BaselineStore store = new BaselineStore();

        store.write(baseline, List.of(forwardSlash));

        // Both paths normalize to forward slashes, so the baseline filters out both.
        assertEquals(List.of(), store.newIssues(List.of(backSlash), baseline));
    }

    private static Issue issueAt(Location location) {
        return Issue.builder()
                .ruleId("jspecify.old-nullness-annotation")
                .severity(Severity.MEDIUM)
                .title("Old annotation")
                .message("Old annotation")
                .location(location)
                .recommendation(Recommendation.of("Convert it."))
                .build();
    }
}
