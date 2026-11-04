package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class GraphMore3FragmentsTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();
    private static final List<String> EVENTS = List.of(
            "danger_spot",
            "spirit_death_drop",
            "work_yield_low",
            "trash_found",
            "chest_purpose_missing",
            "work_conflict",
            "station_idle",
            "player_sleep",
            "sand_collapse",
            "gravel_collapse",
            "farmland_trampled",
            "world_decay",
            "stage_slow");
    private static final List<String> WAYS = List.of(
            "secure_danger",
            "recover_death_drop",
            "retune_work_cost",
            "clear_trash",
            "assign_chest_purpose",
            "spread_work",
            "use_idle_station",
            "home_night_work",
            "raise_bottleneck_weight");

    @Test
    void more3Events_FragmentLoad_AreDeclared() {
        for (String event : EVENTS) {
            assertTrue(GRAPH.events().containsKey(event), "Событие входа области more3 объявлено: " + event);
            assertFalse(GRAPH.events().get(event).tasks().isEmpty(), "Событие ставит работу: " + event);
        }
    }

    @Test
    void more3Ways_FragmentLoad_ImplementedAndDeferred() {
        for (String way : WAYS) {
            SupplyGraph.Way known = GRAPH.way(way).orElseThrow();
            assertTrue(known.weight() > 0, "Стартовый вес пути положителен: " + way);
            boolean live = List.of("secure_danger", "recover_death_drop", "clear_trash", "assign_chest_purpose")
                    .contains(way);
            assertEquals(live, GRAPH.implemented(way), "Состояние действия пути: " + way);
        }
    }

    @Test
    void more3Items_FragmentLoad_HavePositiveStartWeights() {
        for (String item : List.of(
                "danger_known",
                "drop_recovered",
                "work_retuned",
                "trash_cleared",
                "chest_labeled",
                "conflict_resolved",
                "station_loaded",
                "night_home",
                "world_repaired",
                "stage_boosted")) {
            assertTrue(GRAPH.items().get(item).base() > 0, "Стартовый вес предмета положителен: " + item);
        }
    }

    @Test
    void trashGroup_More3Fragment_IsDeclared() {
        assertEquals(
                List.of("rotten_flesh", "poisonous_potato", "spider_eye", "bone", "string", "flint", "gravel"),
                GRAPH.groups().get(More3Rules.TRASH),
                "Группа мусора объявлена один раз и в своём файле");
    }

    @Test
    void more3Events_FragmentLoad_PushKnownItems() {
        for (String event : EVENTS) {
            GraphEvent known = GRAPH.events().get(event);
            known.push()
                    .keySet()
                    .forEach(node -> assertTrue(
                            GRAPH.items().containsKey(node) || GRAPH.isGroup(node),
                            "Событие " + event + " толкает неизвестный узел: " + node));
        }
    }
}
