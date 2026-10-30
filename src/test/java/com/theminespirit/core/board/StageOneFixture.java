package com.theminespirit.core.board;

import java.util.List;
import java.util.Map;
import java.util.UUID;

final class StageOneFixture {
    private StageOneFixture() {}

    static void seed(BoardScenario scenario) {
        List<String> tools =
                List.of("minecraft:stone_pickaxe", "minecraft:stone_axe", "minecraft:stone_shovel", "minecraft:bread");
        for (int i = 1; i <= 3; i++) {
            scenario.spirits.add(new BoardScenario.Spirit(
                    new UUID(scenario.seed, i),
                    "spirit-" + i,
                    tools,
                    BoardScenario.FED,
                    new SpiritView(false, false, false)));
        }
        scenario.stock.putAll(Map.of(
                "oak_log", 256,
                "oak_planks", 256,
                "cobblestone", 256,
                "torch", 64,
                "oak_sapling", 16,
                "dirt", 128,
                "stick", 32,
                "coal", 32,
                "bread", 16));
        scenario.ticket("first:crafting_station:1", BoardCase.CRAFTING_STATION, List.of(), Map.of("oak_planks", 4));
        scenario.ticket("first:clear_grounds:1", BoardCase.CLEAR_GROUNDS, List.of(), Map.of("oak_planks", 8));
        scenario.ticket(
                "first:stock_chests:1",
                BoardCase.STOCK_CHESTS,
                List.of("first:clear_grounds:1"),
                Map.of("oak_planks", 16));
        scenario.ticket("first:build_mine:1", BoardCase.BUILD_MINE, List.of(), Map.of("torch", 6));
        scenario.ticket(
                "first:craft_furnace:1",
                BoardCase.CRAFT_FURNACE,
                List.of("first:stock_chests:1"),
                Map.of("cobblestone", 8));
        scenario.ticket("first:lighting:1", BoardCase.LIGHTING, List.of("first:stock_chests:1"), Map.of("torch", 8));
        scenario.ticket("first:build_fence:1", BoardCase.BUILD_FENCE, List.of(), Map.of("oak_log", 40));
        scenario.ticket(
                "first:build_house:1",
                BoardCase.BUILD_HOUSE,
                List.of(),
                Map.of("oak_log", 12, "oak_planks", 30, "cobblestone", 16));
        scenario.ticket(
                "first:build_orchard:1",
                BoardCase.BUILD_ORCHARD,
                List.of("first:build_house:1"),
                Map.of("oak_sapling", 4, "torch", 4, "dirt", 8));
        scenario.ticket(
                "first:build_streets:1", BoardCase.BUILD_STREETS, List.of("first:build_house:1"), Map.of("dirt", 40));
    }
}
