package io.github.javamodernizationlabs.jspecify.coverage;

import io.github.javamodernizationlabs.jspecify.ProjectModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverageAnalyzerTest {

    @Test
    void estimatesPublicApiNullnessCoverage(@TempDir Path tmp) throws IOException {
        Path api = tmp.resolve("src/main/java/com/acme/api");
        Files.createDirectories(api);
        Files.writeString(api.resolve("package-info.java"),
                """
                @org.jspecify.annotations.NullMarked
                package com.acme.api;
                """);
        Files.writeString(api.resolve("UserApi.java"),
                """
                package com.acme.api;
                import org.jspecify.annotations.Nullable;
                public class UserApi {
                    public String name() { return ""; }
                    public @Nullable String nickname() { return null; }
                }
                """);

        CoverageSummary summary = new CoverageAnalyzer().analyze(ProjectModel.of(tmp));

        assertEquals(3, summary.publicApiElements());
        assertEquals(3, summary.specifiedPublicApiElements());
        assertEquals(1, summary.nullMarkedPackages());
        assertEquals(2, summary.publicMethods());
        assertEquals(2, summary.returnNullnessSpecified());
        assertTrue(summary.specifiedRatio() >= 1.0d);
    }

    @Test
    void publicApiIncludeMatchesBasePackage(@TempDir Path tmp) throws IOException {
        Path api = tmp.resolve("src/main/java/com/acme/api");
        Files.createDirectories(api);
        Files.writeString(api.resolve("UserApi.java"),
                """
                package com.acme.api;
                public class UserApi {}
                """);

        ProjectModel project = ProjectModel.of(tmp, List.of(tmp.resolve("src/main/java")),
                List.of(), List.of("com.acme.api.**"), List.of(), false, false);

        CoverageSummary summary = new CoverageAnalyzer().analyze(project);

        assertEquals(1, summary.publicApiElements());
    }

    @Test
    void nullMarkedPackageCoverageUsesPublicApiScopeForNumerator(@TempDir Path tmp)
            throws IOException {
        Path api = tmp.resolve("src/main/java/com/acme/api");
        Path internal = tmp.resolve("src/main/java/com/acme/internal");
        Files.createDirectories(api);
        Files.createDirectories(internal);
        Files.writeString(api.resolve("package-info.java"),
                "@org.jspecify.annotations.NullMarked\npackage com.acme.api;\n");
        Files.writeString(api.resolve("Api.java"), "package com.acme.api; public class Api {}\n");
        Files.writeString(internal.resolve("package-info.java"),
                "@org.jspecify.annotations.NullMarked\npackage com.acme.internal;\n");
        Files.writeString(internal.resolve("Internal.java"),
                "package com.acme.internal; public class Internal {}\n");
        ProjectModel project = ProjectModel.of(tmp, List.of(tmp.resolve("src/main/java")),
                List.of(), List.of("com.acme.api.**"), List.of(), false, false);

        CoverageSummary summary = new CoverageAnalyzer().analyze(project);

        assertEquals(1, summary.packagesSeen());
        assertEquals(1, summary.nullMarkedPackages());
        assertEquals(1.0d, summary.nullMarkedPackageRatio());
    }

    @Test
    void parameterAnnotationDoesNotSpecifyReturnNullness(@TempDir Path tmp) throws IOException {
        Path api = tmp.resolve("src/main/java/com/acme");
        Files.createDirectories(api);
        Files.writeString(api.resolve("Api.java"),
                """
                package com.acme;
                import org.jspecify.annotations.Nullable;
                public class Api {
                    public String find(@Nullable String key) { return ""; }
                }
                """);

        CoverageSummary summary = new CoverageAnalyzer().analyze(ProjectModel.of(tmp));

        assertEquals(1, summary.publicMethods());
        assertEquals(0, summary.returnNullnessSpecified());
        assertEquals(1, summary.publicParameters());
        assertEquals(1, summary.parameterNullnessSpecified());
    }

    @Test
    void genericTypeUseCoverageCountsAnnotatedGroupsIndividually(@TempDir Path tmp)
            throws IOException {
        Path api = tmp.resolve("src/main/java/com/acme");
        Files.createDirectories(api);
        Files.writeString(api.resolve("Api.java"),
                """
                package com.acme;
                import java.util.List;
                import java.util.Map;
                import org.jspecify.annotations.Nullable;
                public class Api {
                    public Map<String, List<@Nullable Foo>> names() { return null; }
                    public @Nullable List<String> nullableList() { return null; }
                }
                class Foo {}
                """);

        CoverageSummary summary = new CoverageAnalyzer().analyze(ProjectModel.of(tmp));

        assertEquals(3, summary.genericTypeUses());
        assertEquals(2, summary.genericTypeUseNullnessSpecified());
        assertEquals(1, summary.returnNullnessSpecified());
    }

    @Test
    void emptyProjectDoesNotReportPerfectCoverage(@TempDir Path tmp) throws IOException {
        CoverageSummary summary = new CoverageAnalyzer().analyze(ProjectModel.of(tmp));

        assertEquals(0.0d, summary.specifiedRatio());
        assertEquals(0.0d, summary.nullMarkedPackageRatio());
        assertFalse(summary.elementsFound());
    }

    @Test
    void projectWithApiReportsElementsFound(@TempDir Path tmp) throws IOException {
        Path api = tmp.resolve("src/main/java/com/acme");
        Files.createDirectories(api);
        Files.writeString(api.resolve("Api.java"),
                """
                package com.acme;
                public class Api {}
                """);

        CoverageSummary summary = new CoverageAnalyzer().analyze(ProjectModel.of(tmp));

        assertTrue(summary.elementsFound());
    }

    @Test
    void comparisonAndShiftOperatorsDoNotCountAsGenericTypeUses(@TempDir Path tmp)
            throws IOException {
        Path api = tmp.resolve("src/main/java/com/acme");
        Files.createDirectories(api);
        Files.writeString(api.resolve("Api.java"),
                """
                package com.acme;
                public class Api {
                    public boolean less = 1 < 2;
                    public int shifted = 1 << 2;
                }
                """);

        CoverageSummary summary = new CoverageAnalyzer().analyze(ProjectModel.of(tmp));

        assertEquals(0, summary.genericTypeUses());
    }
}
