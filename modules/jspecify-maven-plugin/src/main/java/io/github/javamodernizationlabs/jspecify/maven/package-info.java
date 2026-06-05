/**
 * Maven plugin goals for the JSpecify Migration Kit.
 *
 * <p>This package contains the Maven mojos that expose the migration kit as the {@code jspecify}
 * plugin. Each mojo wraps a piece of the core migration toolchain and writes its output under the
 * project's configured report directory. The available goals are:
 *
 * <ul>
 *   <li>{@code jspecify-migration:plan} ({@link io.github.javamodernizationlabs.jspecify.maven.PlanMojo}) —
 *       scans the project, builds a migration plan, prints a console summary, and writes the plan in
 *       several report formats.</li>
 *   <li>{@code jspecify-migration:report} ({@link io.github.javamodernizationlabs.jspecify.maven.ReportMojo})
 *       — writes the migration plan reports without a console summary.</li>
 *   <li>{@code jspecify-migration:coverage}
 *       ({@link io.github.javamodernizationlabs.jspecify.maven.CoverageMojo}) — reports JSpecify
 *       nullness annotation coverage for the project.</li>
 *   <li>{@code jspecify-migration:nullaway-check}
 *       ({@link io.github.javamodernizationlabs.jspecify.maven.NullAwayCheckMojo}) — verifies the
 *       Error Prone and NullAway compiler setup and emits a ready-to-use configuration snippet.</li>
 *   <li>{@code jspecify-migration:rewrite-hint}
 *       ({@link io.github.javamodernizationlabs.jspecify.maven.RewriteHintMojo}),
 *       {@code jspecify-migration:rewrite-dry-run}
 *       ({@link io.github.javamodernizationlabs.jspecify.maven.RewriteDryRunMojo}), and
 *       {@code jspecify-migration:rewrite-apply}
 *       ({@link io.github.javamodernizationlabs.jspecify.maven.RewriteApplyMojo}) — run the
 *       OpenRewrite recipe to add JSpecify nullness annotations, either as a dry run or applied to
 *       source files.</li>
 *   <li>{@code jspecify-migration:verify-kotlin}
 *       ({@link io.github.javamodernizationlabs.jspecify.maven.VerifyKotlinMojo}) — verifies how the
 *       project's nullness annotations are seen from Kotlin.</li>
 * </ul>
 */
package io.github.javamodernizationlabs.jspecify.maven;
