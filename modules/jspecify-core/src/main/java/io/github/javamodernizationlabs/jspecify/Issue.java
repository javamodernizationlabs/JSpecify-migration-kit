package io.github.javamodernizationlabs.jspecify;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * A single finding produced by an analysis tool during a migration.
 *
 * <p>Instances are typically created through the {@link Builder}, which derives a
 * stable {@code fingerprint} from the issue's tool, rule, location, and message.
 *
 * @param tool the name of the tool that reported the issue
 * @param ruleId the identifier of the rule that fired
 * @param severity the severity ranking of the issue
 * @param title a short human-readable title
 * @param message a detailed human-readable message
 * @param location the source location the issue refers to
 * @param evidence supporting evidence strings; defensively copied
 * @param recommendation the suggested remediation
 * @param fingerprint a stable identifier used to deduplicate findings across runs
 */
public record Issue(
        String tool,
        RuleId ruleId,
        Severity severity,
        String title,
        String message,
        Location location,
        List<String> evidence,
        Recommendation recommendation,
        String fingerprint
) {
    /**
     * Canonical constructor that validates required fields and defensively copies
     * the evidence list.
     *
     * @throws NullPointerException if any required field is {@code null}
     */
    public Issue {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(location, "location");
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        Objects.requireNonNull(recommendation, "recommendation");
        Objects.requireNonNull(fingerprint, "fingerprint");
    }

    /**
     * Returns a new builder for assembling an {@link Issue}.
     *
     * @return a fresh builder with default field values
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Mutable builder for {@link Issue} instances.
     *
     * <p>Setters return {@code this} to allow chaining; {@link #build()} computes the
     * fingerprint and produces an immutable {@code Issue}.
     */
    public static final class Builder {

        /**
         * Creates an empty {@code Builder}; prefer {@link Issue#builder()}.
         */
        public Builder() {
        }

        private String tool = "jspecify-migration-kit";
        private RuleId ruleId;
        private Severity severity = Severity.INFO;
        private String title = "";
        private String message = "";
        private Location location = Location.none();
        private List<String> evidence = List.of();
        private Recommendation recommendation = Recommendation.of("");

        /**
         * Sets the reporting tool name.
         *
         * @param tool the tool name
         * @return this builder
         */
        public Builder tool(String tool) { this.tool = tool; return this; }

        /**
         * Sets the rule identifier.
         *
         * @param ruleId the rule identifier
         * @return this builder
         */
        public Builder ruleId(RuleId ruleId) { this.ruleId = ruleId; return this; }

        /**
         * Sets the rule identifier from its string form.
         *
         * @param ruleId the rule identifier string
         * @return this builder
         */
        public Builder ruleId(String ruleId) { this.ruleId = RuleId.of(ruleId); return this; }

        /**
         * Sets the severity ranking.
         *
         * @param severity the severity
         * @return this builder
         */
        public Builder severity(Severity severity) { this.severity = severity; return this; }

        /**
         * Sets the short title.
         *
         * @param title the title
         * @return this builder
         */
        public Builder title(String title) { this.title = title; return this; }

        /**
         * Sets the detailed message.
         *
         * @param message the message
         * @return this builder
         */
        public Builder message(String message) { this.message = message; return this; }

        /**
         * Sets the source location.
         *
         * @param location the location
         * @return this builder
         */
        public Builder location(Location location) { this.location = location; return this; }

        /**
         * Sets the supporting evidence strings.
         *
         * @param evidence the evidence list
         * @return this builder
         */
        public Builder evidence(List<String> evidence) { this.evidence = evidence; return this; }

        /**
         * Sets the suggested remediation.
         *
         * @param recommendation the recommendation
         * @return this builder
         */
        public Builder recommendation(Recommendation recommendation) {
            this.recommendation = recommendation;
            return this;
        }

        /**
         * Builds an immutable {@link Issue}, deriving its fingerprint from the
         * configured tool, rule, location, and message.
         *
         * @return the assembled issue
         */
        public Issue build() {
            String fp = fingerprint(tool, ruleId, location, message);
            return new Issue(tool, ruleId, severity, title, message,
                    location, evidence, recommendation, fp);
        }
    }

    private static String fingerprint(String tool, RuleId ruleId,
                                      Location location, String message) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String normalizedPath = location.path().toString().replace('\\', '/');
            String seed = tool + "|" + ruleId + "|" + normalizedPath + "|"
                    + location.startLine() + "|" + location.startColumn() + "|" + message;
            md.update(seed.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
