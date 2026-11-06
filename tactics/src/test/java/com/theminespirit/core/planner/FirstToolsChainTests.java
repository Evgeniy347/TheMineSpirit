package com.theminespirit.core.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FirstToolsChainTests {
    private static final Map<String, List<Ingredient>> CRAFTS = Map.ofEntries(
            Map.entry("oak_planks", List.of(new Ingredient("oak_log", 1))),
            Map.entry("stick", List.of(new Ingredient("oak_planks", 2))),
            Map.entry("crafting_table", List.of(new Ingredient("oak_planks", 4))),
            Map.entry("wooden_pickaxe", List.of(new Ingredient("oak_planks", 3), new Ingredient("stick", 2))),
            Map.entry("wooden_axe", List.of(new Ingredient("oak_planks", 3), new Ingredient("stick", 2))),
            Map.entry("wooden_shovel", List.of(new Ingredient("oak_planks", 1), new Ingredient("stick", 2))),
            Map.entry("stone_pickaxe", List.of(new Ingredient("cobblestone", 3), new Ingredient("stick", 2))),
            Map.entry("stone_axe", List.of(new Ingredient("cobblestone", 3), new Ingredient("stick", 2))),
            Map.entry("stone_shovel", List.of(new Ingredient("cobblestone", 1), new Ingredient("stick", 2))),
            Map.entry("stone_sword", List.of(new Ingredient("cobblestone", 2), new Ingredient("stick", 1))),
            Map.entry("furnace", List.of(new Ingredient("cobblestone", 8))));

    private static final Map<String, String> STATIONS = Map.ofEntries(
            Map.entry("wooden_pickaxe", "crafting_table"),
            Map.entry("wooden_axe", "crafting_table"),
            Map.entry("wooden_shovel", "crafting_table"),
            Map.entry("stone_pickaxe", "crafting_table"),
            Map.entry("stone_axe", "crafting_table"),
            Map.entry("stone_shovel", "crafting_table"),
            Map.entry("stone_sword", "crafting_table"),
            Map.entry("furnace", "crafting_table"),
            Map.entry("iron_ingot", "furnace"));

    private static final ResourceKnowledge WORLD = new StubKnowledge(
                    Set.of("oak_log", "cobblestone", "coal", "iron_ore"),
                    CRAFTS,
                    Map.of("iron_ingot", "iron_ore"),
                    Map.of("oak_planks", 4, "stick", 4),
                    STATIONS)
            .withTool("cobblestone", "wooden_pickaxe")
            .withTool("iron_ore", "stone_pickaxe")
            .withTool("coal_ore", "wooden_pickaxe");

    @Test
    void resolve_EmptyInventoryToWoodenPickaxe_PlanIsMinimalAndTableComesFirst() {
        TaskNode plan = CorePlanner.resolve("wooden_pickaxe", 1, WORLD);

        List<ScriptCall> calls = flatten(plan);
        assertEquals(
                3,
                producedCount(calls, "gather", "oak_log"),
                "От пустого инвентаря кирке нужны ровно 3 бревна: 1 на доски кирки, 1 на палки, 1 на верстак. "
                        + "Больше — лишний груз, меньше — крафт упрётся в нехватку. План: " + calls);
        assertTrue(
                indexOf(calls, "craft", "crafting_table") < indexOf(calls, "craft", "wooden_pickaxe"),
                "Верстак обязан быть скрафчен раньше кирки: без станции крафт 3×3 невозможен. План: " + calls);
        assertTrue(
                indexOf(calls, "gather", "oak_log") < indexOf(calls, "craft", "crafting_table"),
                "Брёвна для верстака добываются до его крафта: иначе скрафтить станцию не из чего. План: " + calls);
        assertEquals("craft", last(calls).script(), "План обязан оканчиваться крафтом цели: " + calls);
        assertEquals(
                "wooden_pickaxe", last(calls).params().get("item"), "Последний шаг плана — деревянная кирка: " + calls);
        assertEquals(
                1,
                producedCount(calls, "craft", "crafting_table"),
                "Верстак в плане один: второй — лишние 4 доски и ход. План: " + calls);
    }

    @ParameterizedTest
    @ValueSource(strings = {"wooden_pickaxe", "wooden_axe", "wooden_shovel"})
    void resolve_EmptyInventoryToWoodenTools_TablePrecedesEveryTool(String tool) {
        TaskNode plan = CorePlanner.resolve(tool, 1, WORLD);

        List<ScriptCall> calls = flatten(plan);
        assertTrue(
                indexOf(calls, "craft", "crafting_table") < indexOf(calls, "craft", tool),
                "Верстак обязан быть раньше " + tool + ": кирка, топор и лопата — рецепты 3×3. План: " + calls);
        assertEquals("craft", last(calls).script(), "План обязан оканчиваться крафтом " + tool + ": " + calls);
        assertEquals(
                tool,
                last(calls).params().get("item"),
                "Последний крафт плана — сам инструмент " + tool + ": " + calls);
        assertEquals(
                1,
                producedCount(calls, "craft", "crafting_table"),
                "Один верстак обслуживает все деревянные инструменты в плане: " + calls);
    }

    @Test
    void resolve_EmptyInventoryToStonePickaxe_WoodenPickaxePrecedesCobblestone() {
        TaskNode plan = CorePlanner.resolve("stone_pickaxe", 1, WORLD);

        List<ScriptCall> calls = flatten(plan);
        int woodenPickaxe = indexOf(calls, "craft", "wooden_pickaxe");
        int cobblestone = indexOf(calls, "gather", "cobblestone");
        int stonePickaxe = indexOf(calls, "craft", "stone_pickaxe");
        assertTrue(
                woodenPickaxe < cobblestone,
                "Каменная кирка требует деревянную раньше добычи булыжника: без кирки gather не даёт дроп. План: "
                        + calls);
        assertTrue(
                cobblestone < stonePickaxe,
                "Булыжник обязан быть добыт до крафта каменной кирки: иначе крафт упрётся в нехватку. План: " + calls);
        assertEquals(
                1,
                producedCount(calls, "craft", "wooden_pickaxe"),
                "Деревянная кирка в плане ровно одна: повторный крафт — потерянные брёвна. План: " + calls);
    }

    @Property
    void resolve_StoneToolsFromEmpty_PlansWoodenPickaxeFirst(
            @ForAll("stoneTools") String tool, @ForAll @IntRange(min = 1, max = 8) int count) {
        TaskNode plan = CorePlanner.resolve(tool, count, WORLD);

        List<ScriptCall> calls = flatten(plan);
        assertEquals(
                1,
                producedCount(calls, "craft", "wooden_pickaxe"),
                "На любую каменную цель " + tool + "×" + count + " деревянная кирка нужна ровно один раз: "
                        + "повтор плодит лишние брёвна, отсутствие оставляет gather без дропа. План: " + calls);
        int woodenPickaxe = indexOf(calls, "craft", "wooden_pickaxe");
        int firstCobblestone = indexOf(calls, "gather", "cobblestone");
        assertTrue(
                woodenPickaxe < firstCobblestone,
                "Деревянная кирка обязана появиться до первой добычи булыжника для " + tool + "×" + count + ": план "
                        + calls);
        assertEquals("craft", last(calls).script(), "План обязан оканчиваться крафтом " + tool + ": " + calls);
        assertEquals(tool, last(calls).params().get("item"), "Последний крафт — цель " + tool + ": " + calls);
    }

    @Test
    void resolve_StonePickaxeWithWoodenPickaxeInInventory_ReusesIt() {
        TaskNode plan = CorePlanner.resolve("stone_pickaxe", 1, WORLD, Map.of("wooden_pickaxe", 1));

        List<ScriptCall> calls = flatten(plan);
        assertEquals(
                0,
                producedCount(calls, "craft", "wooden_pickaxe"),
                "Деревянная кирка уже в инвентаре — план не тратит брёвна на вторую: " + calls);
        assertTrue(
                indexOf(calls, "gather", "cobblestone") < indexOf(calls, "craft", "stone_pickaxe"),
                "Имеющаяся кирка идёт в дело до крафта каменной: " + calls);
    }

    @Test
    void resolve_IronIngotFromEmpty_SmeltingComesAfterFurnaceFuelAndPickaxes() {
        TaskNode plan = CorePlanner.resolve("iron_ingot", 1, WORLD);

        List<ScriptCall> calls = flatten(plan);
        int table = indexOf(calls, "craft", "crafting_table");
        int furnace = indexOf(calls, "craft", "furnace");
        int stonePickaxe = indexOf(calls, "craft", "stone_pickaxe");
        int ironOre = indexOf(calls, "gather", "iron_ore");
        int coal = indexOf(calls, "gather", "coal");
        int smelt = indexOf(calls, "smelt", "iron_ingot");
        assertTrue(
                table < furnace,
                "Печь — рецепт 3×3, ей нужен верстак раньше: иначе крафт печи невозможен. План: " + calls);
        assertTrue(furnace < smelt, "Печь обязана быть скрафчена до плавки: план: " + calls);
        assertTrue(coal < smelt, "Топливо обязано быть добыто до плавки: план: " + calls);
        assertTrue(stonePickaxe < ironOre, "Железная руда требует каменную кирку раньше добычи: план: " + calls);
        assertTrue(ironOre < smelt, "Руда обязана быть добыта до плавки: план: " + calls);
    }

    @Provide
    Arbitrary<String> stoneTools() {
        return Arbitraries.of("stone_pickaxe", "stone_axe", "stone_shovel", "stone_sword");
    }

    private static int producedCount(List<ScriptCall> calls, String script, String item) {
        int total = 0;
        for (ScriptCall call : calls) {
            if (produces(call, script, item)) {
                total++;
            }
        }
        return total;
    }

    private static int indexOf(List<ScriptCall> calls, String script, String item) {
        for (int index = 0; index < calls.size(); index++) {
            if (produces(calls.get(index), script, item)) {
                return index;
            }
        }
        throw new AssertionError("В плане нет шага " + script + " " + item + ": " + calls);
    }

    private static boolean produces(ScriptCall call, String script, String item) {
        if (!call.script().equals(script)) {
            return false;
        }
        Object name = call.params().get("gather".equals(script) ? "block" : "item");
        return item.equals(name);
    }

    private static ScriptCall last(List<ScriptCall> calls) {
        if (calls.isEmpty()) {
            throw new AssertionError("План пуст: цель не добывается ни одним шагом");
        }
        return calls.get(calls.size() - 1);
    }

    private static List<ScriptCall> flatten(TaskNode node) {
        List<ScriptCall> calls = new ArrayList<>();
        collect(node, calls);
        return calls;
    }

    private static void collect(TaskNode node, List<ScriptCall> calls) {
        if (node instanceof SequenceNode sequence) {
            sequence.steps().forEach(step -> collect(step, calls));
        } else if (node instanceof ScriptCall call) {
            calls.add(call);
        } else {
            throw new AssertionError("Неожиданный узел плана " + node.getClass() + ": разбор плана неполон");
        }
    }
}
