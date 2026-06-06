package io.github.javamodernizationlabs.jspecify.gradle;

import io.github.javamodernizationlabs.jspecify.ProjectModel;
import io.github.javamodernizationlabs.jspecify.config.JspecifyConfig;
import io.github.javamodernizationlabs.jspecify.config.JspecifyConfigLoader;
import io.github.javamodernizationlabs.jspecify.kotlin.KotlinInteropVerifier;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Gradle task that generates Kotlin interoperability verification artifacts.
 *
 * <p>The task loads the JSpecify configuration, builds a project model and runs the Kotlin
 * interop verifier to generate (and optionally compile) Kotlin sample sources that exercise
 * the project's annotated API. Generated artifacts are written into the configured output
 * directory, and the build can optionally fail when warnings are reported.</p>
 */
@DisableCachingByDefault(because = "Generates verification sources from local project API state.")
public abstract class JspecifyVerifyKotlinTask extends DefaultTask {

    /**
     * Creates a {@code JspecifyVerifyKotlinTask}.
     */
    public JspecifyVerifyKotlinTask() {
    }

    /**
     * Whether Kotlin interop verification is enabled.
     *
     * @return a {@code Property<Boolean>} that is {@code true} when Kotlin verification is enabled
     */
    @Input
    public abstract Property<Boolean> getKotlinVerificationEnabled();

    /**
     * The name of the generated source set used for verification samples.
     *
     * @return a {@code Property<String>} holding the generated source set name
     */
    @Input
    public abstract Property<String> getGeneratedSourceSet();

    /**
     * Whether to compile the generated Kotlin verification samples.
     *
     * @return a {@code Property<Boolean>} that is {@code true} when samples should be compiled
     */
    @Input
    public abstract Property<Boolean> getCompileSamples();

    /**
     * Whether verification should fail the build when warnings are reported.
     *
     * @return a {@code Property<Boolean>} that is {@code true} when warnings should fail the build
     */
    @Input
    public abstract Property<Boolean> getFailOnWarnings();

    /**
     * The compiled output of the project's main source set, used as the classpath when
     * compiling generated Kotlin samples.
     *
     * <p>The plugin wires this from the {@code main} source set output at configuration time,
     * which also establishes the implicit dependency on {@code compileJava}.</p>
     *
     * @return a {@link ConfigurableFileCollection} of main compiled-output directories
     */
    @Classpath
    public abstract ConfigurableFileCollection getMainClasses();

    /**
     * The directory into which the Kotlin verification artifacts are written.
     *
     * @return a {@link DirectoryProperty} pointing at the output directory
     */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    /**
     * Generates the Kotlin interop verification artifacts.
     *
     * <p>Loads the JSpecify configuration, builds the project model, runs the Kotlin interop
     * verifier to generate and optionally compile sample sources, and writes the results to
     * the output directory.</p>
     *
     * @throws IOException if artifacts cannot be written, or if {@code failOnWarnings} is
     *         enabled and the verifier reports one or more warnings
     */
    @TaskAction
    public void run() throws IOException {
        var output = getOutputDirectory().getAsFile().get().toPath();
        Files.createDirectories(output);
        var projectRoot = getProject().getProjectDir().toPath();
        JspecifyConfig config = JspecifyConfigLoader.load(projectRoot);
        var result = new KotlinInteropVerifier().verify(ProjectModel.of(projectRoot, config),
                output,
                getKotlinVerificationEnabled().getOrElse(false),
                getCompileSamples().getOrElse(false),
                mainClasses());
        if (getFailOnWarnings().getOrElse(false) && !result.warnings().isEmpty()) {
            throw new GradleException("Kotlin verification warnings: "
                    + String.join("; ", result.warnings()));
        }
        getLogger().lifecycle("JSpecify Kotlin verification report written to {}", output);
    }

    private List<Path> mainClasses() {
        return getMainClasses().getFiles().stream()
                .map(java.io.File::toPath)
                .toList();
    }
}
