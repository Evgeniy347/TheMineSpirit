package com.theminespirit.core.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CharcoalFuelResolveTests {
    private static final int STATION_RADIUS = 16;

    @Test
    void resolve_CharcoalFuel_BurnsWoodInsteadOfCoal() {
        ResourceKnowledge knowledge = woodKnowledge();

        TaskNode plan = CorePlanner.resolve("charcoal", 1, knowledge);

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
    void resolve_CharcoalWithLogsInInventory_DoesNotGatherInput() {
        TaskNode plan = CorePlanner.resolve("charcoal", 1, woodKnowledge(), Map.of("oak_log", 2));
        List<ScriptCall> steps = new ArrayList<>();
        collect(plan, steps);

        assertTrue(
                steps.stream()
                        .anyMatch(call -> "smelt".equals(call.script())
                                && "charcoal".equals(call.params().get("item"))),
                "Обжиг древесного угля обязан остаться в плане: " + steps);
        assertFalse(
                steps.stream()
                        .anyMatch(call -> "gather".equals(call.script())
                                && "oak_log".equals(call.params().get("block"))),
                "Брёвна уже в инвентаре — обжиг обязан взять их, а не идти рубить дерево: " + steps
                        + ". В observe12 acquire charcoal падал «подходящих блоков в радиусе 32 не осталось» "
                        + "при 8 брёвнах за спиной");
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

    private static void collect(TaskNode node, List<ScriptCall> found) {
        switch (node) {
            case ScriptCall call -> found.add(call);
            case SequenceNode sequence -> sequence.steps().forEach(step -> collect(step, found));
            case FallbackNode fallback -> fallback.steps().forEach(step -> collect(step, found));
            default ->
                throw new IllegalArgumentException("Узел " + node.getClass().getSimpleName()
                        + " не разбирается: тест ждёт script, sequence, fallback");
        }
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
