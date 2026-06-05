/**
 * Command line interface for the JSpecify Migration Kit.
 *
 * <p>This package contains the picocli commands that make up the {@code jml jspecify}
 * toolchain. {@link io.github.javamodernizationlabs.jspecify.cli.JspecifyCli} is the
 * top-level {@code jml} entry point; it groups the JSpecify Migration Kit commands under the
 * {@code jspecify} subcommand.</p>
 *
 * <p>The migration workflow commands are:</p>
 * <ul>
 *   <li>{@link io.github.javamodernizationlabs.jspecify.cli.PlanCommand} - inventory legacy
 *       nullness annotations and emit a migration plan, with optional baseline gating;</li>
 *   <li>{@link io.github.javamodernizationlabs.jspecify.cli.RewriteCommand} - apply JSpecify
 *       migration recipes, either as a dry run or in place;</li>
 *   <li>{@link io.github.javamodernizationlabs.jspecify.cli.CoverageCommand} - report public
 *       API nullness coverage;</li>
 *   <li>{@link io.github.javamodernizationlabs.jspecify.cli.NullAwayConfigCommand} - generate
 *       NullAway and Error Prone configuration snippets;</li>
 *   <li>{@link io.github.javamodernizationlabs.jspecify.cli.VerifyKotlinCommand} - generate
 *       and optionally compile Kotlin interop verification artifacts;</li>
 *   <li>{@link io.github.javamodernizationlabs.jspecify.cli.ReportCommand} - emit the full set
 *       of migration reports;</li>
 *   <li>{@link io.github.javamodernizationlabs.jspecify.cli.ExplainCommand} - explain a
 *       JSpecify Migration Kit rule id.</li>
 * </ul>
 *
 * <p>Each command implements {@code Callable<Integer>} (or {@code Runnable} for the grouping
 * commands) and returns a process exit code where {@code 0} indicates success.</p>
 */
package io.github.javamodernizationlabs.jspecify.cli;
