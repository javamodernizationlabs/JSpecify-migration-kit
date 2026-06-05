/**
 * Verification of how a project's Java public API behaves when consumed from
 * Kotlin under JSpecify nullness contracts.
 *
 * <p>The verifier generates Kotlin samples that exercise the API, flags
 * platform-type leaks where nullness is unspecified, and can optionally compile
 * the samples with {@code kotlinc}.
 */
package io.github.javamodernizationlabs.jspecify.kotlin;
