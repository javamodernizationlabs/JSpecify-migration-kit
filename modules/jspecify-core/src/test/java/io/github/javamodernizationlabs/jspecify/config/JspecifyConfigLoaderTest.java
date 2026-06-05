package io.github.javamodernizationlabs.jspecify.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JspecifyConfigLoaderTest {

    @Test
    void productConfigOverridesSharedConfig(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("jml.yml"),
                """
                reports:
                  formats: [console, json]
                  outputDirectory: build/shared
                """);
        Files.writeString(tmp.resolve("jspecify.yml"),
                """
                jspecify:
                  version: "1.0.0"
                reports:
                  formats: [console, html, markdown, sarif, json, junit]
                  outputDirectory: build/reports/jml/jspecify
                annotations:
                  mappings:
                    com.acme.Nullable: org.jspecify.annotations.Nullable
                migration:
                  generatedCode:
                    patterns:
                      - "**/generated/**"
                      - "**/custom-generated/**"
                sourceRoots:
                  - src/main/java
                packagePolicy:
                  markPackages:
                    - "com.acme.api"
                  leaveUnmarked:
                    - "com.acme.legacy"
                publicApi:
                  include:
                    - "com.acme.api.**"
                  exclude:
                    - "com.acme.internal.**"
                  jpmsExportsOnly: true
                nullaway:
                  enabled: true
                  mode: error
                  annotatedPackages:
                    - "com.acme"
                  excludedClasses:
                    - "com.acme.generated.*"
                kotlinVerification:
                  enabled: true
                  failOnWarnings: true
                  generatedTestsDirectory: build/kotlin-checks
                scanner:
                  followSymlinks: false
                """);

        JspecifyConfig config = JspecifyConfigLoader.load(tmp);

        assertEquals(List.of("console", "html", "markdown", "sarif", "json", "junit"),
                config.reportFormats());
        assertEquals(Path.of("build/reports/jml/jspecify"), config.reportsOutputDirectory());
        assertEquals("org.jspecify.annotations.Nullable",
                config.annotationMappings().get("com.acme.Nullable"));
        assertTrue(config.generatedCodeExcludes().contains("**/custom-generated/**"));
        assertEquals(List.of(Path.of("src/main/java")), config.sourceRoots());
        assertEquals(List.of("com.acme.api"), config.markPackages());
        assertEquals(List.of("com.acme.legacy"), config.leaveUnmarkedPackages());
        assertEquals(List.of("com.acme.api.**"), config.publicApiIncludes());
        assertEquals(List.of("com.acme.internal.**"), config.publicApiExcludes());
        assertTrue(config.publicApiJpmsExportsOnly());
        assertTrue(config.nullawayEnabled());
        assertEquals("error", config.nullawayMode());
        assertEquals(List.of("com.acme"), config.nullawayAnnotatedPackages());
        assertEquals(List.of("com.acme.generated.*"), config.nullawayExcludedClasses());
        assertTrue(config.kotlinVerificationEnabled());
        assertTrue(config.kotlinVerificationFailOnWarnings());
        assertEquals(Path.of("build/kotlin-checks"),
                config.kotlinVerificationGeneratedTestsDirectory());
        assertFalse(config.followSymlinks());
    }

    @Test
    void generatedCodeExcludesCanBeDisabled() {
        JspecifyConfig config = JspecifyConfigLoader.parse(List.of(
                "migration:",
                "  generatedCode:",
                "    exclude: false",
                "    patterns:",
                "      - \"**/generated/**\""
        ));

        assertTrue(config.generatedCodeExcludes().isEmpty());
    }

    @Test
    void blockListsReplaceDefaultsAndPriorLayer(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("jml.yml"),
                """
                reports:
                  formats:
                    - console
                    - json
                migration:
                  generatedCode:
                    patterns:
                      - "**/first-generated/**"
                """);
        Files.writeString(tmp.resolve("jspecify.yml"),
                """
                reports:
                  formats:
                    - markdown
                migration:
                  generatedCode:
                    patterns:
                      - "**/custom-generated/**"
                """);

        JspecifyConfig config = JspecifyConfigLoader.load(tmp);

        assertEquals(List.of("markdown"), config.reportFormats());
        assertEquals(List.of("**/custom-generated/**"), config.generatedCodeExcludes());
    }

    @Test
    void quotedBooleansAreParsedAsBooleans() {
        JspecifyConfig config = JspecifyConfigLoader.parse(List.of(
                "nullaway:",
                "  enabled: \"true\"",
                "publicApi:",
                "  jpmsExportsOnly: 'true'",
                "kotlinVerification:",
                "  enabled: \"true\"",
                "  failOnWarnings: 'true'"
        ));

        assertTrue(config.nullawayEnabled());
        assertTrue(config.publicApiJpmsExportsOnly());
        assertTrue(config.kotlinVerificationEnabled());
        assertTrue(config.kotlinVerificationFailOnWarnings());
    }

    @Test
    void inlineListsWorkForAllDocumentedListKeys() {
        JspecifyConfig config = JspecifyConfigLoader.parse(List.of(
                "sourceRoots: [src/main/java, generated/java]",
                "packagePolicy:",
                "  markPackages: [com.acme.api]",
                "  leaveUnmarked: [com.acme.legacy]",
                "publicApi:",
                "  include: [com.acme.api.**]",
                "  exclude: [com.acme.internal.**]",
                "nullaway:",
                "  annotatedPackages: [com.acme]",
                "  excludedClasses: [com.acme.generated.*]"
        ));

        assertEquals(List.of(Path.of("src/main/java"), Path.of("generated/java")),
                config.sourceRoots());
        assertEquals(List.of("com.acme.api"), config.markPackages());
        assertEquals(List.of("com.acme.legacy"), config.leaveUnmarkedPackages());
        assertEquals(List.of("com.acme.api.**"), config.publicApiIncludes());
        assertEquals(List.of("com.acme.internal.**"), config.publicApiExcludes());
        assertEquals(List.of("com.acme"), config.nullawayAnnotatedPackages());
        assertEquals(List.of("com.acme.generated.*"), config.nullawayExcludedClasses());
    }

    @Test
    void stripCommentHonorsSingleQuotedScalars() {
        JspecifyConfig config = JspecifyConfigLoader.parse(List.of(
                "nullaway:",
                "  mode: 'warn#still-value' # real comment"
        ));

        assertEquals("warn#still-value", config.nullawayMode());
    }

    @Test
    void tabIndentationIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> JspecifyConfigLoader.parse(List.of(
                "nullaway:",
                "\tenabled: true"
        )));
    }
}
