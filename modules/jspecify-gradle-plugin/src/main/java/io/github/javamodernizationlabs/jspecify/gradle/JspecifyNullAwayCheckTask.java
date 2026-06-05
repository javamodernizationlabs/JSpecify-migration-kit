package io.github.javamodernizationlabs.jspecify.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Gradle task that generates NullAway/Error Prone configuration for the JSpecify migration.
 *
 * <p>When NullAway verification is enabled, the task validates that the project exposes an
 * executable Error Prone and NullAway setup, then writes a ready-to-use Gradle snippet and a
 * Markdown status report into the configured output directory. When verification is disabled,
 * it records the disabled status instead.</p>
 */
@DisableCachingByDefault(because = "Inspects project task and dependency configuration.")
public abstract class JspecifyNullAwayCheckTask extends DefaultTask {

    /**
     * Creates a {@code JspecifyNullAwayCheckTask}.
     */
    public JspecifyNullAwayCheckTask() {
    }

    /**
     * Whether the NullAway verification profile is enabled.
     *
     * @return a {@code Property<Boolean>} that is {@code true} when NullAway verification is enabled
     */
    @Input
    public abstract Property<Boolean> getNullAwayEnabled();

    /**
     * The NullAway severity mode, for example {@code warn} or {@code error}.
     *
     * @return a {@code Property<String>} holding the NullAway mode
     */
    @Input
    public abstract Property<String> getMode();

    /**
     * The packages NullAway should treat as annotated.
     *
     * @return a {@code ListProperty<String>} of annotated package names
     */
    @Input
    public abstract ListProperty<String> getAnnotatedPackages();

    /**
     * The classes NullAway should exclude from analysis.
     *
     * @return a {@code ListProperty<String>} of excluded class names
     */
    @Input
    public abstract ListProperty<String> getExcludedClasses();

    /**
     * Whether Error Prone wiring was detected during task configuration.
     *
     * @return a {@code Property<Boolean>} that is {@code true} when Error Prone is configured
     */
    @Input
    public abstract Property<Boolean> getErrorProneConfigured();

    /**
     * Whether NullAway wiring was detected during task configuration.
     *
     * @return a {@code Property<Boolean>} that is {@code true} when NullAway is configured
     */
    @Input
    public abstract Property<Boolean> getNullAwayConfigured();

    /**
     * The directory into which the generated NullAway configuration and report are written.
     *
     * @return a {@link DirectoryProperty} pointing at the output directory
     */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    /**
     * Verifies the NullAway setup and writes the generated configuration and status report.
     *
     * <p>If verification is disabled, writes a Markdown report recording the disabled status.
     * Otherwise it requires that at least one annotated package is configured and that the
     * project exposes an Error Prone and NullAway setup, then writes a Gradle snippet and a
     * Markdown status report.</p>
     *
     * @throws IOException if a configuration file or report cannot be written
     * @throws GradleException if verification is enabled but no annotated packages are
     *         configured, or the project lacks an executable Error Prone/NullAway setup
     */
    @TaskAction
    public void run() throws IOException {
        var output = getOutputDirectory().getAsFile().get().toPath();
        Files.createDirectories(output);
        if (!getNullAwayEnabled().getOrElse(false)) {
            Files.writeString(output.resolve("nullaway-check.md"),
                    "# JSpecify NullAway check\n\nStatus: `disabled`\n",
                    StandardCharsets.UTF_8);
            getLogger().lifecycle("JSpecify NullAway check disabled.");
            return;
        }

        List<String> packages = getAnnotatedPackages().getOrElse(List.of());
        List<String> excluded = getExcludedClasses().getOrElse(List.of());
        if (packages.isEmpty()) {
            throw new GradleException("JSpecify NullAway profile is enabled but no "
                    + "annotatedPackages are configured.");
        }
        boolean errorProneConfigured = getErrorProneConfigured().getOrElse(false);
        boolean nullAwayConfigured = getNullAwayConfigured().getOrElse(false);
        if (!errorProneConfigured || !nullAwayConfigured) {
            throw new GradleException("JSpecify NullAway profile is enabled but the project "
                    + "does not expose an executable Error Prone/NullAway setup. Apply an "
                    + "Error Prone Gradle plugin and add the com.uber.nullaway:nullaway "
                    + "dependency before running jspecifyNullAwayCheck.");
        }

        String severity = getMode().getOrElse("warn").equalsIgnoreCase("error")
                ? "ERROR"
                : "WARN";
        String gradleSnippet = """
                // Generated by JSpecify Migration Kit.
                // Apply the Error Prone plugin, then add these NullAway options.
                tasks.withType(JavaCompile).configureEach {
                    options.errorprone {
                        option("NullAway:AnnotatedPackages", "%s")
                        option("NullAway:ExcludedClasses", "%s")
                        check("NullAway", CheckSeverity.%s)
                    }
                }
                """.formatted(
                String.join(",", packages),
                String.join(",", excluded),
                severity);
        Files.writeString(output.resolve("nullaway.gradle.kts"), gradleSnippet,
                StandardCharsets.UTF_8);
        String report = """
                # JSpecify NullAway check

                Status: `ready`
                Error Prone configured: `%s`
                NullAway configured: `%s`
                Mode: `%s`
                Annotated packages: `%s`
                Excluded classes: `%s`
                """.formatted(errorProneConfigured, nullAwayConfigured,
                getMode().getOrElse("warn"), String.join(",", packages),
                String.join(",", excluded));
        Files.writeString(output.resolve("nullaway-check.md"), report, StandardCharsets.UTF_8);
        getLogger().lifecycle("JSpecify NullAway profile verified; config written to {}", output);
    }

}
