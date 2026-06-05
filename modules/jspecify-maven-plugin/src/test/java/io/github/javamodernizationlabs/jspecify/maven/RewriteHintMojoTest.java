package io.github.javamodernizationlabs.jspecify.maven;

import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RewriteHintMojoTest {

    @Test
    void rewriteApplyCannotBeOverriddenToDryRun(@TempDir Path tmp) throws Exception {
        Path source = source(tmp);
        RewriteApplyMojo mojo = new RewriteApplyMojo();
        configure(mojo, tmp, false);

        mojo.execute();

        assertTrue(Files.readString(source).contains("org.jspecify.annotations.Nullable"));
    }

    @Test
    void rewriteDryRunCannotBeOverriddenToApply(@TempDir Path tmp) throws Exception {
        Path source = source(tmp);
        RewriteDryRunMojo mojo = new RewriteDryRunMojo();
        configure(mojo, tmp, true);

        mojo.execute();

        assertTrue(Files.readString(source).contains("org.jetbrains.annotations.Nullable"));
    }

    @Test
    void rewriteHintHonorsCustomAnnotationMappings(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("jspecify.yml"),
                """
                annotations:
                  mappings:
                    com.acme.Nullable: org.jspecify.annotations.Nullable
                """);
        Files.writeString(tmp.resolve("pom.xml"),
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.acme</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                </project>
                """);
        Path source = tmp.resolve("src/main/java/com/acme/Api.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source,
                """
                package com.acme;
                import com.acme.Nullable;
                class Api { @Nullable String name() { return null; } }
                """);
        RewriteHintMojo mojo = new RewriteHintMojo();
        configure(mojo, tmp, true);

        mojo.execute();

        assertTrue(Files.readString(source).contains("org.jspecify.annotations.Nullable"));
    }

    private Path source(Path tmp) throws Exception {
        Files.writeString(tmp.resolve("pom.xml"),
                """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.acme</groupId>
                  <artifactId>demo</artifactId>
                  <version>1.0.0</version>
                </project>
                """);
        Path source = tmp.resolve("src/main/java/com/acme/Api.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source,
                """
                package com.acme;
                import org.jetbrains.annotations.Nullable;
                class Api { @Nullable String name() { return null; } }
                """);
        return source;
    }

    private void configure(RewriteHintMojo mojo, Path tmp, boolean injectedApply) throws Exception {
        MavenProject project = new MavenProject(new Model());
        project.setFile(tmp.resolve("pom.xml").toFile());
        set(mojo, "project", project);
        set(mojo, "outputDirectory", tmp.resolve("target/reports").toFile());
        set(mojo, "recipe", "convert-known-annotations");
        set(mojo, "apply", injectedApply);
    }

    private void set(Object target, String fieldName, Object value) throws Exception {
        Field field = RewriteHintMojo.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
