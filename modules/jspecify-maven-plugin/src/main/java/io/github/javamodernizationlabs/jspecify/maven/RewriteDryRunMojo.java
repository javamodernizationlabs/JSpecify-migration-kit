package io.github.javamodernizationlabs.jspecify.maven;

import org.apache.maven.plugins.annotations.Mojo;

/**
 * Implements the {@code jspecify-migration:rewrite-dry-run} goal.
 *
 * <p>This goal runs the configured OpenRewrite recipe and reports the JSpecify nullness annotation
 * changes that would be made without modifying any source files. It is a thin variant of
 * {@link RewriteHintMojo} with apply mode forced off.
 */
@Mojo(name = "rewrite-dry-run", threadSafe = true, requiresProject = true)
public class RewriteDryRunMojo extends RewriteHintMojo {
    /**
     * Creates a mojo that performs a dry run without applying rewrites to source files.
     */
    public RewriteDryRunMojo() {
        super(false);
    }
}
