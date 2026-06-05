package io.github.javamodernizationlabs.jspecify;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MigrationPlannerTest {

    @Test
    void riskScalesWithVolumeAndDiversity() {
        var counts = new LinkedHashMap<String, Integer>();
        var locs = new LinkedHashMap<String, List<Location>>();
        AnnotationInventory empty = new AnnotationInventory(counts, locs, 0);
        assertEquals(MigrationPlan.Risk.LOW, new MigrationPlanner().plan(empty).estimatedRisk());

        counts.put("org.jetbrains.annotations.Nullable", 10);
        AnnotationInventory small = new AnnotationInventory(counts, locs, 1);
        assertEquals(MigrationPlan.Risk.LOW, new MigrationPlanner().plan(small).estimatedRisk());

        counts.put("javax.annotation.Nullable", 40);
        counts.put("org.springframework.lang.Nullable", 5);
        AnnotationInventory medium = new AnnotationInventory(counts, locs, 1);
        assertEquals(MigrationPlan.Risk.MEDIUM,
                new MigrationPlanner().plan(medium).estimatedRisk());

        counts.put("edu.umd.cs.findbugs.annotations.Nullable", 200);
        AnnotationInventory high = new AnnotationInventory(counts, locs, 1);
        assertEquals(MigrationPlan.Risk.HIGH, new MigrationPlanner().plan(high).estimatedRisk());
    }

    @Test
    void planHasSixPhases() {
        AnnotationInventory empty = AnnotationInventory.empty();
        var plan = new MigrationPlanner().plan(empty);
        assertEquals(6, plan.phases().size());
        assertFalse(plan.phases().get(0).commands().isEmpty());
    }

    @Test
    void jspecifyAnnotationsDoNotIncreaseRisk() {
        var counts = new LinkedHashMap<String, Integer>();
        counts.put("org.jspecify.annotations.NullMarked", 500);
        AnnotationInventory inventory = new AnnotationInventory(counts, new LinkedHashMap<>(), 1);

        assertEquals(MigrationPlan.Risk.LOW,
                new MigrationPlanner().plan(inventory).estimatedRisk());
    }

    @Test
    void annotationInventoryDoesNotExposeMutableMaps() {
        AnnotationInventory inventory = AnnotationInventory.empty();

        assertThrows(UnsupportedOperationException.class,
                () -> inventory.totalByAnnotation().put("x", 1));
        assertThrows(UnsupportedOperationException.class,
                () -> inventory.locationsByAnnotation().put("x", List.of()));
    }
}
