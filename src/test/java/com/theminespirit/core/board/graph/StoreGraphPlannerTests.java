package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class StoreGraphPlannerTests {
    private static final GraphFacts.Position AT = new GraphFacts.Position(1, 64, 2);

    @Test
    void storeMinimum_Deficit_AddsStoreNodeAndGathersLogs() {
        GraphPlan plan = GraphWorld.settled()
                .facts(facts(new GraphFacts.StageReqs(Map.of("oak_log", 64), Map.of("oak_log", 0))))
                .plan();

        assertTrue(ids(plan).contains("store:minimum"), "Недобор минимума склада — отдельный узел графа");
        assertTrue(ids(plan).contains("chop_oak_log"), "Недобор минимума выводит добычу брёвен");
    }

    @Test
    void storeMinimum_Satisfied_LeavesStoreNodeOut() {
        GraphPlan plan = GraphWorld.settled()
                .facts(facts(new GraphFacts.StageReqs(Map.of("oak_log", 64), Map.of("oak_log", 64))))
                .plan();

        assertFalse(ids(plan).contains("store:minimum"), "Закрытый минимум не создаёт задание склада");
    }

    @Test
    void storeMinimum_MissingIronAxe_AddsCraftPath() {
        GraphWorld world = GraphWorld.settled().with("iron_ingot", 8).with("stick", 8);
        world.stage = 2;
        GraphPlan plan = world.facts(facts(new GraphFacts.StageReqs(Map.of("iron_axe", 1), Map.of("iron_axe", 0))))
                .plan();

        assertTrue(ids(plan).contains("craft_iron_axe"), "Минимум железного топора ведёт к его крафту");
    }

    @Test
    void carry_SurplusAndFreeChest_AddsTransferNode() {
        GraphFacts facts = facts(
                new GraphFacts.StageReqs(Map.of(), Map.of()),
                List.of(new GraphFacts.Chest(AT, Map.of(), 10, 27)),
                Map.of("spirit-1", spirit(false, null, Map.of("minecraft:cobblestone", 12))),
                Map.of());
        GraphPlan plan = GraphWorld.settled().facts(facts).plan();

        assertTrue(ids(plan).contains("transfer/carry"), "Несомое и свободный сундук дают узел переноса");
    }

    @Test
    void smelt_LoadedAndReady_TakesTwoSeparatePaths() {
        GraphFacts facts = facts(
                new GraphFacts.StageReqs(Map.of(), Map.of()),
                List.of(),
                Map.of(),
                Map.of(
                        "furnace-load",
                        new GraphFacts.Station("furnace-load", "furnace", AT, false, "coal", "iron_ore", null),
                        "furnace-ready",
                        new GraphFacts.Station("furnace-ready", "furnace", AT, true, "coal", null, "iron_ingot")));
        GraphPlan plan = GraphWorld.settled().facts(facts).plan();

        assertTrue(ids(plan).contains("station:smelt:load"), "Загрузка печи — отдельный путь");
        assertTrue(ids(plan).contains("station:smelt:take"), "Забрать готовое — отдельный путь");
    }

    @Test
    void storageSpace_LowFreeSlots_AddsChestsNode() {
        GraphFacts facts = facts(
                new GraphFacts.StageReqs(Map.of(), Map.of()),
                List.of(new GraphFacts.Chest(AT, Map.of("oak_log", 8), 1, 27)),
                Map.of(),
                Map.of());
        GraphPlan plan = GraphWorld.settled().facts(facts).plan();

        assertTrue(ids(plan).contains("storage_space"), "Мало места в сундуках — узел новой пары сундуков");
    }

    @Test
    void sort_MixedChest_AddsSortNode() {
        Map<String, Integer> mixed = new HashMap<>();
        for (int index = 0; index < 9; index++) {
            mixed.put("item" + index, 1);
        }
        GraphFacts facts = facts(
                new GraphFacts.StageReqs(Map.of(), Map.of()),
                List.of(new GraphFacts.Chest(AT, mixed, 1, 27)),
                Map.of(),
                Map.of());
        GraphPlan plan = GraphWorld.settled().facts(facts).plan();

        assertTrue(ids(plan).contains("sort"), "Смешанный сундук — узел сортировки");
    }

    @Test
    void digOut_SealedSpirit_AddsDigAndRestore() {
        GraphPlan plan = GraphWorld.settled()
                .facts(facts(
                        new GraphFacts.StageReqs(Map.of(), Map.of()),
                        List.of(),
                        Map.of("spirit-1", spirit(true, "sealed", Map.of())),
                        Map.of()))
                .plan();

        assertTrue(ids(plan).contains("dig_out"), "Замурованный дух получает узел dig_out");
        assertTrue(ids(plan).contains("restore_trace"), "После прохода обязателен restore_trace");
        assertFalse(ids(plan).contains("pillar"), "Для замурованного не выбирают столб");
        assertFalse(ids(plan).contains("bridge"), "Для замурованного не выбирают мост");
    }

    @Test
    void digOut_PitSpirit_AddsPillarAndRestore() {
        GraphPlan plan = GraphWorld.settled()
                .facts(facts(
                        new GraphFacts.StageReqs(Map.of(), Map.of()),
                        List.of(),
                        Map.of("spirit-1", spirit(true, "pit", Map.of())),
                        Map.of()))
                .plan();

        assertTrue(ids(plan).contains("pillar"), "Из ямы выбирают подъём столбом");
        assertTrue(ids(plan).contains("restore_trace"), "Подъём из ямы тоже возвращает след");
        assertFalse(ids(plan).contains("dig_out"), "Для ямы копание не выбирают");
    }

    @Test
    void digOut_WaterSpirit_AddsBridgeAndRestore() {
        GraphPlan plan = GraphWorld.settled()
                .facts(facts(
                        new GraphFacts.StageReqs(Map.of(), Map.of()),
                        List.of(),
                        Map.of("spirit-1", spirit(true, "water", Map.of())),
                        Map.of()))
                .plan();

        assertTrue(ids(plan).contains("bridge"), "Через воду выбирают мост");
        assertTrue(ids(plan).contains("restore_trace"), "Мост после прохода разбирают");
    }

    @Test
    void digOut_UntrappedSpirit_NoDigNodes() {
        GraphPlan plan = GraphWorld.settled()
                .facts(facts(
                        new GraphFacts.StageReqs(Map.of(), Map.of()),
                        List.of(),
                        Map.of("spirit-1", spirit(false, null, Map.of())),
                        Map.of()))
                .plan();

        assertFalse(ids(plan).contains("dig_out"), "Свободный дух не копает");
        assertFalse(ids(plan).contains("restore_trace"), "Без прохода нечего восстанавливать");
    }

    private static Set<String> ids(GraphPlan plan) {
        return plan.tasks().stream().map(GraphPlan.Task::id).collect(Collectors.toSet());
    }

    private static GraphFacts facts(GraphFacts.StageReqs reqs) {
        return facts(reqs, List.of(), Map.of(), Map.of());
    }

    private static GraphFacts facts(
            GraphFacts.StageReqs reqs,
            List<GraphFacts.Chest> chests,
            Map<String, GraphFacts.Spirit> spirits,
            Map<String, GraphFacts.Station> stations) {
        return new GraphFacts(
                spirits, List.of(), chests, List.of(), stations, Map.of(), List.of(), GraphFacts.Terrain.empty(), reqs);
    }

    private static GraphFacts.Spirit spirit(boolean trapped, String trap, Map<String, Integer> inventory) {
        return new GraphFacts.Spirit(
                "spirit-1", AT, 20, 20, 300, false, trapped, false, null, inventory, List.of(), List.of(), trap);
    }
}
