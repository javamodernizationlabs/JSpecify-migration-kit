/**
 * Core domain model for the JSpecify Migration Kit.
 *
 * <p>This package defines the shared value types used across the toolchain: the
 * {@link io.github.javamodernizationlabs.jspecify.AnnotationCatalog} of legacy-to-JSpecify
 * annotation mappings, the {@link io.github.javamodernizationlabs.jspecify.ProjectModel}
 * describing a project to scan or rewrite, and the
 * {@link io.github.javamodernizationlabs.jspecify.AnnotationInventory} summarizing the
 * annotations found.</p>
 *
 * <p>Analysis findings are represented by {@link io.github.javamodernizationlabs.jspecify.Issue}
 * (with its {@link io.github.javamodernizationlabs.jspecify.RuleId},
 * {@link io.github.javamodernizationlabs.jspecify.Severity},
 * {@link io.github.javamodernizationlabs.jspecify.Location}, and
 * {@link io.github.javamodernizationlabs.jspecify.Recommendation}), while nullness facts are
 * captured by {@link io.github.javamodernizationlabs.jspecify.NullnessUsage},
 * {@link io.github.javamodernizationlabs.jspecify.Nullness},
 * {@link io.github.javamodernizationlabs.jspecify.ElementKind},
 * {@link io.github.javamodernizationlabs.jspecify.TypeUsePath}, and
 * {@link io.github.javamodernizationlabs.jspecify.NullnessEvidence}.</p>
 *
 * <p>The {@link io.github.javamodernizationlabs.jspecify.MigrationPlanner} turns an inventory
 * into a staged {@link io.github.javamodernizationlabs.jspecify.MigrationPlan}.</p>
 */
package io.github.javamodernizationlabs.jspecify;
