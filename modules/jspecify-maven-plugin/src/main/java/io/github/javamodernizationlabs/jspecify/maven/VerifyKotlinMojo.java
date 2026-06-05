package io.github.javamodernizationlabs.jspecify.maven;

import io.github.javamodernizationlabs.jspecify.ProjectModel;
import io.github.javamodernizationlabs.jspecify.config.JspecifyConfig;
import io.github.javamodernizationlabs.jspecify.config.JspecifyConfigLoader;
import io.github.javamodernizationlabs.jspecify.kotlin.KotlinInteropVerifier;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

/**
 * Implements the {@code jspecify-migration:verify-kotlin} goal.
 *
 * <p>This goal verifies how the current Maven project's JSpecify nullness annotations are seen from
 * Kotlin. It can optionally generate Kotlin interop samples and compile them, and writes a
 * verification report to the configured output directory. When configured to fail on warnings, it
 * fails the build if any verification warnings are produced.
 */
@Mojo(name = "verify-kotlin", threadSafe = true, requiresProject = true)
public class VerifyKotlinMojo extends AbstractMojo {

    /**
     * Creates a {@code VerifyKotlinMojo}.
     */
    public VerifyKotlinMojo() {
    }

    @Parameter(property = "jspecify.outputDirectory",
            defaultValue = "${project.build.directory}/reports/jml/jspecify/kotlin-verification")
    private File outputDirectory;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "jspecify.kotlin.generateSamples", defaultValue = "true")
    private boolean generateSamples;

    @Parameter(property = "jspecify.kotlin.compile", defaultValue = "false")
    private boolean compile;

    @Parameter(property = "jspecify.kotlin.failOnWarnings", defaultValue = "false")
    private boolean failOnWarnings;

    /**
     * Runs the {@code jspecify-migration:verify-kotlin} goal.
     *
     * <p>Loads the JSpecify configuration, runs the Kotlin interop verifier over the project (using
     * the project's compiled output directory and the configured sample-generation and compilation
     * options), and writes the verification report to the configured output directory.
     *
     * @throws MojoExecutionException if the configuration cannot be loaded, verification fails, or
     *     verification produces warnings while fail-on-warnings mode is enabled
     */
    @Override
    public void execute() throws MojoExecutionException {
        try {
            var out = outputDirectory.toPath();
            Files.createDirectories(out);
            var projectRoot = project.getBasedir().toPath();
            JspecifyConfig config = JspecifyConfigLoader.load(projectRoot);
            var result = new KotlinInteropVerifier().verify(ProjectModel.of(projectRoot, config),
                    out, generateSamples, compile, List.of(project.getBuild().getOutputDirectory())
                            .stream().map(java.nio.file.Path::of).toList());
            if (failOnWarnings && !result.warnings().isEmpty()) {
                throw new MojoExecutionException("Kotlin verification warnings: "
                        + String.join("; ", result.warnings()));
            }
            getLog().info("JSpecify Kotlin verification report written to " + out);
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("JSpecify Kotlin verification failed: " + e.getMessage(), e);
        }
    }
}
