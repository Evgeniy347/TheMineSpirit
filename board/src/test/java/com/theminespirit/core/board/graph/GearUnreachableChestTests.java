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
                        + "не ставится"
                        + " При смене events_UnreachableChestWithPickaxe_CraftsInsteadOfEquip проверь это утверждение.");
        assertFalse(
                events.contains(GearInputs.EQUIP_BEST),
                "Сундук без пути не годится для смены инструмента: дух ходит к нему вхолостую"
                        + " Иначе тест events_UnreachableChestWithPickaxe_CraftsInsteadOfEquip перестанет ловить эту поломку. При смене events_UnreachableChestWithPickaxe_CraftsInsteadOfEquip проверь это утверждение.");
    }

    @Test
    void events_RemoteChestWithPickaxe_EquipsInsteadOfCraft() {
        Set<String> events = GearInputs.events(
                facts(
                        Map.of("spirit-1", spirit("mining", List.of())),
                        List.of(remoteChest(Map.of("minecraft:stone_pickaxe", 1)))),
                1);

        assertTrue(
                events.contains(GearInputs.EQUIP_BEST),
                "Кирка в сундуке с дистанционным получением закрывает нужду: иначе граф ставит лишний крафт "
                        + "при готовом инструменте. При смене events_RemoteChestWithPickaxe_EquipsInsteadOfCraft "
                        + "проверь GraphFacts.Chest.retrievable и живой сундук.");
        assertFalse(
                events.contains(GearInputs.CRAFT_TOOL + "pickaxe"),
                "Готовый дистанционный инструмент отменяет крафт: иначе дух тратит ресурсы на дубликат. "
                        + "При смене events_RemoteChestWithPickaxe_EquipsInsteadOfCraft проверь GearInputs.events.");
    }

    @Test
    void equipUpgrade_RemoteChest_Selected() {
        GraphFacts.Spirit spirit = spirit(null, List.of(tool("pickaxe")));
        GraphFacts.Chest chest = remoteChest(Map.of("minecraft:iron_pickaxe", 1));

        assertTrue(
                GearRules.equipUpgrade(spirit, List.of(chest), GearInputs.settings())
                        .isPresent(),
                "Обновка из удалённого сундука выбирается: получить её можно без физического пути. Иначе "
                        + "граф заказывает лишний крафт при готовом инструменте. При смене "
                        + "equipUpgrade_RemoteChest_Selected проверь GearRules.equipUpgrade и ActivityPlans.equip.");
    }

    @Test
    void chestToolRanks_RemoteChest_Counted() {
        Map<String, Integer> ranks = GearRules.chestToolRanks(
                List.of(remoteChest(Map.of("minecraft:stone_pickaxe", 1))), GearInputs.settings());

        assertTrue(
                ranks.getOrDefault("pickaxe", -1) >= 0,
                "Ранг инструмента в удалённом сундуке учитывается: иначе граф не видит готового инструмента. "
                        + "При смене chestToolRanks_RemoteChest_Counted проверь GearRules.chestToolRanks.");
    }

    @Test
    void equipUpgrade_UnreachableChest_Skipped() {
        GraphFacts.Spirit spirit = spirit(null, List.of(tool("pickaxe")));
        GraphFacts.Chest chest = unreachableChest(Map.of("minecraft:iron_pickaxe", 1));

        assertTrue(
                GearRules.equipUpgrade(spirit, List.of(chest), GearInputs.settings())
                        .isEmpty(),
                "Обновка из недостижимого сундука не выбирается: до неё не дойти"
                        + " Иначе тест equipUpgrade_UnreachableChest_Skipped перестанет ловить эту поломку. При смене equipUpgrade_UnreachableChest_Skipped проверь это утверждение.");
    }

    @Test
    void chestToolRanks_UnreachableChest_Ignored() {
        Map<String, Integer> ranks = GearRules.chestToolRanks(
                List.of(unreachableChest(Map.of("minecraft:stone_pickaxe", 1))), GearInputs.settings());

        assertTrue(
                ranks.isEmpty(),
                "Ранги инструментов считаются только по достижимым сундукам"
                        + " Иначе тест chestToolRanks_UnreachableChest_Ignored перестанет ловить эту поломку. При смене chestToolRanks_UnreachableChest_Ignored проверь это утверждение.");
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

    private static GraphFacts.Chest remoteChest(Map<String, Integer> contents) {
        return new GraphFacts.Chest(
                new GraphFacts.Position(0, 92, 0), contents, 10, 27, GraphFacts.Reach.NO_PATH, true);
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
                List.of());
    }
}
