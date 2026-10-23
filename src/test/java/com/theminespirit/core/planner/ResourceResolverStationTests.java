package com.theminespirit.core.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ResourceResolverStationTests {
    private static final int STATION_RADIUS = 16;

    private static final Map<String, List<Ingredient>> CRAFTS = Map.of(
            "oak_planks", List.of(new Ingredient("oak_log", 1)),
            "stick", List.of(new Ingredient("oak_planks", 2)),
            "crafting_table", List.of(new Ingredient("oak_planks", 4)),
            "wooden_pickaxe", List.of(new Ingredient("oak_planks", 3), new Ingredient("stick", 2)),
            "furnace", List.of(new Ingredient("cobblestone", 8)),
            "iron_pickaxe", List.of(new Ingredient("iron_ingot", 3), new Ingredient("stick", 2)),
            "tool_kit", List.of(new Ingredient("wooden_pickaxe", 1), new Ingredient("iron_pickaxe", 1)));
    private static final Map<String, String> STATIONS = Map.of(
            "wooden_pickaxe", "crafting_table",
            "iron_pickaxe", "crafting_table",
            "furnace", "crafting_table",
            "iron_ingot", "furnace");
    private static final ResourceKnowledge WORLD = new StubKnowledge(
            Set.of("oak_log", "cobblestone", "iron_ore", "coal"),
            CRAFTS,
            Map.of("iron_ingot", "iron_ore"),
            Map.of("oak_planks", 4, "stick", 4),
            STATIONS);

    @Test
    void resolve_ThreeByThreeCraft_AcquiresCraftingTableRightBeforeIt() {
        TaskNode plan = ResourceResolver.resolve("wooden_pickaxe", 1, WORLD);

        assertEquals(
                seq(
                        seq(gather("oak_log", 1), craft("oak_planks", 3)),
                        seq(seq(gather("oak_log", 1), craft("oak_planks", 2)), craft("stick", 2)),
                        tableSteps(),
                        craft("wooden_pickaxe", 1)),
                plan,
                "Кирка 3×3 требует верстак: резолвер обязан вставить получение верстака (доски → верстак) перед "
                        + "крафтом кирки, иначе дух упрётся в 'нужен верстак' посреди плана");
    }

    @Test
    void resolve_TwoCraftsNeedingTable_AcquiresTableOnce() {
        TaskNode plan = ResourceResolver.resolve("tool_kit", 1, WORLD);

        assertEquals(
                1,
                producedCount(plan, "crafting_table"),
                "Два крафта 3×3 в одном плане обязаны делить один верстак: второй верстак — лишние 4 доски и ход");
        assertTrue(
                indexOfCraft(plan, "crafting_table") < indexOfCraft(plan, "wooden_pickaxe"),
                "Верстак обязан быть получен до первого крафта, которому он нужен: иначе кирка крафтится без него");
    }

    @Test
    void resolve_InventoryCraft_DoesNotAskForStation() {
        TaskNode plan = ResourceResolver.resolve("stick", 4, WORLD);

        assertEquals(
                0,
                producedCount(plan, "crafting_table"),
                "Палки идут в сетке инвентаря: резолвер не вправе просить верстак ради рецепта 2×2");
    }

    @Test
    void resolve_Smelt_AcquiresFurnaceAndItsTableBeforeSmelting() {
        TaskNode plan = ResourceResolver.resolve("iron_ingot", 1, WORLD);

        assertEquals(
                seq(
                        gather("iron_ore", 1),
                        gather("coal", 1),
                        seq(gather("cobblestone", 8), tableSteps(), craft("furnace", 1)),
                        smelt("iron_ingot", 1)),
                plan,
                "Плавке нужна печь, а печи (3×3) нужен верстак: резолвер обязан получить верстак, затем печь и только "
                        + "потом плавить. Топливо добывается как раньше (1 уголь на 1 слиток)");
    }

    @Test
    void resolve_FurnaceStandingInWorld_SkipsFurnaceBuildingPlan() {
        TaskNode plan = ResourceResolver.resolve("iron_ingot", 1, WORLD, Map.of("coal", 1), Set.of("furnace"));

        assertEquals(
                seq(gather("iron_ore", 1), smelt("iron_ingot", 1)),
                plan,
                "Печь уже стоит в мире — план обязан плавить сразу: добыча 8 булыжников под вторую печь падает "
                        + "в мире, где булыжника нет");
    }

    @Test
    void resolve_CraftingTableStandingInWorld_FurnacePlannedWithoutTable() {
        TaskNode plan = ResourceResolver.resolve("iron_ingot", 1, WORLD, Map.of("coal", 1), Set.of("crafting_table"));

        assertEquals(
                seq(gather("iron_ore", 1), seq(gather("cobblestone", 8), craft("furnace", 1)), smelt("iron_ingot", 1)),
                plan,
                "Верстак в мире гасит только свой шаг: печь строится, но без второго верстака — иначе план "
                        + "тратит 4 доски на дубль того, что уже стоит");
    }

    @Test
    void resolve_SmeltAndThreeByThreeCraft_EachStationOnce() {
        TaskNode plan = ResourceResolver.resolve("iron_pickaxe", 1, WORLD);

        assertEquals(
                1,
                producedCount(plan, "crafting_table"),
                "Верстак нужен и печи, и кирке, но план обязан получить его один раз");
        assertEquals(1, producedCount(plan, "furnace"), "Печь нужна плавке один раз на план");
        assertTrue(
                indexOfCraft(plan, "furnace") < indexOfSmelt(plan, "iron_ingot"),
                "Печь обязана быть получена до плавки, которой она нужна");
    }

    @Test
    void resolve_StationWithoutWayToGetIt_ThrowsNamingStation() {
        ResourceKnowledge noTable = new StubKnowledge(
                Set.of("oak_log"),
                Map.of(
                        "oak_planks",
                        List.of(new Ingredient("oak_log", 1)),
                        "wooden_pickaxe",
                        List.of(new Ingredient("oak_planks", 3))),
                Map.of(),
                Map.of("oak_planks", 4),
                Map.of("wooden_pickaxe", "crafting_table"));

        UnplannableGoalException error = assertThrows(
                UnplannableGoalException.class,
                () -> ResourceResolver.resolve("wooden_pickaxe", 1, noTable),
                "Станция, которую негде взять, обязана давать отказ: иначе план выглядит готовым и ломается у "
                        + "верстака");

        assertEquals(
                "crafting_table",
                error.item(),
                "Исключение обязано нести станцию 'crafting_table': по ней правят знания или выдают верстак духу");
    }

    private static TaskNode tableSteps() {
        return seq(seq(gather("oak_log", 1), craft("oak_planks", 4)), craft("crafting_table", 1));
    }

    private static int producedCount(TaskNode plan, String item) {
        int total = 0;
        for (ScriptCall call : flatten(plan)) {
            boolean gathers =
                    "gather".equals(call.script()) && call.params().get("block").equals(item);
            boolean crafts =
                    "craft".equals(call.script()) && call.params().get("item").equals(item);
            if (gathers || crafts) {
                total++;
            }
        }
        return total;
    }

    private static int indexOfCraft(TaskNode plan, String item) {
        return indexOf(plan, "craft", item);
    }

    private static int indexOfSmelt(TaskNode plan, String item) {
        return indexOf(plan, "smelt", item);
    }

    private static int indexOf(TaskNode plan, String script, String item) {
        List<ScriptCall> calls = flatten(plan);
        for (int i = 0; i < calls.size(); i++) {
            if (calls.get(i).script().equals(script)
                    && calls.get(i).params().get("item").equals(item)) {
                return i;
            }
        }
        throw new AssertionError("В плане нет шага " + script + " " + item + ": " + calls);
    }

    private static List<ScriptCall> flatten(TaskNode node) {
        List<ScriptCall> calls = new ArrayList<>();
        if (node instanceof SequenceNode sequence) {
            sequence.steps().forEach(step -> calls.addAll(flatten(step)));
        } else if (node instanceof ScriptCall call) {
            calls.add(call);
        } else {
            throw new AssertionError("Неожиданный узел плана " + node.getClass());
        }
        return calls;
    }

    private static SequenceNode seq(TaskNode... steps) {
        return new SequenceNode(List.of(steps));
    }

    private static ScriptCall gather(String block, int count) {
        // Брёвна ищутся в радиусе 32: расчистка участка вырубает деревья в 16 от дома.
        int radius = block.endsWith("_log") ? CatalogLimits.HARVEST_RADIUS_MAX : CatalogLimits.HARVEST_RADIUS_DEFAULT;
        return new ScriptCall("gather", Map.of("block", block, "count", count, "radius", radius));
    }

    private static ScriptCall craft(String item, int count) {
        return new ScriptCall("craft", Map.of("item", item, "count", count, "radius", STATION_RADIUS));
    }

    private static ScriptCall smelt(String item, int count) {
        return new ScriptCall("smelt", Map.of("item", item, "count", count, "radius", STATION_RADIUS));
    }
}
