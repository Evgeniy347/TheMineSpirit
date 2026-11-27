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
        assertEquals(
                "stone",
                settings.requiredAt(1).get("pickaxe"),
                "На первом этапе кирка каменная Иначе тест settings_ProductionFile_RaisesTierByStage перестанет ловить эту поломку. При смене settings_ProductionFile_RaisesTierByStage проверь это утверждение.");
        assertEquals(
                "iron",
                settings.requiredAt(2).get("pickaxe"),
                "На втором этапе кирка железная Иначе тест settings_ProductionFile_RaisesTierByStage перестанет ловить эту поломку. При смене settings_ProductionFile_RaisesTierByStage проверь это утверждение.");
        assertEquals(
                "diamond",
                settings.requiredAt(3).get("pickaxe"),
                "На третьем этапе кирка алмазная Иначе тест settings_ProductionFile_RaisesTierByStage перестанет ловить эту поломку. При смене settings_ProductionFile_RaisesTierByStage проверь это утверждение.");
        assertEquals(
                20,
                settings.wornPercent(),
                "Инструмент считается изношенным при 20 % прочности Иначе тест settings_ProductionFile_RaisesTierByStage перестанет ловить эту поломку. При смене settings_ProductionFile_RaisesTierByStage проверь это утверждение.");
    }

    @Test
    void events_WornPickaxe_AddsToolWorn() {
        Set<String> events = GearInputs.events(
                facts(Map.of("spirit-1", spirit(null, List.of(tool("pickaxe", 2, 10, 250)), List.of())), List.of()), 2);
        assertTrue(
                events.contains(GearInputs.TOOL_WORN),
                "Кирка с 4 % прочности обязана требовать замены Иначе тест events_WornPickaxe_AddsToolWorn перестанет ловить эту поломку. При смене events_WornPickaxe_AddsToolWorn проверь это утверждение.");
    }

    @Test
    void events_FreshPickaxe_NoToolWorn() {
        Set<String> events = GearInputs.events(
                facts(Map.of("spirit-1", spirit(null, List.of(tool("pickaxe", 2, 200, 250)), List.of())), List.of()),
                2);
        assertFalse(
                events.contains(GearInputs.TOOL_WORN),
                "Кирка с 80 % прочности ещё не изношена Иначе тест events_FreshPickaxe_NoToolWorn перестанет ловить эту поломку. При смене events_FreshPickaxe_NoToolWorn проверь это утверждение.");
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
        assertTrue(
                events.contains(GearInputs.ARMOR_WORN),
                "Шлем с 6 % прочности обязан требовать ремонта Иначе тест events_WornHelmet_AddsArmorWorn перестанет ловить эту поломку. При смене events_WornHelmet_AddsArmorWorn проверь это утверждение.");
    }

    @Test
    void events_EmptySpirit_AddsCraftToolNodes() {
        Set<String> events =
                GearInputs.events(facts(Map.of("spirit-1", spirit(null, List.of(), List.of())), List.of()), 1);
        assertTrue(
                events.contains(GearInputs.CRAFT_TOOL + "pickaxe"),
                "Без кирки граф обязан просить кирку Иначе тест events_EmptySpirit_AddsCraftToolNodes перестанет ловить эту поломку. При смене events_EmptySpirit_AddsCraftToolNodes проверь это утверждение.");
        assertTrue(
                events.contains(GearInputs.CRAFT_TOOL + "axe"),
                "Без топора граф обязан просить топор Иначе тест events_EmptySpirit_AddsCraftToolNodes перестанет ловить эту поломку. При смене events_EmptySpirit_AddsCraftToolNodes проверь это утверждение.");
        assertTrue(
                events.contains(GearInputs.CRAFT_TOOL + "hoe"),
                "Без тяпки граф обязан просить тяпку Иначе тест events_EmptySpirit_AddsCraftToolNodes перестанет ловить эту поломку. При смене events_EmptySpirit_AddsCraftToolNodes проверь это утверждение.");
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
