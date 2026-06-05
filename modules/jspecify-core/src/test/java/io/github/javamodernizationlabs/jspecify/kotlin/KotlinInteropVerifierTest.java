package io.github.javamodernizationlabs.jspecify.kotlin;

import io.github.javamodernizationlabs.jspecify.ProjectModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KotlinInteropVerifierTest {

    @Test
    void generatesKotlinSamplesForPublicApi(@TempDir Path tmp) throws Exception {
        Path api = tmp.resolve("src/main/java/com/acme");
        Files.createDirectories(api);
        Files.writeString(api.resolve("UserApi.java"),
                """
                package com.acme;
                import org.jspecify.annotations.Nullable;
                @org.jspecify.annotations.NullMarked
                public class UserApi {
                    public String name() { return ""; }
                    public @Nullable String nickname() { return null; }
                }
                """);

        Path out = tmp.resolve("build/jspecify-kotlin-verification");
        KotlinVerificationResult result = new KotlinInteropVerifier()
                .verify(ProjectModel.of(tmp), out, true, false, List.of());

        String sample = Files.readString(result.sampleFile());
        assertTrue(sample.contains("com.acme.UserApi"));
        assertTrue(sample.contains("val nameValue: String = api.name()"));
        assertTrue(sample.contains("val nicknameValue: String? = api.nickname()"));
        assertFalse(result.warnings().stream()
                .anyMatch(warning -> warning.contains("KOTLIN_PLATFORM_TYPE_LEAK")));
        assertTrue(Files.readString(out.resolve("kotlin-verification.md"))
                .contains("Samples generated: `true`"));
    }

    @Test
    void reportsPlatformTypeLeaksForUnmarkedPublicApi(@TempDir Path tmp) throws Exception {
        Path api = tmp.resolve("src/main/java/com/acme");
        Files.createDirectories(api);
        Files.writeString(api.resolve("UserApi.java"),
                """
                package com.acme;
                public class UserApi {
                    public String name() { return ""; }
                }
                """);

        KotlinVerificationResult result = new KotlinInteropVerifier()
                .verify(ProjectModel.of(tmp), tmp.resolve("build/kotlin"), true, false, List.of());

        assertTrue(result.warnings().stream()
                .anyMatch(warning -> warning.contains("KOTLIN_PLATFORM_TYPE_LEAK")
                        && warning.contains("com.acme.UserApi#name()")));
    }

    @Test
    void generatesValidTypesForArraysNestedGenericsAndMethodModifiers(@TempDir Path tmp)
            throws Exception {
        Path api = tmp.resolve("src/main/java/com/acme");
        Files.createDirectories(api);
        Files.writeString(api.resolve("UserApi.java"),
                """
                package com.acme;
                import java.util.List;
                import java.util.Map;
                @org.jspecify.annotations.NullMarked
                public interface UserApi {
                    public default String greet() { return ""; }
                    public int[] ids();
                    public Object[][] matrix();
                    public Map<String, List<Integer>> names();
                }
                """);

        KotlinVerificationResult result = new KotlinInteropVerifier()
                .verify(ProjectModel.of(tmp), tmp.resolve("build/kotlin"), true, false, List.of());

        String sample = Files.readString(result.sampleFile());
        assertTrue(sample.contains("val greetValue: String = api.greet()"));
        assertTrue(sample.contains("val idsValue: IntArray = api.ids()"));
        assertTrue(sample.contains("val matrixValue: Array<Array<Any>> = api.matrix()"));
        assertTrue(sample.contains("val namesValue: java.util.Map<String, java.util.List<Integer>>"));
        assertFalse(sample.contains("default String"));
        assertFalse(sample.contains("Map>"));
        assertFalse(sample.contains("Array<int>"));
    }

    @Test
    void ignoresMethodsDeclaredOnlyOnNestedTypes(@TempDir Path tmp) throws Exception {
        Path api = tmp.resolve("src/main/java/com/acme");
        Files.createDirectories(api);
        Files.writeString(api.resolve("OuterApi.java"),
                """
                package com.acme;
                @org.jspecify.annotations.NullMarked
                public class OuterApi {
                    public String outerName() { return ""; }
                    public static class InnerApi {
                        public String innerName() { return ""; }
                    }
                }
                """);

        KotlinVerificationResult result = new KotlinInteropVerifier()
                .verify(ProjectModel.of(tmp), tmp.resolve("build/kotlin"), true, false, List.of());

        String sample = Files.readString(result.sampleFile());
        assertTrue(sample.contains("api.outerName()"));
        assertFalse(sample.contains("api.innerName()"));
    }
}
