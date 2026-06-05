package io.github.javamodernizationlabs.jspecify;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregated counts of legacy nullness annotations encountered in a project.
 * Each entry pairs the source file path with the line where the annotation
 * was referenced. The map preserves insertion order so reports are stable.
 *
 * @param totalByAnnotation count of usages keyed by annotation fully qualified name
 * @param locationsByAnnotation source locations of each usage keyed by annotation name
 * @param filesScanned the number of source files examined
 */
public record AnnotationInventory(
        Map<String, Integer> totalByAnnotation,
        Map<String, List<Location>> locationsByAnnotation,
        int filesScanned
) {
    /**
     * Canonical constructor that defensively copies the maps and substitutes empty
     * maps for {@code null} arguments.
     */
    public AnnotationInventory {
        totalByAnnotation = Collections.unmodifiableMap(totalByAnnotation == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(totalByAnnotation));
        Map<String, List<Location>> copiedLocations = new LinkedHashMap<>();
        if (locationsByAnnotation != null) {
            locationsByAnnotation.forEach((annotation, locations) ->
                    copiedLocations.put(annotation, locations == null
                            ? List.of()
                            : List.copyOf(locations)));
        }
        locationsByAnnotation = Collections.unmodifiableMap(copiedLocations);
    }

    /**
     * Returns an empty inventory with no recorded annotations and zero files scanned.
     *
     * @return an empty inventory instance
     */
    public static AnnotationInventory empty() {
        return new AnnotationInventory(new LinkedHashMap<>(), new LinkedHashMap<>(), 0);
    }

    /**
     * Returns the total number of annotation usages across all annotations.
     *
     * @return the sum of all per-annotation usage counts
     */
    public int totalAnnotations() {
        return totalByAnnotation.values().stream().mapToInt(Integer::intValue).sum();
    }
}
