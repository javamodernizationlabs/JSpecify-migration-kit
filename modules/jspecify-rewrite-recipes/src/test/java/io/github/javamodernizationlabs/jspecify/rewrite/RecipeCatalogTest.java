package io.github.javamodernizationlabs.jspecify.rewrite;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeCatalogTest {

    @Test
    void publishesFrameworkPresets() throws Exception {
        try (var in = getClass().getResourceAsStream("/META-INF/rewrite/jspecify.yml")) {
            assertNotNull(in);
            String catalog = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(catalog.contains("io.github.jml.jspecify.AddDependency"));
            assertTrue(catalog.contains("io.github.jml.jspecify.AddNullMarkedToPackage"));
            assertTrue(catalog.contains("io.github.jml.jspecify.SpringPreset"));
            assertTrue(catalog.contains("io.github.jml.jspecify.ReactorPreset"));
            assertTrue(catalog.contains("io.github.jml.jspecify.MicrometerPreset"));
            assertTrue(catalog.contains("io.github.jml.jspecify.FixTypeUseAnnotationPlacement"));
            assertTrue(catalog.contains("io.github.jml.jspecify.RemoveOldAnnotationDependencies"));
            assertTrue(catalog.contains("org.openrewrite.maven.RemoveDependency"));
            assertTrue(catalog.contains("org.openrewrite.gradle.RemoveDependency"));
            assertTrue(catalog.contains("@io.micrometer.common.lang.NonNullApi"));
        }
    }

    @Test
    void documentsFixTypeUsePlacementNarrowing() throws Exception {
        try (var in = getClass().getResourceAsStream("/META-INF/rewrite/jspecify.yml")) {
            assertNotNull(in);
            String catalog = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(catalog.contains("does not match arbitrary *.Nullable annotations"),
                    "FixTypeUseAnnotationPlacement should document its intentional narrowing");
        }
    }
}
