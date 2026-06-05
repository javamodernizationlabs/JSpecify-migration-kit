package io.github.javamodernizationlabs.jspecify.gradle;

import io.github.javamodernizationlabs.jspecify.AnnotationCatalog;
import io.github.javamodernizationlabs.jspecify.ProjectModel;
import io.github.javamodernizationlabs.jspecify.config.JspecifyConfig;
import io.github.javamodernizationlabs.jspecify.config.JspecifyConfigLoader;
import io.github.javamodernizationlabs.jspecify.report.RewriteReportWriter;
import io.github.javamodernizationlabs.jspecify.rewrite.JspecifyRewriter;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.IOException;
import java.util.List;

/**
 * Gradle task that previews or applies JSpecify rewrite recipes.
 *
 * <p>The task loads the JSpecify configuration, builds a project model and runs the configured
 * rewrite recipe either in dry-run mode (previewing changes) or apply mode (modifying source
 * files). A Markdown rewrite report describing the changed files and replacements is written
 * into the configured output directory.</p>
 */
@DisableCachingByDefault(because = "May rewrite project files and writes reports from local filesystem state.")
public abstract class JspecifyRewriteHintTask extends DefaultTask {

    /**
     * Creates a {@code JspecifyRewriteHintTask}.
     */
    public JspecifyRewriteHintTask() {
    }

    /**
     * Whether the rewrite recipe should be applied rather than previewed.
     *
     * @return a {@code Property<Boolean>} that is {@code true} to apply changes or {@code false} for a dry run
     */
    @Input
    public abstract Property<Boolean> getApply();

    /**
     * The fully qualified name of the rewrite recipe to run.
     *
     * @return a {@code Property<String>} holding the rewrite recipe identifier
     */
    @Input
    public abstract Property<String> getRecipe();

    /**
     * The directory into which the rewrite report is written.
     *
     * @return a {@link DirectoryProperty} pointing at the report output directory
     */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    /**
     * Runs the configured rewrite recipe and writes the rewrite report.
     *
     * <p>Loads the JSpecify configuration, builds the project model, executes the recipe in
     * dry-run or apply mode and writes a Markdown report summarising the changed files and
     * replacements.</p>
     *
     * @throws IOException if the rewrite report cannot be written to the output directory
     */
    @TaskAction
    public void run() throws IOException {
        boolean apply = getApply().getOrElse(false);
        String recipe = getRecipe().getOrElse("io.github.jml.jspecify.Migrate");
        var projectRoot = getProject().getProjectDir().toPath();
        JspecifyConfig config = JspecifyConfigLoader.load(projectRoot);
        var result = new JspecifyRewriter(new AnnotationCatalog(config.annotationMappings()))
                .rewrite(ProjectModel.of(projectRoot, config), List.of(recipe), apply);
        var report = getOutputDirectory().getAsFile().get().toPath().resolve("rewrite.md");
        new RewriteReportWriter().write(report, result);
        getLogger().lifecycle("JSpecify rewrite {}: {} files, {} replacements",
                apply ? "applied" : "dry run",
                result.changedFiles(),
                result.replacements());
        getLogger().lifecycle("JSpecify rewrite report written to {}", report);
    }
}
