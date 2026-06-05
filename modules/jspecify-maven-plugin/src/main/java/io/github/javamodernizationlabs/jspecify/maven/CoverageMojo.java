package io.github.javamodernizationlabs.jspecify.maven;

import io.github.javamodernizationlabs.jspecify.ProjectModel;
import io.github.javamodernizationlabs.jspecify.config.JspecifyConfig;
import io.github.javamodernizationlabs.jspecify.config.JspecifyConfigLoader;
import io.github.javamodernizationlabs.jspecify.coverage.CoverageAnalyzer;
import io.github.javamodernizationlabs.jspecify.report.CoverageReportWriter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;

/**
 * Implements the {@code jspecify-migration:coverage} goal.
 *
 * <p>This goal analyzes how much of the current Maven project is already covered by JSpecify
 * nullness annotations and writes a coverage report to the configured output directory.
 */
@Mojo(name = "coverage", threadSafe = true, requiresProject = true)
public class CoverageMojo extends AbstractMojo {

    /**
     * Creates a {@code CoverageMojo}.
     */
    public CoverageMojo() {
    }

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "jspecify.outputDirectory",
            defaultValue = "${project.build.directory}/reports/jml/jspecify")
    private File outputDirectory;

    /**
     * Runs the {@code jspecify-migration:coverage} goal.
     *
     * <p>Loads the JSpecify configuration, computes a coverage summary for the project, and writes
     * the coverage report to the configured output directory.
     *
     * @throws MojoExecutionException if the configuration cannot be loaded, coverage cannot be
     *     computed, or the report fails to be written
     */
    @Override
    public void execute() throws MojoExecutionException {
        try {
            var projectRoot = project.getBasedir().toPath();
            JspecifyConfig config = JspecifyConfigLoader.load(projectRoot);
            var summary = new CoverageAnalyzer().analyze(ProjectModel.of(projectRoot, config));
            new CoverageReportWriter().write(outputDirectory.toPath(), summary);
            getLog().info("JSpecify coverage report written to " + outputDirectory);
        } catch (Exception e) {
            throw new MojoExecutionException("JSpecify coverage failed: " + e.getMessage(), e);
        }
    }
}
