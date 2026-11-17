package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GearUnreachableChestTests {
    @Test
    void events_UnreachableChestWithPickaxe_CraftsInsteadOfEquip() {
        Set<String> events = GearInputs.events(
                facts(
                        Map.of("spirit-1", spirit("mining", List.of())),
                        List.of(unreachableChest(Map.of("minecraft:stone_pickaxe", 1)))),
                1);

        assertTrue(
                events.contains(GearInputs.CRAFT_TOOL + "pickaxe"),
                "Кирка в сундуке без пути не закрывает нужду: иначе шахта падает с «нужна кирка», а крафт "
                        + "не ставится");
        assertFalse(
                events.contains(GearInputs.EQUIP_BEST),
                "Сундук без пути не годится для смены инструмента: дух ходит к нему вхолостую");
    }

    @Test
    void equipUpgrade_UnreachableChest_Skipped() {
        GraphFacts.Spirit spirit = spirit(null, List.of(tool("pickaxe")));
        GraphFacts.Chest chest = unreachableChest(Map.of("minecraft:iron_pickaxe", 1));

        assertTrue(
                GearRules.equipUpgrade(spirit, List.of(chest), GearInputs.settings())
                        .isEmpty(),
                "Обновка из недостижимого сундука не выбирается: до неё не дойти");
    }

    @Test
    void chestToolRanks_UnreachableChest_Ignored() {
        Map<String, Integer> ranks = GearRules.chestToolRanks(
                List.of(unreachableChest(Map.of("minecraft:stone_pickaxe", 1))), GearInputs.settings());

        assertTrue(ranks.isEmpty(), "Ранги инструментов считаются только по достижимым сундукам");
    }

    private static GraphFacts.Spirit spirit(String activity, List<GraphFacts.Tool> tools) {
        return new GraphFacts.Spirit(
                "spirit-1",
                new GraphFacts.Position(0, 64, 0),
                20,
                20,
                null,
                18,
                300,
                false,
                false,
                false,
                activity,
                Map.of(),
                List.of(),
                tools,
                null);
    }

    private static GraphFacts.Tool tool(String kind) {
        return new GraphFacts.Tool("minecraft:tool_" + kind, kind, 1, 131, 131);
    }

    private static GraphFacts.Chest unreachableChest(Map<String, Integer> contents) {
        return new GraphFacts.Chest(new GraphFacts.Position(0, 92, 0), contents, 10, 27, GraphFacts.Reach.NO_PATH);
    }

    private static GraphFacts facts(Map<String, GraphFacts.Spirit> spirits, List<GraphFacts.Chest> chests) {
        return new GraphFacts(
                new LinkedHashMap<>(spirits),
                List.of(),
                chests,
                List.of(),
                Map.of(),
                Map.of(),
                List.of(),
                GraphSiteFacts.Terrain.empty(),
                new GraphSiteFacts.StageReqs(Map.of(), Map.of()),
                GraphFeedback.none(),
                List.of(),
                List.of(),
                List.of());
    }
}
