package com.theminespirit.core.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CraftPlanTests {
    private static final IngredientSpec PLANKS = IngredientSpec.of("oak_planks");
    private static final IngredientSpec STICK = IngredientSpec.of("stick");
    private static final RecipeSpec STICKS = RecipeSpec.shaped("stick", "stick", 4, 1, 2, List.of(PLANKS, PLANKS));
    private static final RecipeSpec PLANKS_FROM_LOG =
            RecipeSpec.shapeless("oak_planks", "oak_planks", 4, List.of(IngredientSpec.of("oak_log")));
    private static final RecipeSpec PICKAXE = RecipeSpec.shaped(
            "wooden_pickaxe", "wooden_pickaxe", 1, 3, 3, List.of(PLANKS, PLANKS, PLANKS, STICK, STICK));

    @Test
    void of_EightPlanksFromLogs_TwoCraftsConsumeTwoLogs() {
        CraftPlan plan = CraftPlan.of(PLANKS_FROM_LOG, 8, Map.of("oak_log", 2));

        assertEquals(
                2,
                plan.crafts(),
                "8 досок при выходе 4 — ровно 2 крафта: лишний крафт сожжёт бревно зря, иначе запас дерева тает. "
                        + "При смене CraftPlan.of проверь строку required ниже в тесте");
        assertEquals(
                Map.of("oak_log", 2),
                plan.required(),
                "2 крафта по бревну обязаны расходовать 2 бревна, иначе отказ называет неверную нехватку. При смене "
                        + "расчёта в CraftPlan.of проверь строку crafts выше в тесте");
        assertTrue(
                plan.feasible(),
                "С двумя брёвнами в наличии план обязан быть выполним, иначе дух отказывает при полном наборе. При "
                        + "смене CraftPlan.feasible проверь строку нехватки ниже в тесте");
    }

    @Test
    void of_FiveSticksFromPlanks_RoundsCraftsUp() {
        CraftPlan plan = CraftPlan.of(STICKS, 5, Map.of("oak_planks", 4));

        assertEquals(
                2,
                plan.crafts(),
                "5 палок при выходе 4 требуют ceil(5/4) = 2 крафтов, остаток палок остаётся у духа, иначе лишний "
                        + "крафт тратит доски. При смене ceil в CraftPlan.of проверь строку required в этом тесте");
        assertEquals(
                Map.of("oak_planks", 4),
                plan.required(),
                "2 крафта по 2 доски — 4 доски, иначе отказ называет неверную нехватку и дух добывает лишнее. При "
                        + "смене CraftPlan.of проверь строку crafts выше в тесте");
    }

    @Test
    void of_ShortOfPlanks_ReportsMissingWithTotalRequired() {
        CraftPlan plan = CraftPlan.of(PICKAXE, 1, Map.of("oak_planks", 0, "stick", 2));

        assertFalse(
                plan.feasible(),
                "Кирка без досок невыполнима, иначе дух идёт крафтить без материала и падает. При смене "
                        + "CraftPlan.feasible проверь строку missing ниже в тесте");
        assertEquals(
                Map.of("oak_planks", 3),
                plan.missing(),
                "Отказ обязан называть недостающее: 3 доски нужны, 0 есть, иначе доска заказов не показывает "
                        + "причину. При смене CraftPlan.missing проверь строку required ниже в тесте. Фактическое: "
                        + plan.missing());
        assertEquals(
                3,
                plan.required().get("oak_planks"),
                "Отказ вида «не хватает: oak_planks 3 из N» берёт N из required: кирке нужно всего 3 доски, иначе "
                        + "игрок видит неверный знаменатель. При смене required проверь строку missing выше в тесте");
    }

    @Test
    void of_ShortOfSeveralItems_ListsAllMissing() {
        CraftPlan plan = CraftPlan.of(PICKAXE, 1, Map.of("oak_planks", 1));

        assertEquals(
                Map.of("oak_planks", 2, "stick", 2),
                plan.missing(),
                "Отказ обязан перечислять все недостающие предметы сразу, а не по одному за попытку, иначе игрок "
                        + "чинит нехватку итеративно и заказ висит. При смене CraftPlan.of проверь строку одной "
                        + "нехватки выше в тесте");
    }

    @Test
    void of_ScarceItemSharedAcrossCrafts_StillFeasible() {
        RecipeSpec recipe =
                RecipeSpec.shapeless("mix", "dye", 1, List.of(IngredientSpec.of("x", "y"), IngredientSpec.of("x")));

        CraftPlan plan = CraftPlan.of(recipe, 2, Map.of("x", 3, "y", 1));

        assertTrue(
                plan.feasible(),
                "Два крафта из x (3 штуки), y (1 штука) выполнимы только если гибкая клетка берёт y: иначе обе клетки "
                        + "съедят x и дух остановится на втором крафте при наличии материалов. При смене CraftPlan.of "
                        + "проверь строку itemsForCraft ниже в тесте");
    }

    @Test
    void itemsForCraft_TwoCrafts_SlicesAssignmentByCraft() {
        RecipeSpec recipe = RecipeSpec.shapeless(
                "any_plank_dye", "dye", 1, List.of(IngredientSpec.of("oak_planks", "birch_planks")));
        CraftPlan plan = CraftPlan.of(recipe, 2, Map.of("oak_planks", 1, "birch_planks", 1));

        assertEquals(
                List.of("oak_planks"),
                plan.itemsForCraft(0),
                "Первый крафт обязан получить первую часть назначения: ровно столько предметов, сколько мест в "
                        + "рецепте, иначе сетка заполняется лишним. При смене CraftPlan.itemsForCraft проверь строку "
                        + "второго крафта ниже в тесте");
        assertEquals(
                List.of("birch_planks"),
                plan.itemsForCraft(1),
                "Второй крафт обязан получить свою часть назначения, а не повторить первую: иначе дух положит в "
                        + "сетку предмет, которого уже нет. При смене нарезки в itemsForCraft проверь строку первого "
                        + "крафта выше в тесте");
    }

    @Test
    void itemsForCraft_InfeasiblePlan_IsRejected() {
        CraftPlan plan = CraftPlan.of(STICKS, 4, Map.of());

        assertThrows(
                IllegalStateException.class,
                () -> plan.itemsForCraft(0),
                "У невыполнимого плана нет назначения: обращение к нему обязано падать, а не отдавать пустой срез, "
                        + "иначе скрипт крафтит пустую сетку. При смене CraftPlan.itemsForCraft проверь строку двух "
                        + "крафтов выше в тесте");
    }

    @Test
    void of_ZeroCount_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CraftPlan.of(STICKS, 0, Map.of()),
                "Нулевое количество обязано отвергаться: крафт ноль штук не имеет смысла, иначе план из нуля крафтов "
                        + "зависает в очереди. При смене проверки в CraftPlan.of проверь строку восьми досок выше в "
                        + "тесте");
    }
}
