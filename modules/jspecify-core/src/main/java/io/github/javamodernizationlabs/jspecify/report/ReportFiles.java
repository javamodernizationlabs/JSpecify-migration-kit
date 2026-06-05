package io.github.javamodernizationlabs.jspecify.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class ReportFiles {

    private ReportFiles() {}

    static void writeString(Path output, String content) throws IOException {
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(output, content, StandardCharsets.UTF_8);
    }
}
