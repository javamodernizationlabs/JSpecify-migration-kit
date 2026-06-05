package io.github.javamodernizationlabs.jspecify.maven;

import io.github.javamodernizationlabs.jspecify.AnnotationInventory;
import io.github.javamodernizationlabs.jspecify.MigrationPlan;
import io.github.javamodernizationlabs.jspecify.MigrationPlanner;
import io.github.javamodernizationlabs.jspecify.ProjectModel;
import io.github.javamodernizationlabs.jspecify.config.JspecifyConfig;
import io.github.javamodernizationlabs.jspecify.config.JspecifyConfigLoader;
import io.github.javamodernizationlabs.jspecify.report.ConsoleReportWriter;
import io.github.javamodernizationlabs.jspecify.report.HtmlReportWriter;
import io.github.javamodernizationlabs.jspecify.report.JsonReportWriter;
import io.github.javamodernizationlabs.jspecify.report.JunitXmlReportWriter;
import io.github.javamodernizationlabs.jspecify.report.MarkdownReportWriter;
import io.github.javamodernizationlabs.jspecify.report.SarifReportWriter;
import io.github.javamodernizationlabs.jspecify.scan.AnnotationScanner;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;

/**
 * Implements the {@code jspecify-migration:plan} goal.
 *
 * <p>This goal scans the current Maven project for JSpecify nullness annotations, builds a
 * migration plan from the resulting inventory, and prints a summary to the console. It also writes
 * the plan to the configured output directory in JSON, Markdown, SARIF, HTML, and JUnit XML
 * formats.
 */
@Mojo(name = "plan", threadSafe = true, requiresProject = true)
public class PlanMojo extends AbstractMojo {

    /**
     * Creates a {@code PlanMojo}.
     */
    public PlanMojo() {
    }

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "jspecify.outputDirectory",
            defaultValue = "${project.build.directory}/reports/jml/jspecify")
    private File outputDirectory;

    /**
     * Runs the {@code jspecify-migration:plan} goal.
     *
     * <p>Loads the JSpecify configuration, scans the project for nullness annotations, computes a
     * migration plan, prints a console summary, and writes the plan reports to the configured
     * output directory.
     *
     * @throws MojoExecutionException if the configuration cannot be loaded, the project cannot be
     *     scanned, or any report fails to be written
     */
    @Override
    public void execute() throws MojoExecutionException {
        try {
            var projectRoot = project.getBasedir().toPath();
            JspecifyConfig config = JspecifyConfigLoader.load(projectRoot);
            ProjectModel model = ProjectModel.of(projectRoot, config);
            AnnotationInventory inventory = AnnotationScanner.forConfig(config).scan(model);
            MigrationPlan plan = new MigrationPlanner().plan(inventory);

            new ConsoleReportWriter().write(System.out, plan);
            var out = outputDirectory.toPath();
            new JsonReportWriter().write(out.resolve("plan.json"), plan);
            new MarkdownReportWriter().write(out.resolve("plan.md"), plan);
            new SarifReportWriter().write(out.resolve("plan.sarif"), plan);
            new HtmlReportWriter().write(out.resolve("index.html"), plan);
            new JunitXmlReportWriter().write(out.resolve("TEST-jspecify-migration.xml"), plan);
            getLog().info("JSpecify reports written to " + out);
        } catch (Exception e) {
            throw new MojoExecutionException("JSpecify plan failed: " + e.getMessage(), e);
        }
    }
}
