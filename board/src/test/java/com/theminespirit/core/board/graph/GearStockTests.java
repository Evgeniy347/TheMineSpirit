package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GearStockTests {
    @Test
    void events_AxeInChest_NoCraftAxeForEmptySpirit() {
        Set<String> events = GearInputs.events(
                facts(
                        Map.of("spirit-1", spirit(null, List.of(), List.of())),
                        List.of(chest(Map.of("minecraft:stone_axe", 3)))),
                1);

        assertFalse(
                events.contains(GearInputs.CRAFT_TOOL + "axe"),
                "Топор лежит в сундуке: граф обязан видеть его и не просить новый");
    }

    @Test
    void events_WoodenAxeInChest_NoCraftAxeDespiteStoneRequirement() {
        Set<String> events = GearInputs.events(
                facts(
                        Map.of("spirit-1", spirit(null, List.of(), List.of())),
                        List.of(chest(Map.of("minecraft:wooden_axe", 57)))),
                1);

        assertFalse(
                events.contains(GearInputs.CRAFT_TOOL + "axe"),
                "Запас деревянных топоров в сундуках — не повод крафтить ещё: дух сначала берёт запас, "
                        + "крафт ставится только при полной нехватке (observe25 B10)");
    }

    @Test
    void plan_ToolStockInChest_NoAxeCraftDespiteStageTier() {
        GraphFacts facts = facts(
                Map.of("spirit-1", spirit(null, List.of(), List.of())),
                List.of(chest(Map.of("minecraft:wooden_axe", 57))));
        GraphWorld world = GraphWorld.settled().tools("spirit-1", -1).facts(facts);

        GraphPlan plan = world.plan();

        assertFalse(
                plan.tasks().stream()
                        .anyMatch(
                                task -> task.id().contains("_axe") && task.id().contains("craft")),
                "В сундуках 57 топоров — граф обязан вести духа за запасом, а не крафтить ещё: " + plan.tasks());
    }

    @Test
    void events_AxeMovedFromChestToSpirit_NoCraftBeforeOrAfter() {
        List<GraphFacts.Chest> chestWithAxe = List.of(chest(Map.of("minecraft:stone_axe", 1)));
        GraphFacts.Spirit empty = spirit(null, List.of(), List.of());
        Set<String> before = GearInputs.events(facts(Map.of("spirit-1", empty), chestWithAxe), 1);

        List<GraphFacts.Chest> emptyChest = List.of(chest(Map.of()));
        GraphFacts.Spirit carrying = spirit(null, List.of(tool("axe", 1, 131, 131)), List.of());
        Set<String> after = GearInputs.events(facts(Map.of("spirit-1", carrying), emptyChest), 1);

        assertFalse(before.contains(GearInputs.CRAFT_TOOL + "axe"), "До переноса топор виден в сундуке");
        assertFalse(after.contains(GearInputs.CRAFT_TOOL + "axe"), "После переноса топор виден у духа");
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
                20,
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

    private static GraphFacts.Chest chest(Map<String, Integer> contents) {
        return new GraphFacts.Chest(new GraphFacts.Position(0, 64, 0), contents, 10, 27, GraphFacts.Reach.UNKNOWN);
    }

    private static GraphFacts facts(Map<String, GraphFacts.Spirit> spirits, List<GraphFacts.Chest> chests) {
        return GraphFacts.empty().withSpirits(new LinkedHashMap<>(spirits)).withChests(chests);
    }
}
