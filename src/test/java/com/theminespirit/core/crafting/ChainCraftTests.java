package com.theminespirit.core.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChainCraftTests {
    private static final IngredientSpec ANY_PLANKS = IngredientSpec.of("oak_planks", "birch_planks");
    private static final RecipeSpec OAK_PLANKS =
            RecipeSpec.shapeless("oak_planks", "oak_planks", 4, List.of(IngredientSpec.of("oak_log", "oak_wood")));
    private static final RecipeSpec BIRCH_PLANKS =
            RecipeSpec.shapeless("birch_planks", "birch_planks", 4, List.of(IngredientSpec.of("birch_log")));
    private static final RecipeSpec STICKS =
            RecipeSpec.shaped("stick", "stick", 4, 1, 2, List.of(ANY_PLANKS, ANY_PLANKS));
    private static final RecipeSpec TORCHES = RecipeSpec.shaped(
            "torch", "torch", 4, 1, 2, List.of(IngredientSpec.of("coal", "charcoal"), IngredientSpec.of("stick")));
    private static final RecipeIndex INDEX = RecipeIndex.of(List.of(OAK_PLANKS, BIRCH_PLANKS, STICKS, TORCHES));

    @Test
    void ofItem_LogAndCoal_TorchesWithoutExplicitPlanksAndSticks() {
        ChainPlan plan = ChainCraft.ofItem(INDEX, "torch", 4, Map.of("oak_log", 1, "coal", 1))
                .orElseThrow();

        assertTrue(plan.feasible(), "Бревно и уголь дают факелы через доски и палки: план обязан быть выполним");
        assertEquals(
                List.of("oak_planks", "stick", "torch"),
                plan.steps().stream().map(step -> step.recipe().id()).toList(),
                "Шаги идут от сырья к цели: доски, палки, факелы — иначе крафт палок не найдёт досок");
        assertEquals(
                Map.of("oak_log", 1, "coal", 1),
                plan.consumed(),
                "Из поселения списываются только бревно и уголь: промежуточные делаются по пути");
        assertEquals(4, plan.targetCount(), "Один крафт факелов даёт 4 факела");
        assertEquals(
                Map.of("oak_planks", 2, "stick", 3),
                plan.leftovers(),
                "Остаток: 4 доски − 2 на палки = 2, 4 палки − 1 на факелы = 3; они уходят игроку, а не пропадают");
    }

    @Test
    void ofItem_SticksInStock_UsesThemWithoutCraftingPlanks() {
        ChainPlan plan = ChainCraft.ofItem(INDEX, "torch", 8, Map.of("stick", 2, "coal", 2, "oak_log", 5))
                .orElseThrow();

        assertEquals(1, plan.steps().size(), "Готовые палки берутся из запаса: лишний крафт досок жжёт брёвна");
        assertEquals(Map.of("stick", 2, "coal", 2), plan.consumed(), "8 факелов — 2 палки и 2 угля, брёвна целы");
    }

    @Test
    void ofItem_MoreBirchThanOak_ChoosesBirchRoute() {
        ChainPlan plan = ChainCraft.ofItem(INDEX, "stick", 4, Map.of("oak_log", 1, "birch_log", 3))
                .orElseThrow();

        assertEquals(
                Map.of("birch_log", 1),
                plan.consumed(),
                "Маршрут выбирается детерминированно — сначала то, чего больше в наличии: берёзы 3 против 1 дуба");
    }

    @Test
    void ofItem_NoLogs_ReportsRawShortage() {
        ChainPlan plan = ChainCraft.ofItem(INDEX, "torch", 4, Map.of()).orElseThrow();

        assertFalse(plan.feasible(), "Без сырья факелы не сделать");
        assertEquals(
                Map.of("coal", 1, "oak_log", 1),
                plan.missing(),
                "Нехватка считается до сырья (бревно, уголь), а не до досок: по ней ставятся задания духам");
        assertEquals(Map.of(), plan.consumed(), "Занятое в долг не списывается со склада");
    }

    @Test
    void ofItem_NothingStocked_BorrowsRawLogNotWoodBlock() {
        RecipeSpec wood = RecipeSpec.shaped(
                "oak_wood",
                "oak_wood",
                3,
                2,
                2,
                List.of(
                        IngredientSpec.of("oak_log"),
                        IngredientSpec.of("oak_log"),
                        IngredientSpec.of("oak_log"),
                        IngredientSpec.of("oak_log")));
        RecipeSpec planks =
                RecipeSpec.shapeless("oak_planks", "oak_planks", 4, List.of(IngredientSpec.of("oak_wood", "oak_log")));
        RecipeIndex index = RecipeIndex.of(List.of(wood, planks, BIRCH_PLANKS, STICKS, TORCHES));

        ChainPlan plan = ChainCraft.ofItem(index, "torch", 4, Map.of("coal", 1)).orElseThrow();

        assertEquals(
                Map.of("oak_log", 1),
                plan.missing(),
                "При пустом складе нехватка — сырьё (1 бревно), а не 4 бревна ради блока древесины: "
                        + "незапасённый рецепт идёт после сырья, иначе духи добывают вчетверо больше");
    }

    @Test
    void ofItem_NoRecipe_ReturnsEmpty() {
        assertTrue(
                ChainCraft.ofItem(INDEX, "diamond", 1, Map.of()).isEmpty(),
                "Предмет без рецепта верстака многошагово не делается: план пуст, а не выдуман");
    }

    @Test
    void ofRecipe_CyclicRecipes_TerminatesWithShortage() {
        RecipeSpec first = RecipeSpec.shapeless("a", "a", 1, List.of(IngredientSpec.of("b")));
        RecipeSpec second = RecipeSpec.shapeless("b", "b", 1, List.of(IngredientSpec.of("a")));

        ChainPlan plan = ChainCraft.ofRecipe(RecipeIndex.of(List.of(first, second)), first, 1, Map.of());

        assertEquals(Map.of("b", 1), plan.missing(), "Цикл a↔b обрывается защитой пути, а не уходит в рекурсию");
    }

    @Test
    void ofRecipe_ChainDeeperThanLimit_IsInfeasible() {
        List<RecipeSpec> chain = new ArrayList<>();
        for (int level = 1; level <= ChainCraft.MAX_DEPTH + 3; level++) {
            chain.add(RecipeSpec.shapeless("i" + level, "i" + level, 1, List.of(IngredientSpec.of("i" + (level - 1)))));
        }
        RecipeSpec top = chain.get(chain.size() - 1);

        ChainPlan plan = ChainCraft.ofRecipe(RecipeIndex.of(chain), top, 1, Map.of("i0", 1));

        assertFalse(
                plan.feasible(),
                "Цепочка глубже MAX_DEPTH=" + ChainCraft.MAX_DEPTH + " не разворачивается: предел держит время тика");
    }

    @Test
    void maxCrafts_CoalLimits_TenCrafts() {
        int crafts = ChainCraft.maxCrafts(INDEX, TORCHES, Map.of("oak_log", 2, "coal", 10), 16);

        assertEquals(10, crafts, "2 бревна дают 16 палок, но угля 10 — предел 10 крафтов факелов");
    }

    @Test
    void ofRecipe_Smelting_Throws() {
        RecipeSpec smelt = RecipeSpec.smelting("ingot", "iron_ingot", 1, IngredientSpec.of("raw_iron"), 200);

        assertThrows(
                IllegalArgumentException.class,
                () -> ChainCraft.ofRecipe(INDEX, smelt, 1, Map.of()),
                "Плавка не крафт верстака: цепочка обязана отказать, а не сделать слиток мгновенно");
    }

    @Test
    void ofRecipe_FeasiblePlan_ConsumesNoMoreThanStock() {
        Map<String, Integer> stock = new HashMap<>(Map.of("oak_log", 3, "birch_log", 2, "coal", 7, "stick", 1));

        ChainPlan plan = ChainCraft.ofRecipe(INDEX, TORCHES, 7, stock);

        assertTrue(plan.feasible(), "7 крафтов факелов выполнимы из 5 брёвен, 7 угля и палки");
        plan.consumed()
                .forEach((item, count) -> assertTrue(
                        count <= stock.getOrDefault(item, 0),
                        "Списание " + item + " ×" + count + " больше наличия: склад ушёл бы в минус"));
        assertEquals(28, plan.produced().get("torch"), "7 крафтов по 4 факела");
    }
}
