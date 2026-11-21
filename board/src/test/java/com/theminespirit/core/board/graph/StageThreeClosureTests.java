package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphState.Built;
import com.theminespirit.core.inventory.api.JsonResource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StageThreeClosureTests {
    private static final SupplyGraph MANUAL =
            SupplyGraph.parse(JsonResource.readClasspath(SupplyGraph.RESOURCE), SupplyGraph.RESOURCE);
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();
    private static final RecipeImport.Result VANILLA =
            RecipeImport.importRecipes(MinecraftRecipeData.recipes(), MinecraftRecipeData.tags(), MANUAL);
    private static final List<String> CRAFTED = List.of(
            "craft_repeater",
            "craft_lever",
            "craft_stone_button",
            "craft_stone_pressure_plate",
            "craft_piston",
            "craft_hopper",
            "craft_rail",
            "craft_minecart",
            "craft_magma_cream");

    @Test
    void craft_StageThreeWays_ImplementedAndVanillaRecipeExists() {
        List<String> problems = new ArrayList<>();
        for (String id : CRAFTED) {
            SupplyGraph.Way way =
                    GRAPH.way(id).orElseThrow(() -> new AssertionError("Путь " + id + " пропал из графа"));
            if (!GRAPH.implemented(id)) {
                problems.add(id + ": implemented=false");
            }
            boolean recipe = VANILLA.ways().stream()
                    .anyMatch(candidate -> candidate.makes().equals(way.makes()));
            if (!recipe) {
                problems.add(id + ": нет ванильного рецепта верстака для " + way.makes());
            }
        }
        assertEquals(
                List.of(),
                problems,
                "Крафт скрипт craft берёт любой рецепт из RecipeManager, а путь графа обязан быть реализован: "
                        + problems
                        + " Иначе тест craft_StageThreeWays_ImplementedAndVanillaRecipeExists перестанет ловить эту поломку. При смене craft_StageThreeWays_ImplementedAndVanillaRecipeExists проверь это утверждение.");
    }

    @Test
    void plan_MechanismOrders_TableCraftFeasible() {
        GraphWorld world = GraphWorld.settled().tools("spirit-1", 3).built("crafting_table", Built.BUILT);
        world.stage = 3;
        world.with("oak_planks", 3)
                .with("cobblestone", 4)
                .with("iron_ingot", 7)
                .with("redstone", 1)
                .with("stick", 1);
        GraphPlan plan = GraphPlanner.plan(GRAPH, world.state().withOrders(Map.of("piston", 1, "rail", 1)));

        assertTrue(
                plan.task("craft_piston")
                                .map(task -> task.implemented() && task.feasible())
                                .orElse(false)
                        && plan.task("craft_rail")
                                .map(task -> task.implemented() && task.feasible())
                                .orElse(false),
                "Поршень и рельсы на верстаке — пути выполнимы: " + plan.task("craft_piston") + ", "
                        + plan.task("craft_rail")
                        + " Иначе тест plan_MechanismOrders_TableCraftFeasible перестанет ловить эту поломку. При смене plan_MechanismOrders_TableCraftFeasible проверь это утверждение.");
    }

    @Test
    void plan_MagmaCreamOrder_ShapelessCraftFeasible() {
        GraphWorld world = GraphWorld.settled().tools("spirit-1", 3);
        world.stage = 3;
        world.with("blaze_powder", 1).with("slime_ball", 1);
        GraphPlan plan = GraphPlanner.plan(GRAPH, world.state().withOrders(Map.of("magma_cream", 1)));

        assertTrue(
                plan.task("craft_magma_cream")
                        .map(task -> task.implemented() && task.feasible())
                        .orElse(false),
                "Магмовая слизь — бесформенный рецепт верстака без постройки, выполним при ингредиентах: "
                        + plan.task("craft_magma_cream")
                        + " Иначе тест plan_MagmaCreamOrder_ShapelessCraftFeasible перестанет ловить эту поломку. При смене plan_MagmaCreamOrder_ShapelessCraftFeasible проверь это утверждение.");
    }

    @Test
    void saddle_NoVanillaRecipe_LootOnly() {
        assertTrue(
                GRAPH.way("craft_saddle").isEmpty(),
                "Седло в Minecraft 1.21.1 не крафтится: путь craft_saddle — выдуманный рецепт"
                        + " Иначе тест saddle_NoVanillaRecipe_LootOnly перестанет ловить эту поломку. При смене saddle_NoVanillaRecipe_LootOnly проверь это утверждение.");
        assertTrue(
                GRAPH.way("loot_saddle")
                        .map(way -> !GRAPH.implemented(way.id()))
                        .orElse(false),
                "Источник седла — сундуки, рыбалка и торговля: loot_saddle внесён как «в разработке»"
                        + " Иначе тест saddle_NoVanillaRecipe_LootOnly перестанет ловить эту поломку. При смене saddle_NoVanillaRecipe_LootOnly проверь это утверждение.");
        assertTrue(
                VANILLA.ways().stream().noneMatch(way -> "saddle".equals(way.makes())),
                "Ванильного рецепта седла нет: граф не должен обещать его крафт"
                        + " Иначе тест saddle_NoVanillaRecipe_LootOnly перестанет ловить эту поломку. При смене saddle_NoVanillaRecipe_LootOnly проверь это утверждение.");
    }

    @Test
    void expeditionSword_NoVanillaRecipe_StaysUnimplemented() {
        assertFalse(
                GRAPH.implemented("craft_expedition_sword"),
                "expedition_sword — не ванильный предмет без рецепта: пометка «в разработке» остаётся"
                        + " Иначе тест expeditionSword_NoVanillaRecipe_StaysUnimplemented перестанет ловить эту поломку. При смене expeditionSword_NoVanillaRecipe_StaysUnimplemented проверь это утверждение.");
        assertTrue(
                VANILLA.ways().stream().noneMatch(way -> "expedition_sword".equals(way.makes())),
                "Ванильного рецепта expedition_sword нет: крафт скриптом craft невозможен"
                        + " Иначе тест expeditionSword_NoVanillaRecipe_StaysUnimplemented перестанет ловить эту поломку. При смене expeditionSword_NoVanillaRecipe_StaysUnimplemented проверь это утверждение.");
    }

    @Test
    void golem_NoPumpkin_NotFeasibleAndStillUnimplemented() {
        GraphPlan plan = golemPlan(0);

        assertTrue(
                plan.task("build_iron_golem").map(task -> !task.feasible()).orElse(false),
                "Без резной тыквы голем не собирается, даже если железные блоки есть: " + plan.task("build_iron_golem")
                        + " Иначе тест golem_NoPumpkin_NotFeasibleAndStillUnimplemented перестанет ловить эту поломку. При смене golem_NoPumpkin_NotFeasibleAndStillUnimplemented проверь это утверждение.");
        assertFalse(
                GRAPH.implemented("build_iron_golem"),
                "Сборки голема у духа нет: пока carve_pumpkin не реализована, путь остаётся «в разработке»"
                        + " Иначе тест golem_NoPumpkin_NotFeasibleAndStillUnimplemented перестанет ловить эту поломку. При смене golem_NoPumpkin_NotFeasibleAndStillUnimplemented проверь это утверждение.");
    }

    @Test
    void golem_WithPumpkin_FeasibleButStillUnimplemented() {
        GraphPlan plan = golemPlan(1);

        assertTrue(
                plan.task("build_iron_golem").map(GraphPlan.Task::feasible).orElse(false),
                "С 4 железными блоками и резной тыквой условие сборки выполнено: " + plan.task("build_iron_golem")
                        + " Иначе тест golem_WithPumpkin_FeasibleButStillUnimplemented перестанет ловить эту поломку. При смене golem_WithPumpkin_FeasibleButStillUnimplemented проверь это утверждение.");
        assertFalse(
                plan.task("build_iron_golem").map(GraphPlan.Task::implemented).orElse(false),
                "Исполнителя сборки голема в игре нет — задание не ставится на доску"
                        + " Иначе тест golem_WithPumpkin_FeasibleButStillUnimplemented перестанет ловить эту поломку. При смене golem_WithPumpkin_FeasibleButStillUnimplemented проверь это утверждение.");
    }

    private static GraphPlan golemPlan(int pumpkins) {
        GraphWorld world = GraphWorld.settled().tools("spirit-1", 3);
        world.stage = 3;
        world.with("iron_block", 4).with("carved_pumpkin", pumpkins);
        return GraphPlanner.plan(GRAPH, world.state().withOrders(Map.of("iron_golem", 1)));
    }
}
