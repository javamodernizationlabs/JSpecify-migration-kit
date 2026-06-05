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
}
