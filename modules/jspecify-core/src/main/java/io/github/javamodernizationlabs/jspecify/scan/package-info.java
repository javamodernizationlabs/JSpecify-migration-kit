/**
 * Source scanning for legacy nullness annotations.
 *
 * <p>This package contains {@link io.github.javamodernizationlabs.jspecify.scan.AnnotationScanner},
 * a text-based scanner that walks a project's Java sources and tallies usages of known
 * nullness annotations into an
 * {@link io.github.javamodernizationlabs.jspecify.AnnotationInventory}. The scanner avoids a
 * full Java parser, making it fast and tolerant of files that do not yet compile.</p>
 */
package io.github.javamodernizationlabs.jspecify.scan;
