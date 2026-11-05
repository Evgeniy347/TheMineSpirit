package com.theminespirit.core.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CraftAssemblyTests {
    private static final List<String> SPECIES = List.of("jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo");

    @ParameterizedTest
    @MethodSource("speciesLogs")
    void plan_WoodenPickaxeFromOneSpeciesLogs_CraftsPlanksAndSticksFirst(String species, String log) {
        Optional<List<CraftAssembly.Order>> plan = CraftAssembly.plan("wooden_pickaxe", 1, Map.of(log, 2), index());

        assertTrue(
                plan.isPresent(),
                "Кирка из брёвен породы '" + species + "' обязана собираться цепочкой: доски и палки крафтятся "
                        + "из брёвен автоматически, иначе craft падает «не хватает: " + species + "_planks» "
                        + "(INC-13)");
        List<CraftAssembly.Order> orders = plan.orElseThrow();
        assertTrue(
                orders.stream().anyMatch(order -> order.recipe().outputItem().equals(species + "_planks")),
                "Цепочка обязана содержать крафт досок породы '" + species + "': иначе промежуточный предмет "
                        + "требуется готовым, а в инвентаре только брёвна");
        assertTrue(
                orders.stream().anyMatch(order -> "stick".equals(order.recipe().outputItem())),
                "Цепочка обязана содержать крафт палок из досок: иначе кирку не из чего собрать");
        CraftAssembly.Order last = orders.get(orders.size() - 1);
        assertEquals(
                "wooden_pickaxe",
                last.recipe().outputItem(),
                "Последний шаг цепочки обязан выпускать целевой предмет: иначе дух тратит брёвна и не получает кирку");
        assertTrue(
                last.items().contains(species + "_planks"),
                "Финальный рецепт обязан принять доски той породы, что была в брёвнах, а не oak/spruce/birch");
        assertTrue(
                last.items().contains("stick"), "Финальный рецепт обязан принять палки, скрафченные в этой же цепочке");
    }

    @Test
    void plan_PickaxeAlternatives_AcceptEveryPlankSpecies() {
        RecipeSpec pickaxe = index().craftsFor("wooden_pickaxe").get(0);
        List<String> planks = pickaxe.ingredients().get(0).alternatives();

        for (String species : SPECIES) {
            assertTrue(
                    planks.contains(species + "_planks"),
                    "В альтернативах досок рецепта обязана быть порода '" + species + "': список из трёх пород "
                            + "оставляет джунглевые и остальные брёвна без применения");
        }
    }

    @Test
    void plan_NoRecipeForItem_ReturnsEmpty() {
        Optional<List<CraftAssembly.Order>> plan =
                CraftAssembly.plan("diamond_pickaxe", 1, Map.of("jungle_log", 8), index());

        assertFalse(
                plan.isPresent(),
                "Без рецепта цепочка не выдумывается: отказ обязан назвать нехватку, а не подставить чужой предмет");
    }

    @Test
    void plan_TwoCraftsOfPlanks_KeepsEnoughForSticks() {
        Optional<List<CraftAssembly.Order>> plan =
                CraftAssembly.plan("wooden_pickaxe", 1, Map.of("jungle_log", 1), index());

        assertFalse(
                plan.isPresent(),
                "Одного бревна (4 доски) не хватает на 3 доски кирки и 2 доски палок: цепочка обязана считать "
                        + "расход промежуточных досок, а не только нехватку финального рецепта");
    }

    @Test
    void plan_TwoLogs_AreEnoughForPickaxe() {
        Optional<List<CraftAssembly.Order>> plan =
                CraftAssembly.plan("wooden_pickaxe", 1, Map.of("jungle_log", 2), index());

        assertEquals(
                2,
                plan.orElseThrow().stream()
                        .filter(order -> "jungle_planks".equals(order.recipe().outputItem()))
                        .count(),
                "На 3 доски кирки и 2 доски палок нужно два крафта досок из двух брёвен: иначе цепочка "
                        + "переиспользует уже потраченные доски");
    }

    private static Stream<Arguments> speciesLogs() {
        return SPECIES.stream().map(species -> Arguments.of(species, logOf(species)));
    }

    private static String logOf(String species) {
        return "bamboo".equals(species) ? "bamboo_block" : species + "_log";
    }

    private static RecipeIndex index() {
        List<RecipeSpec> recipes = new ArrayList<>();
        List<String> planks = new ArrayList<>();
        for (String species : SPECIES) {
            planks.add(species + "_planks");
            recipes.add(RecipeSpec.shapeless(
                    "minecraft:" + species + "_planks",
                    species + "_planks",
                    4,
                    List.of(IngredientSpec.of(logOf(species)))));
        }
        IngredientSpec anyPlanks = new IngredientSpec(planks);
        recipes.add(RecipeSpec.shapeless("minecraft:stick", "stick", 4, List.of(anyPlanks, anyPlanks)));
        recipes.add(RecipeSpec.shaped(
                "minecraft:wooden_pickaxe",
                "wooden_pickaxe",
                1,
                3,
                3,
                List.of(anyPlanks, anyPlanks, anyPlanks, IngredientSpec.of("stick"), IngredientSpec.of("stick"))));
        return RecipeIndex.of(recipes);
    }
}
