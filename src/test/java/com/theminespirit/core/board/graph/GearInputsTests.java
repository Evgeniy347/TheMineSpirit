package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GearInputsTests {
    @Test
    void settings_ProductionFile_RaisesTierByStage() {
        GearInputs.Settings settings = GearInputs.settings();

        assertEquals("stone", settings.requiredAt(1).get("pickaxe"), "На первом этапе кирка каменная");
        assertEquals("iron", settings.requiredAt(2).get("pickaxe"), "На втором этапе кирка железная");
        assertEquals("diamond", settings.requiredAt(3).get("pickaxe"), "На третьем этапе кирка алмазная");
        assertEquals(20, settings.wornPercent(), "Инструмент считается изношенным при 20 % прочности");
    }

    @Test
    void events_WornPickaxe_AddsToolWorn() {
        Set<String> events = GearInputs.events(
                facts(Map.of("spirit-1", spirit(null, List.of(tool("pickaxe", 2, 10, 250)), List.of())), List.of()), 2);

        assertTrue(events.contains(GearInputs.TOOL_WORN), "Кирка с 4 % прочности обязана требовать замены");
    }

    @Test
    void events_FreshPickaxe_NoToolWorn() {
        Set<String> events = GearInputs.events(
                facts(Map.of("spirit-1", spirit(null, List.of(tool("pickaxe", 2, 200, 250)), List.of())), List.of()),
                2);

        assertFalse(events.contains(GearInputs.TOOL_WORN), "Кирка с 80 % прочности ещё не изношена");
    }

    @Test
    void events_WornHelmet_AddsArmorWorn() {
        Set<String> events = GearInputs.events(
                facts(
                        Map.of(
                                "spirit-1",
                                spirit(
                                        null,
                                        List.of(),
                                        List.of(new GraphFacts.Armor("HEAD", "minecraft:iron_helmet", 10, 165)))),
                        List.of()),
                2);

        assertTrue(events.contains(GearInputs.ARMOR_WORN), "Шлем с 6 % прочности обязан требовать ремонта");
    }

    @Test
    void events_EmptySpirit_AddsCraftToolNodes() {
        Set<String> events =
                GearInputs.events(facts(Map.of("spirit-1", spirit(null, List.of(), List.of())), List.of()), 1);

        assertTrue(events.contains(GearInputs.CRAFT_TOOL + "pickaxe"), "Без кирки граф обязан просить кирку");
        assertTrue(events.contains(GearInputs.CRAFT_TOOL + "axe"), "Без топора граф обязан просить топор");
        assertTrue(events.contains(GearInputs.CRAFT_TOOL + "hoe"), "Без тяпки граф обязан просить тяпку");
    }

    @Test
    void events_StageTwoWithoutArmor_AddsCraftArmor() {
        Set<String> events =
                GearInputs.events(facts(Map.of("spirit-1", spirit(null, List.of(), List.of())), List.of()), 2);

        assertTrue(events.contains(GearInputs.CRAFT_ARMOR), "На втором этапе пустые слоты брони требуют набора");
    }

    @Test
    void events_BetterPickaxeInChest_AddsEquipBest() {
        Set<String> events = GearInputs.events(
                facts(
                        Map.of("spirit-1", spirit(null, List.of(tool("pickaxe", 1, 131, 131)), List.of())),
                        List.of(chest(Map.of("minecraft:iron_pickaxe", 1)))),
                2);

        assertTrue(events.contains(GearInputs.EQUIP_BEST), "Железная кирка в сундуке лучше каменной в руке");
    }

    @Test
    void events_WeakerPickaxeInChest_NoEquipBest() {
        Set<String> events = GearInputs.events(
                facts(
                        Map.of("spirit-1", spirit(null, List.of(tool("pickaxe", 2, 250, 250)), List.of())),
                        List.of(chest(Map.of("minecraft:stone_pickaxe", 1)))),
                2);

        assertFalse(events.contains(GearInputs.EQUIP_BEST), "Каменная кирка в сундуке не лучше железной в руке");
    }

    @Test
    void events_MiningWithoutPickaxe_AddsMiningKit() {
        Set<String> events =
                GearInputs.events(facts(Map.of("spirit-1", spirit("mining", List.of(), List.of())), List.of()), 1);

        assertTrue(events.contains(GearInputs.KIT + "mining"), "Шахтёру без кирки нужен набор шахтёра");
    }

    @Test
    void events_BuildingActivity_AddsBuildingKit() {
        Set<String> events =
                GearInputs.events(facts(Map.of("spirit-1", spirit("building", List.of(), List.of())), List.of()), 1);

        assertTrue(events.contains(GearInputs.KIT + "building"), "Строителю нужен набор строителя");
    }

    @Test
    void events_DiamondPickaxe_CoversIronRequirement() {
        Set<String> events = GearInputs.events(
                facts(
                        Map.of(
                                "spirit-1",
                                spirit(
                                        null,
                                        List.of(
                                                tool("pickaxe", 3, 1561, 1561),
                                                tool("axe", 2, 250, 250),
                                                tool("shovel", 2, 250, 250),
                                                tool("sword", 2, 250, 250),
                                                tool("hoe", 0, 59, 59)),
                                        allArmor())),
                        List.of()),
                2);

        assertFalse(
                events.contains(GearInputs.CRAFT_TOOL + "pickaxe"),
                "Алмазная кирка закрывает железное требование второго этапа");
    }

    private static List<GraphFacts.Armor> allArmor() {
        return List.of(
                new GraphFacts.Armor("HEAD", "minecraft:iron_helmet", 165, 165),
                new GraphFacts.Armor("CHEST", "minecraft:iron_chestplate", 240, 240),
                new GraphFacts.Armor("LEGS", "minecraft:iron_leggings", 225, 225),
                new GraphFacts.Armor("FEET", "minecraft:iron_boots", 195, 195));
    }

    private static GraphFacts.Tool tool(String kind, int tier, int durability, int max) {
        return new GraphFacts.Tool("minecraft:tool_" + kind, kind, tier, durability, max);
    }

    private static GraphFacts.Spirit spirit(
            String activity, List<GraphFacts.Tool> tools, List<GraphFacts.Armor> armor) {
        return new GraphFacts.Spirit(
                "spirit-1",
                new GraphFacts.Position(0, 64, 0),
                20,
                18,
                300,
                false,
                false,
                false,
                activity,
                Map.of(),
                armor,
                tools);
    }

    private static GraphFacts.Chest chest(Map<String, Integer> contents) {
        return new GraphFacts.Chest(new GraphFacts.Position(0, 64, 0), contents, 10, 27);
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
                GraphFacts.Terrain.empty(),
                new GraphFacts.StageReqs(Map.of(), Map.of()));
    }
}
