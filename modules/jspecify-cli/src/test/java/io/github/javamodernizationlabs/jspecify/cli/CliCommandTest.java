package io.github.javamodernizationlabs.jspecify.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CliCommandTest {

    @Test
    void coverageRejectsUnknownFormats(@TempDir Path tmp) throws Exception {
        CoverageCommand command = new CoverageCommand();
        command.project = tmp;
        command.formats = List.of("bogus");

        assertEquals(2, command.call());
    }

    @Test
    void coverageConsoleFormatDoesNotWriteAllReports(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("jspecify.yml"),
                """
                reports:
                  formats: [console]
                """);
        CoverageCommand command = new CoverageCommand();
        command.project = tmp;

        assertEquals(0, command.call());
        Path reports = tmp.resolve("build/reports/jml/jspecify");
        assertFalse(Files.exists(reports.resolve("coverage.md")));
        assertFalse(Files.exists(reports.resolve("coverage.json")));
        assertFalse(Files.exists(reports.resolve("coverage.html")));
    }

    @Test
    void nullAwayConfigRejectsUnknownMode(@TempDir Path tmp) throws Exception {
        NullAwayConfigCommand command = new NullAwayConfigCommand();
        command.project = tmp;
        command.mode = "verbose";

        assertEquals(2, command.call());
    }

    @Test
    void nullAwayConfigRequiresAnnotatedPackages(@TempDir Path tmp) throws Exception {
        NullAwayConfigCommand command = new NullAwayConfigCommand();
        command.project = tmp;
        command.mode = "warn";

        assertEquals(2, command.call());
    }

    @Test
    void planRejectsInvalidFailOnSeverity(@TempDir Path tmp) throws Exception {
        PlanCommand command = new PlanCommand();
        command.project = tmp;
        command.failOn = "urgent";

        assertEquals(2, command.call());
    }
}
