package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StoreFactsTests {
    private static final GraphFacts.Position AT = new GraphFacts.Position(1, 64, 2);

    @Test
    void minimums_Stages_AccumulateUntilCurrent() {
        StoreFacts.Minimums minimums = StoreFacts.parse(
                "{\"store\":{\"reservePercent\":10,\"minimum\":{\"1\":{\"oak_log\":10},\"2\":{\"bread\":4}}}}",
                "store.json");

        assertEquals(Map.of("oak_log", 10), minimums.targets(1), "Первый этап берёт только свой минимум");
        assertEquals(
                Map.of("oak_log", 10, "bread", 4),
                minimums.targets(2),
                "Второй этап сохраняет минимум первого и добавляет свой");
    }

    @Test
    void minimums_ReserveOutsideRange_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> StoreFacts.Minimums.of(Map.of("oak_log", 10), 101),
                "Резерв больше 100% сделал бы запас бесконечным: допустим только 0..100");
    }

    @Test
    void stageReqs_GroupToolAndReserve_CountedFromChestsAndSpirits() {
        StoreFacts.Minimums minimums = StoreFacts.Minimums.of(Map.of("#logs", 64, "iron_axe", 1), 25);
        List<GraphFacts.Chest> chests = List.of(new GraphFacts.Chest(AT, Map.of("minecraft:oak_log", 10), 20, 27));
        Map<String, GraphFacts.Spirit> spirits = Map.of(
                "spirit-1",
                spirit(
                        Map.of("minecraft:birch_log", 5),
                        List.of(new GraphFacts.Tool("minecraft:iron_axe", "axe", 2, 200, 250))));

        GraphFacts.StageReqs reqs = StoreFacts.stageReqs(GraphWorld.GRAPH, minimums, 1, chests, spirits);

        assertEquals(80, reqs.minimum().get("#logs"), "Резерв 25% добавляет 16 к минимуму брёвен 64");
        assertEquals(15, reqs.actual().get("#logs"), "Факт группы складывается из сундука и инвентаря");
        assertEquals(1, reqs.minimum().get("iron_axe"), "Инструмент не получает резерв: он персональный");
        assertEquals(1, reqs.actual().get("iron_axe"), "Железный топор духа закрывает минимум инструмента");
    }

    @Test
    void stageReqs_WoodenTool_DoesNotCloseIronMinimum() {
        StoreFacts.Minimums minimums = StoreFacts.Minimums.of(Map.of("iron_pickaxe", 1), 25);
        Map<String, GraphFacts.Spirit> spirits = Map.of(
                "spirit-1",
                spirit(Map.of(), List.of(new GraphFacts.Tool("minecraft:stone_pickaxe", "pickaxe", 1, 100, 131))));

        GraphFacts.StageReqs reqs = StoreFacts.stageReqs(GraphWorld.GRAPH, minimums, 1, List.of(), spirits);

        assertEquals(0, reqs.actual().get("iron_pickaxe"), "Каменная кирка не заменяет железную");
    }

    @Test
    void carry_SurplusInInventory_NeedsFreeChest() {
        List<GraphFacts.Chest> chests = List.of(new GraphFacts.Chest(AT, Map.of(), 1, 27));
        Map<String, GraphFacts.Spirit> spirits =
                Map.of("spirit-1", spirit(Map.of("minecraft:cobblestone", 10), List.of()));

        assertTrue(StoreFacts.carry(chests, spirits, 8), "Несомое сверх порога надо сдать в свободный сундук");
        assertFalse(
                StoreFacts.carry(List.of(new GraphFacts.Chest(AT, Map.of("oak_log", 1), 0, 27)), spirits, 8),
                "Без свободного места нести некуда");
    }

    @Test
    void relocate_TwoChestsWithRoom_MovesContents() {
        List<GraphFacts.Chest> chests = List.of(
                new GraphFacts.Chest(AT, Map.of("oak_log", 8), 0, 27),
                new GraphFacts.Chest(new GraphFacts.Position(2, 64, 2), Map.of(), 27, 27));

        assertTrue(StoreFacts.relocate(chests), "Второй сундук пуст и может принять содержимое первого");
        assertFalse(StoreFacts.relocate(List.of(chests.get(0))), "Один сундук некуда переносить");
    }

    @Test
    void mixed_TooManyTypes_AsksSorting() {
        Map<String, Integer> mixed = new HashMap<>();
        for (int index = 0; index < 9; index++) {
            mixed.put("item" + index, 1);
        }

        assertTrue(
                StoreFacts.mixed(List.of(new GraphFacts.Chest(AT, mixed, 1, 27))),
                "Девять типов в одном сундуке — повод разложить по типам");
        assertFalse(
                StoreFacts.mixed(List.of(new GraphFacts.Chest(AT, Map.of("oak_log", 8), 1, 27))),
                "Один тип хранится нормально");
    }

    @Test
    void space_LowFreeSlots_AsksNewChests() {
        assertTrue(
                StoreFacts.space(List.of(new GraphFacts.Chest(AT, Map.of("oak_log", 8), 1, 27)), 20),
                "Один свободный слот из 27 — меньше порога 20%");
        assertFalse(
                StoreFacts.space(List.of(new GraphFacts.Chest(AT, Map.of(), 27, 27)), 20),
                "Полностью свободный сундук места не требует");
    }

    @Test
    void smelt_LoadAndTake_FollowFurnaceContents() {
        GraphFacts.Station loading = station(false, "coal", "iron_ore", null);
        GraphFacts.Station working = station(true, "coal", "iron_ore", null);
        GraphFacts.Station ready = station(true, "coal", null, "iron_ingot");

        assertTrue(StoreFacts.smeltLoad(loading), "Свободная печь с топливом и рудой принимает загрузку");
        assertFalse(StoreFacts.smeltLoad(working), "Занятую печь не загружают повторно");
        assertTrue(StoreFacts.smeltTake(ready), "Готовый выход забирают отдельным путём");
        assertFalse(StoreFacts.smeltTake(loading), "Из пустой печи забирать нечего");
    }

    private static GraphFacts.Station station(boolean busy, String fuel, String input, String output) {
        return new GraphFacts.Station("furnace", "furnace", AT, busy, fuel, input, output);
    }

    private static GraphFacts.Spirit spirit(Map<String, Integer> inventory, List<GraphFacts.Tool> tools) {
        return new GraphFacts.Spirit(
                "spirit-1", AT, 20, 20, 300, false, false, false, null, inventory, List.of(), tools, null);
    }
}
