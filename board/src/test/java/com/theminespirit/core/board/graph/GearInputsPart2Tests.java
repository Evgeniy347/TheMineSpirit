package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GearInputsPart2Tests {
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
                Math.max(20, 1f),
                null,
                18,
                300,
                false,
                false,
                false,
                activity,
                Map.of(),
                armor,
                tools,
                null);
    }

    private static GraphFacts.Spirit spiritWithId(String id, List<GraphFacts.Tool> tools) {
        GraphFacts.Spirit base = spirit(null, tools, List.of());
        return new GraphFacts.Spirit(
                id,
                base.position(),
                base.health(),
                base.maxHealth(),
                base.recentHurt(),
                base.food(),
                base.air(),
                base.inWater(),
                base.trapped(),
                base.stuck(),
                base.activity(),
                base.inventory(),
                base.armor(),
                base.tools(),
                base.trap());
    }

    private static GraphFacts.Chest chest(Map<String, Integer> contents) {
        return new GraphFacts.Chest(new GraphFacts.Position(0, 64, 0), contents, 10, 27, GraphFacts.Reach.UNKNOWN);
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

    @Test
    void events_StageTwoWithoutArmor_AddsCraftArmor() {
        Set<String> events =
                GearInputs.events(facts(Map.of("spirit-1", spirit(null, List.of(), List.of())), List.of()), 2);
        assertTrue(
                events.contains(GearInputs.CRAFT_ARMOR),
                "На втором этапе пустые слоты брони требуют набора Иначе тест events_StageTwoWithoutArmor_AddsCraftArmor перестанет ловить эту поломку. При смене events_StageTwoWithoutArmor_AddsCraftArmor проверь это утверждение.");
    }

    @Test
    void events_BetterPickaxeInChest_AddsEquipBest() {
        Set<String> events = GearInputs.events(
                facts(
                        Map.of("spirit-1", spirit(null, List.of(tool("pickaxe", 1, 131, 131)), List.of())),
                        List.of(chest(Map.of("minecraft:iron_pickaxe", 1)))),
                2);
        assertTrue(
                events.contains(GearInputs.EQUIP_BEST),
                "Железная кирка в сундуке лучше каменной в руке Иначе тест events_BetterPickaxeInChest_AddsEquipBest перестанет ловить эту поломку. При смене events_BetterPickaxeInChest_AddsEquipBest проверь это утверждение.");
    }

    @Test
    void events_WeakerPickaxeInChest_NoEquipBest() {
        Set<String> events = GearInputs.events(
                facts(
                        Map.of("spirit-1", spirit(null, List.of(tool("pickaxe", 2, 250, 250)), List.of())),
                        List.of(chest(Map.of("minecraft:stone_pickaxe", 1)))),
                2);
        assertFalse(
                events.contains(GearInputs.EQUIP_BEST),
                "Каменная кирка в сундуке не лучше железной в руке Иначе тест events_WeakerPickaxeInChest_NoEquipBest перестанет ловить эту поломку. При смене events_WeakerPickaxeInChest_NoEquipBest проверь это утверждение.");
    }

    @Test
    void events_MiningWithoutPickaxe_AddsMiningKit() {
        Set<String> events =
                GearInputs.events(facts(Map.of("spirit-1", spirit("mining", List.of(), List.of())), List.of()), 1);
        assertTrue(
                events.contains(GearInputs.KIT + "mining"),
                "Шахтёру без кирки нужен набор шахтёра Иначе тест events_MiningWithoutPickaxe_AddsMiningKit перестанет ловить эту поломку. При смене events_MiningWithoutPickaxe_AddsMiningKit проверь это утверждение.");
    }

    @Test
    void events_BuildingActivity_AddsBuildingKit() {
        Set<String> events =
                GearInputs.events(facts(Map.of("spirit-1", spirit("building", List.of(), List.of())), List.of()), 1);
        assertTrue(
                events.contains(GearInputs.KIT + "building"),
                "Строителю нужен набор строителя Иначе тест events_BuildingActivity_AddsBuildingKit перестанет ловить эту поломку. При смене events_BuildingActivity_AddsBuildingKit проверь это утверждение.");
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
                "Алмазная кирка закрывает железное требование второго этапа Иначе тест events_DiamondPickaxe_CoversIronRequirement перестанет ловить эту поломку. При смене events_DiamondPickaxe_CoversIronRequirement проверь это утверждение.");
    }

    @Test
    void equipUpgrade_BetterToolInChest_NamesItemAndChest() {
        GraphFacts.Spirit spirit = spirit(null, List.of(tool("pickaxe", 1, 131, 131)), List.of());
        var upgrade = GearRules.equipUpgrade(
                        spirit, List.of(chest(Map.of("minecraft:iron_pickaxe", 1))), GearInputs.settings())
                .orElseThrow();
        assertEquals(
                "minecraft:iron_pickaxe",
                upgrade.item(),
                "Надеть надо железную кирку из сундука Иначе тест equipUpgrade_BetterToolInChest_NamesItemAndChest перестанет ловить эту поломку. При смене equipUpgrade_BetterToolInChest_NamesItemAndChest проверь это утверждение.");
        assertEquals(
                64,
                upgrade.chest().y(),
                "Сундук назван позицией: дух должен дойти до него Иначе тест equipUpgrade_BetterToolInChest_NamesItemAndChest перестанет ловить эту поломку. При смене equipUpgrade_BetterToolInChest_NamesItemAndChest проверь это утверждение.");
    }

    @Test
    void equipUpgrade_NothingBetter_Empty() {
        GraphFacts.Spirit spirit = spirit(null, List.of(tool("pickaxe", 2, 250, 250)), List.of());
        assertTrue(
                GearRules.equipUpgrade(
                                spirit, List.of(chest(Map.of("minecraft:stone_pickaxe", 1))), GearInputs.settings())
                        .isEmpty(),
                "Каменная кирка не лучше железной — менять нечего Иначе тест equipUpgrade_NothingBetter_Empty перестанет ловить эту поломку. При смене equipUpgrade_NothingBetter_Empty проверь это утверждение.");
    }

    @Test
    void equipTarget_FirstSpiritBusy_FreeSpiritTakesTool() {
        GraphFacts.Spirit busy = spiritWithId("spirit-1", List.of());
        GraphFacts.Spirit free = spiritWithId("spirit-2", List.of());
        GearRules.Equip target = GearRules.equipTarget(
                        List.of(busy, free),
                        id -> "spirit-1".equals(id),
                        List.of(chest(Map.of("minecraft:stone_pickaxe", 1))),
                        GearInputs.settings())
                .orElseThrow();
        assertEquals(
                "spirit-2",
                target.spirit().id(),
                "Занятый первый дух не забирает обновку у свободного: иначе кирка из сундука лежит без дела, "
                        + "а снос и шахта падают с «нужна кирка» При смене equipTarget_FirstSpiritBusy_FreeSpiritTakesTool проверь это утверждение.");
    }

    @Test
    void availableChests_ReservedTool_SubtractsOnlyReservedCount() {
        GraphFacts.Chest chest = chest(Map.of(
                "minecraft:wooden_pickaxe", 2,
                "minecraft:stone_pickaxe", 1));

        var available = GearRules.availableChests(List.of(chest), Map.of("wooden_pickaxe", 1));

        assertEquals(
                Map.of(
                        "minecraft:wooden_pickaxe", 1,
                        "minecraft:stone_pickaxe", 1),
                available.get(0).contents(),
                "Бронь занятия убирает из выбора ровно одну кирку: иначе второй дух снова получит"
                        + " тот же последний предмет и упадёт с «в контейнере 0 из 1» Иначе тест"
                        + " availableChests_ReservedTool_SubtractsOnlyReservedCount перестанет ловить"
                        + " живой случай 25.11. При смене availableChests_ReservedTool_SubtractsOnlyReservedCount"
                        + " проверь GearPost.equip и BoardReserve.items.");
    }

    @Test
    void missingArmor_StageOne_LeatherHelmet() {
        GraphFacts.Spirit spirit = spirit(null, List.of(), List.of());
        assertEquals(
                "leather_helmet",
                GearRules.missingArmor(spirit, 1, GearInputs.settings()).orElseThrow(),
                "На первом этапе первый пустой слот закрывают кожаным шлемом Иначе тест missingArmor_StageOne_LeatherHelmet перестанет ловить эту поломку. При смене missingArmor_StageOne_LeatherHelmet проверь это утверждение.");
    }

    @Test
    void missingArmor_StageTwo_IronChestplateAfterHelmet() {
        GraphFacts.Spirit spirit =
                spirit(null, List.of(), List.of(new GraphFacts.Armor("HEAD", "minecraft:iron_helmet", 165, 165)));
        assertEquals(
                "iron_chestplate",
                GearRules.missingArmor(spirit, 2, GearInputs.settings()).orElseThrow(),
                "Второй слот на втором этапе — железный нагрудник Иначе тест missingArmor_StageTwo_IronChestplateAfterHelmet перестанет ловить эту поломку. При смене missingArmor_StageTwo_IronChestplateAfterHelmet проверь это утверждение.");
    }

    @Test
    void craftToolItem_StageTwo_NamesIronTool() {
        assertEquals(
                "iron_axe",
                GearRules.craftToolItem(spirit(null, List.of(), List.of()), "axe", 2, GearInputs.settings()),
                "Второй этап требует железный топор Иначе тест craftToolItem_StageTwo_NamesIronTool перестанет ловить эту поломку. При смене craftToolItem_StageTwo_NamesIronTool проверь это утверждение.");
    }

    @Test
    void needsTool_StrongerTool_TrueAndFalse() {
        GearInputs.Settings settings = GearInputs.settings();
        assertTrue(
                GearRules.needsTool(
                        spirit(null, List.of(tool("pickaxe", 1, 131, 131)), List.of()), "pickaxe", 2, settings),
                "Каменная кирка не закрывает железное требование Иначе тест needsTool_StrongerTool_TrueAndFalse перестанет ловить эту поломку. При смене needsTool_StrongerTool_TrueAndFalse проверь это утверждение.");
        assertFalse(
                GearRules.needsTool(
                        spirit(null, List.of(tool("pickaxe", 2, 250, 250)), List.of()), "pickaxe", 2, settings),
                "Железная кирка закрывает требование второго этапа Иначе тест needsTool_StrongerTool_TrueAndFalse перестанет ловить эту поломку. При смене needsTool_StrongerTool_TrueAndFalse проверь это утверждение.");
    }
}
