package com.theminespirit.core.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CharcoalFuelResolveTests {
    private static final int STATION_RADIUS = 16;

    @Test
    void resolve_CharcoalFuel_BurnsWoodInsteadOfCoal() {
        ResourceKnowledge knowledge = woodKnowledge();

        TaskNode plan = ResourceResolver.resolve("charcoal", 1, knowledge);

        assertEquals(
                new SequenceNode(List.of(
                        gather("oak_log", 1),
                        new SequenceNode(List.of(gather("oak_log", 1), craft("oak_planks", 1))),
                        smelt("charcoal", 1))),
                plan,
                "Древесный уголь жгут брёвнами, а не углём: уголь приходится добывать из руды, и обжиг "
                        + "оказывался недоступен там, где уголь и понадобился (наблюдение 8, «не удалось добыть "
                        + "charcoal ×1»). Топливо — своя доска из брёвен, уголь в цепочке не участвует");
    }

    @Test
    void select_PrefixedCharcoal_SkipsCoalFuel() {
        FuelPlanner.FuelRequirement fuel = FuelPlanner.select(
                        "minecraft:charcoal", 1, woodKnowledge(), Set.of(), Map.of())
                .orElseThrow();

        assertEquals(
                "oak_planks",
                fuel.fuel(),
                "Предмет из задания приходит с полным id (minecraft:charcoal), а знания и таблица топлива — "
                        + "с короткими: без приведения имени обжиг снова выбирал уголь, и сад зависел от шахты");
    }

    private static ResourceKnowledge woodKnowledge() {
        return new StubKnowledge(
                        Set.of("oak_log", "coal"),
                        Map.of("oak_planks", List.of(new Ingredient("oak_log", 1))),
                        Map.of("charcoal", "oak_log"))
                .withGatherSource("coal", "coal_ore");
    }

    private static ScriptCall craft(String item, int count) {
        return new ScriptCall("craft", Map.of("item", item, "count", count, "radius", STATION_RADIUS, "place", false));
    }

    private static ScriptCall smelt(String item, int count) {
        return new ScriptCall("smelt", Map.of("item", item, "count", count, "radius", STATION_RADIUS));
    }

    private static ScriptCall gather(String block, int count) {
        int radius = block.endsWith("_log") ? CatalogLimits.HARVEST_RADIUS_MAX : CatalogLimits.HARVEST_RADIUS_DEFAULT;
        return new ScriptCall("gather", Map.of("block", block, "count", count, "radius", radius));
    }
}
