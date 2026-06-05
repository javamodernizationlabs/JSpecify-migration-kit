package io.github.javamodernizationlabs.jspecify.gradle;

import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JspecifyMigrationPluginTest {

    @Test
    void registersExpectedTasks() {
        var project = ProjectBuilder.builder().build();
        project.getPlugins().apply("java");
        project.getPlugins().apply(JspecifyMigrationPlugin.class);

        for (String taskName : new String[] {
                "jspecifyPlan", "jspecifyReport",
                "jspecifyRewriteDryRun", "jspecifyRewriteApply",
                "jspecifyCoverage", "jspecifyNullAwayCheck", "jspecifyVerifyKotlin"
        }) {
            assertNotNull(project.getTasks().findByName(taskName),
                    "Expected task missing: " + taskName);
        }
        assertNotNull(project.getExtensions().findByName("jspecifyMigration"));
    }

    @Test
    void testKitRunsPlanTask(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("settings.gradle.kts"),
                "rootProject.name = \"jspecify-testkit-smoke\"\n");
        Files.writeString(tmp.resolve("build.gradle.kts"),
                """
                plugins {
                    java
                    id("io.github.javamodernizationlabs.jspecify-migration")
                }
                """);
        Path source = tmp.resolve("src/main/java/com/acme/Api.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source,
                """
                package com.acme;

                import org.jetbrains.annotations.Nullable;

                public class Api {
                    @Nullable
                    public String name() { return null; }
                }
                """);

        var result = GradleRunner.create()
                .withProjectDir(tmp.toFile())
                .withArguments("jspecifyPlan", "--stacktrace")
                .withPluginClasspath()
                .build();

        assertTrue(result.getOutput().contains("JSpecify reports written"));
        Path report = tmp.resolve("build/reports/jml/jspecify/plan.md");
        assertTrue(Files.readString(report)
                .contains("org.jetbrains.annotations.Nullable"));
    }

    @Test
    void nullAwayCheckFailsWhenProfileCannotRun() {
        var project = ProjectBuilder.builder().build();
        project.getPlugins().apply("java");
        project.getPlugins().apply(JspecifyMigrationPlugin.class);
        var task = (JspecifyNullAwayCheckTask) project.getTasks()
                .getByName("jspecifyNullAwayCheck");
        task.getAnnotatedPackages().set(List.of("com.acme"));

        var failure = assertThrows(org.gradle.api.GradleException.class, task::run);

        assertTrue(failure.getMessage().contains("Error Prone/NullAway"));
    }

    @Test
    void nullAwayCheckWritesReadyReportWhenProfileIsConfigured(@TempDir Path tmp)
            throws Exception {
        var project = ProjectBuilder.builder().withProjectDir(tmp.toFile()).build();
        project.getPlugins().apply("java");
        project.getPlugins().apply(JspecifyMigrationPlugin.class);
        project.getConfigurations().create("errorprone");
        project.getDependencies().add("errorprone", "com.uber.nullaway:nullaway:0.12.0");
        project.getTasks().withType(JavaCompile.class).configureEach(task ->
                task.getOptions().getCompilerArgs().add("-Xplugin:ErrorProne"));
        var task = (JspecifyNullAwayCheckTask) project.getTasks()
                .getByName("jspecifyNullAwayCheck");
        task.getAnnotatedPackages().set(List.of("com.acme"));

        task.run();

        Path report = tmp.resolve("build/reports/jml/jspecify/nullaway-check.md");
        assertTrue(Files.readString(report).contains("Status: `ready`"));
    }

    @Test
    void verifyKotlinFailOnWarningsThrowsGradleException(@TempDir Path tmp) throws Exception {
        var project = ProjectBuilder.builder().withProjectDir(tmp.toFile()).build();
        project.getPlugins().apply("java");
        project.getPlugins().apply(JspecifyMigrationPlugin.class);
        Path source = tmp.resolve("src/main/java/com/acme/Api.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source,
                """
                package com.acme;
                public class Api {
                    public String name() { return ""; }
                }
                """);
        var task = (JspecifyVerifyKotlinTask) project.getTasks()
                .getByName("jspecifyVerifyKotlin");
        task.getKotlinVerificationEnabled().set(true);
        task.getFailOnWarnings().set(true);

        var failure = assertThrows(org.gradle.api.GradleException.class, task::run);

        assertTrue(failure.getMessage().contains("Kotlin verification warnings"));
    }

    @Test
    void rewriteTaskHonorsCustomAnnotationMappings(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("jspecify.yml"),
                """
                annotations:
                  mappings:
                    com.acme.Nullable: org.jspecify.annotations.Nullable
                """);
        var project = ProjectBuilder.builder().withProjectDir(tmp.toFile()).build();
        project.getPlugins().apply("java");
        project.getPlugins().apply(JspecifyMigrationPlugin.class);
        Path source = tmp.resolve("src/main/java/com/acme/Api.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source,
                """
                package com.acme;
                import com.acme.Nullable;
                class Api { @Nullable String name() { return null; } }
                """);
        var task = (JspecifyRewriteHintTask) project.getTasks()
                .getByName("jspecifyRewriteApply");
        task.getRecipe().set("convert-known-annotations");

        task.run();

        assertTrue(Files.readString(source).contains("org.jspecify.annotations.Nullable"));
    }
}
