package io.github.javamodernizationlabs.jspecify.baseline;

import io.github.javamodernizationlabs.jspecify.Issue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads and writes the baseline of accepted issue fingerprints.
 *
 * <p>A baseline records the fingerprints of known issues so that subsequent runs
 * can distinguish newly introduced issues from pre-existing ones. The on-disk
 * format is a small JSON document containing a list of fingerprints.
 */
public final class BaselineStore {

    private static final Pattern FINGERPRINT =
            Pattern.compile("\"fingerprint\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern BASELINE_SHAPE =
            Pattern.compile("\\A\\s*\\{\\s*\"fingerprints\"\\s*:\\s*\\[.*]\\s*}\\s*\\z",
                    Pattern.DOTALL);

    /**
     * Creates a {@code BaselineStore}.
     */
    public BaselineStore() {
    }

    /**
     * Reads the set of fingerprints recorded in a baseline file.
     *
     * @param baselineFile the baseline file to read; a {@code null} or
     *     non-existent file yields an empty set
     * @return the fingerprints recorded in the baseline, in encounter order
     * @throws IOException if the file exists but cannot be read
     */
    public Set<String> read(Path baselineFile) throws IOException {
        if (baselineFile == null || !Files.isRegularFile(baselineFile)) {
            return Set.of();
        }
        String content = Files.readString(baselineFile, StandardCharsets.UTF_8);
        if (!content.isBlank() && !BASELINE_SHAPE.matcher(content).matches()) {
            throw new IOException("Invalid JSpecify baseline format in "
                    + baselineFile);
        }
        Set<String> fingerprints = new LinkedHashSet<>();
        var matcher = FINGERPRINT.matcher(content);
        while (matcher.find()) {
            fingerprints.add(matcher.group(1));
        }
        return fingerprints;
    }

    /**
     * Filters the given issues down to those not present in the baseline.
     *
     * @param issues the issues to filter
     * @param baselineFile the baseline file whose fingerprints are excluded
     * @return the issues whose fingerprints are not in the baseline
     * @throws IOException if the baseline file exists but cannot be read
     */
    public List<Issue> newIssues(List<Issue> issues, Path baselineFile) throws IOException {
        Set<String> baseline = read(baselineFile);
        return issues.stream()
                .filter(issue -> !baseline.contains(issue.fingerprint()))
                .toList();
    }

    /**
     * Writes the fingerprints of the given issues to a baseline file, creating
     * parent directories as needed.
     *
     * @param baselineFile the file to write the baseline to
     * @param issues the issues whose fingerprints make up the baseline
     * @throws IOException if the parent directories or file cannot be written
     */
    public void write(Path baselineFile, List<Issue> issues) throws IOException {
        if (baselineFile.getParent() != null) {
            Files.createDirectories(baselineFile.getParent());
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{\"fingerprints\":[");
        for (int i = 0; i < issues.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"fingerprint\":\"").append(issues.get(i).fingerprint()).append("\"}");
        }
        sb.append("]}\n");
        Path directory = baselineFile.getParent() == null ? Path.of(".") : baselineFile.getParent();
        Path temp = Files.createTempFile(directory, baselineFile.getFileName().toString(), ".tmp");
        Files.writeString(temp, sb.toString(), StandardCharsets.UTF_8);
        try {
            Files.move(temp, baselineFile, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, baselineFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
