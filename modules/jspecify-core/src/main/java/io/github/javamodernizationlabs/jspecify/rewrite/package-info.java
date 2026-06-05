/**
 * Source and build-file rewrites for adopting JSpecify.
 *
 * <p>This package contains {@link io.github.javamodernizationlabs.jspecify.rewrite.JspecifyRewriter},
 * which applies migration recipes such as adding the JSpecify dependency, converting known
 * legacy annotations, and adding package-level {@code @NullMarked}. Each run produces a
 * {@link io.github.javamodernizationlabs.jspecify.rewrite.RewriteResult} aggregating the
 * individual {@link io.github.javamodernizationlabs.jspecify.rewrite.RewriteChange} entries and
 * any warnings, and may be executed as a preview or applied to disk.</p>
 */
package io.github.javamodernizationlabs.jspecify.rewrite;
