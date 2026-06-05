package io.github.javamodernizationlabs.jspecify.maven;

import io.github.javamodernizationlabs.jspecify.AnnotationCatalog;
import io.github.javamodernizationlabs.jspecify.ProjectModel;
import io.github.javamodernizationlabs.jspecify.config.JspecifyConfig;
import io.github.javamodernizationlabs.jspecify.config.JspecifyConfigLoader;
import io.github.javamodernizationlabs.jspecify.report.RewriteReportWriter;
import io.github.javamodernizationlabs.jspecify.rewrite.JspecifyRewriter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.List;

/**
 * Implements the {@code jspecify-migration:rewrite-hint} goal.
 *
 * <p>This goal runs the configured OpenRewrite recipe against the current Maven project to add
 * JSpecify nullness annotations and writes a Markdown summary of the changed files and replacements
 * to the configured output directory. By default it performs a dry run; whether changes are applied
 * to source files is controlled by the {@code jspecify.apply} parameter.
 *
 * <p>This class also serves as the base for {@link RewriteApplyMojo} and {@link RewriteDryRunMojo},
 * which preconfigure the apply behavior.
 */
@Mojo(name = "rewrite-hint", threadSafe = true, requiresProject = true)
public class RewriteHintMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "jspecify.outputDirectory",
            defaultValue = "${project.build.directory}/reports/jml/jspecify")
    private File outputDirectory;

    @Parameter(property = "jspecify.recipe",
            defaultValue = "io.github.jml.jspecify.Migrate")
    private String recipe = "io.github.jml.jspecify.Migrate";

    @Parameter(property = "jspecify.apply", defaultValue = "false")
    private boolean apply;

    private final Boolean forcedApply;

    /**
     * Creates a mojo that performs a dry run unless overridden by the {@code jspecify.apply}
     * parameter.
     */
    public RewriteHintMojo() {
        this.forcedApply = null;
    }

    /**
     * Creates a mojo with a fixed apply behavior, used by subclasses that hardcode whether changes
     * are written to source files.
     *
     * @param apply {@code true} to apply rewrites to source files, {@code false} to perform a dry
     *     run
     */
    protected RewriteHintMojo(boolean apply) {
        this.apply = apply;
        this.forcedApply = apply;
    }

    /**
     * Runs the {@code jspecify-migration:rewrite-hint} goal.
     *
     * <p>Loads the JSpecify configuration, runs the configured recipe against the project (applying
     * changes only when apply mode is enabled), and writes a Markdown summary of the changed files
     * and replacements to the configured output directory.
     *
     * @throws MojoExecutionException if the configuration cannot be loaded, the rewrite fails, or
     *     the report fails to be written
     */
    @Override
    public void execute() throws MojoExecutionException {
        try {
            var projectRoot = project.getBasedir().toPath();
            JspecifyConfig config = JspecifyConfigLoader.load(projectRoot);
            boolean effectiveApply = forcedApply == null ? apply : forcedApply;
            var result = new JspecifyRewriter(new AnnotationCatalog(config.annotationMappings()))
                    .rewrite(ProjectModel.of(projectRoot, config), List.of(recipe), effectiveApply);
            var report = outputDirectory.toPath().resolve("rewrite.md");
            new RewriteReportWriter().write(report, result);
            getLog().info("JSpecify rewrite " + (effectiveApply ? "applied" : "dry run")
                    + ": " + result.changedFiles() + " files, "
                    + result.replacements() + " replacements");
            getLog().info("JSpecify rewrite report written to " + report);
        } catch (Exception e) {
            throw new MojoExecutionException("JSpecify rewrite failed: " + e.getMessage(), e);
        }
    }
}
