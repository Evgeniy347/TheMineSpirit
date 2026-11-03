package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class GraphMore4FragmentsTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();
    private static final List<String> EVENTS = List.of(
            More4Rules.PLAYER_NEAR,
            More4Rules.PLAYER_HUNGRY,
            More4Rules.PLAYER_TOOL_BROKEN,
            More4Rules.WRONG_TOOL_HAND,
            More4Rules.PORTAL_FOUND,
            More4Rules.PORTAL_UNSAFE,
            More4Rules.SPACE_FOUND,
            More4Rules.SPACE_TIGHT,
            More4Rules.TAMED_PET,
            More4Rules.RARE_FOUND,
            More4Rules.RARE_MIXED,
            More4Rules.CAUTION);
    private static final List<String> WAYS = List.of(
            "more4_guard_settlement",
            "more4_watch_chests",
            "more4_bring_player_food",
            "more4_lend_player_tool",
            "more4_switch_tool",
            "more4_guard_portal",
            "more4_fence_portal",
            "more4_plan_expansion",
            "more4_find_space",
            "more4_guard_with_pet",
            "more4_leave_pet_alone",
            "more4_store_rare",
            "more4_spare_rare",
            "more4_sort_rare",
            "more4_feed_hungry",
            "more4_rest_injured");

    @Test
    void more4Events_FragmentLoad_AreDeclared() {
        for (String event : EVENTS) {
            assertTrue(GRAPH.events().containsKey(event), "Событие входа области graphmore4 объявлено: " + event);
            assertFalse(GRAPH.events().get(event).tasks().isEmpty(), "Событие ставит работу: " + event);
        }
    }

    @Test
    void more4Ways_FragmentLoad_AreStubbedWithTodo() {
        for (String way : WAYS) {
            SupplyGraph.Way known = GRAPH.way(way).orElseThrow();
            assertTrue(known.weight() > 0, "Стартовый вес пути положителен: " + way);
            assertFalse(GRAPH.implemented(way), "Действия пока нет — путь заглушён: " + way);
        }
    }

    @Test
    void more4Items_FragmentLoad_HavePositiveStartWeights() {
        for (String item : List.of(
                "more4:settlement_guarded",
                "more4:player_helped",
                "more4:tool_ready",
                "more4:portal_guarded",
                "more4:expansion_planned",
                "more4:pet_guarded",
                "more4:rare_kept",
                "more4:caution")) {
            assertTrue(GRAPH.items().get(item).base() > 0, "Стартовый вес предмета положителен: " + item);
        }
    }

    @Test
    void rareGroup_More4Fragment_IsDeclared() {
        assertEquals(
                List.of(
                        "diamond",
                        "emerald",
                        "netherite_ingot",
                        "netherite_scrap",
                        "ancient_debris",
                        "totem_of_undying",
                        "echo_shard",
                        "dragon_breath",
                        "nether_star",
                        "amethyst_shard",
                        "blaze_rod",
                        "ender_pearl",
                        "obsidian"),
                GRAPH.groups().get(More4Rules.RARE),
                "Группа редких предметов объявлена один раз и в своём файле");
    }

    @Test
    void more4Events_FragmentLoad_PushAndConsumeKnownNodes() {
        for (String event : EVENTS) {
            GraphEvent known = GRAPH.events().get(event);
            known.push()
                    .keySet()
                    .forEach(
                            node -> assertTrue(known(node), "Событие " + event + " толкает неизвестный узел: " + node));
            known.tasks()
                    .forEach(task -> task.consumes()
                            .keySet()
                            .forEach(item ->
                                    assertTrue(known(item), "Событие " + event + " расходует неизвестное " + item)));
        }
    }

    @Test
    void more4EventTasks_EveryTask_PointsAtStubWay() {
        for (String event : EVENTS) {
            for (GraphEvent.Task task : GRAPH.events().get(event).tasks()) {
                assertTrue(
                        GRAPH.way(task.id()).isPresent(),
                        "Задача " + event + " -> " + task.id() + " должна быть путём");
                assertFalse(task.implemented(), "Задача " + event + " -> " + task.id() + " ещё не реализована");
            }
        }
    }

    @Test
    void more4EventNames_AllEvents_UseAreaPrefix() {
        assertEquals(EVENTS.size(), EVENTS.stream().distinct().count(), "Имена событий области не повторяются");
        for (String event : EVENTS) {
            assertTrue(event.startsWith("more4_"), "Событие области названо с префиксом more4_: " + event);
        }
    }

    private static boolean known(String node) {
        if (node.startsWith("building:")) {
            return GRAPH.buildings().containsKey(node.substring("building:".length()));
        }
        return GRAPH.items().containsKey(node) || GRAPH.isGroup(node) || GRAPH.isTool(node);
    }
}
