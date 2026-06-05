package io.github.javamodernizationlabs.jspecify.maven;

import org.apache.maven.plugins.annotations.Mojo;

/**
 * Implements the {@code jspecify-migration:rewrite-apply} goal.
 *
 * <p>This goal runs the configured OpenRewrite recipe and applies the resulting JSpecify nullness
 * annotations to the project source files. It is a thin variant of {@link RewriteHintMojo} with
 * apply mode forced on.
 */
@Mojo(name = "rewrite-apply", threadSafe = true, requiresProject = true)
public class RewriteApplyMojo extends RewriteHintMojo {
    /**
     * Creates a mojo that applies rewrites to source files.
     */
    public RewriteApplyMojo() {
        super(true);
    }
}
