package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;

class GraphEventsTests {
    @Test
    void merge_WorldFactsAndFarm_KeepBoth() {
        Set<String> merged = GraphEvents.merge(Set.of("backfill", "demolish_stray"), Set.of("crop_ready"));

        assertEquals(
                Set.of("backfill", "demolish_stray", "crop_ready"),
                merged,
                "вход фермы обязан добавлять свои события к событиям мира: иначе граф теряет ямы и лишнее");
    }
}
