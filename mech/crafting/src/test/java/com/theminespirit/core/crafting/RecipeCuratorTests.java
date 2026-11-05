package com.theminespirit.core.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RecipeCuratorTests {
    private static final int COOK = 200;

    private static RecipeSpec smelting(String id, String product, String input) {
        return RecipeSpec.smelting(id, product, 1, IngredientSpec.of(input), COOK);
    }

    private static RecipeSpec compression(String id, String block, String piece) {
        return RecipeSpec.shaped(id, block, 1, 3, 3, nine(piece));
    }

    private static RecipeSpec decompression(String id, String piece, String block) {
        return RecipeSpec.shapeless(id, piece, 9, List.of(IngredientSpec.of(block)));
    }

    private static List<IngredientSpec> nine(String piece) {
        List<IngredientSpec> slots = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            slots.add(IngredientSpec.of(piece));
        }
        return slots;
    }

    private static Set<String> ids(List<RecipeSpec> recipes) {
        return recipes.stream().map(RecipeSpec::id).collect(Collectors.toSet());
    }

    private static List<RecipeSpec> ironFamily() {
        return List.of(
                smelting("iron_ingot_from_smelting_raw_iron", "iron_ingot", "raw_iron"),
                RecipeSpec.shaped("iron_ingot_from_nuggets", "iron_ingot", 1, 3, 3, nine("iron_nugget")),
                decompression("iron_ingot_from_iron_block", "iron_ingot", "iron_block"),
                compression("iron_block", "iron_block", "iron_ingot"),
                decompression("iron_nugget", "iron_nugget", "iron_ingot"));
    }

    @Test
    void curate_ItemWithSmeltingRecipe_CraftingRecipesOfItemAreDropped() {
        List<RecipeSpec> curated = RecipeCurator.curate(ironFamily());

        assertFalse(
                ids(curated).contains("iron_ingot_from_nuggets"),
                "Слиток железа получают плавкой, а не из 9 самородков: иначе резолвер зациклится слиток → самородок "
                        + "→ слиток и никогда не пойдёт в печь. Осталось: " + ids(curated));
        assertFalse(
                ids(curated).contains("iron_ingot_from_iron_block"),
                "Слиток из блока не должен считаться способом получения слитка, если у слитка есть плавка. "
                        + "Осталось: " + ids(curated));
        assertTrue(
                ids(curated).contains("iron_ingot_from_smelting_raw_iron"),
                "Рецепт плавки обязан остаться: он и есть способ получить слиток. Осталось: " + ids(curated));
    }

    @Test
    void curate_IronFamily_KeepsCompressionAndNuggetsFromIngots() {
        List<RecipeSpec> curated = RecipeCurator.curate(ironFamily());

        assertEquals(
                Set.of("iron_ingot_from_smelting_raw_iron", "iron_block", "iron_nugget"),
                ids(curated),
                "Блок железа из 9 слитков и самородки из слитка обязаны остаться: это единственный путь к ним");
    }

    @Test
    void curate_GoldFamilyWithoutSmelting_DropsDecompressionOfIngotAndBlock() {
        List<RecipeSpec> recipes = List.of(
                RecipeSpec.shaped("gold_ingot_from_nuggets", "gold_ingot", 1, 3, 3, nine("gold_nugget")),
                compression("gold_block", "gold_block", "gold_ingot"),
                decompression("gold_ingot_from_gold_block", "gold_ingot", "gold_block"),
                decompression("gold_nugget", "gold_nugget", "gold_ingot"));

        Set<String> kept = ids(RecipeCurator.curate(recipes));

        assertEquals(
                Set.of("gold_ingot_from_nuggets", "gold_block"),
                kept,
                "Разжатие (блок → 9 слитков, слиток → 9 самородков), у которого есть обратная сборка, обязано "
                        + "отбрасываться, сжатие — оставаться: иначе золото получают из ничего по кругу");
    }

    @Test
    void curate_CoalAndCoalBlock_DropsBlockToCoal() {
        List<RecipeSpec> recipes =
                List.of(compression("coal_block", "coal_block", "coal"), decompression("coal", "coal", "coal_block"));

        Set<String> kept = ids(RecipeCurator.curate(recipes));

        assertEquals(
                Set.of("coal_block"),
                kept,
                "Уголь не должен добываться из блока угля, если блок собирается из угля: резолвер ушёл бы в цикл");
    }

    @Test
    void curate_LogToPlanksAndPlanksToSticks_NothingDropped() {
        List<RecipeSpec> recipes = List.of(
                RecipeSpec.shapeless("oak_planks", "oak_planks", 4, List.of(IngredientSpec.of("oak_log", "oak_wood"))),
                RecipeSpec.shaped(
                        "stick",
                        "stick",
                        4,
                        1,
                        2,
                        List.of(IngredientSpec.of("oak_planks"), IngredientSpec.of("oak_planks"))),
                RecipeSpec.shaped(
                        "stick_from_bamboo",
                        "stick",
                        1,
                        1,
                        2,
                        List.of(IngredientSpec.of("bamboo"), IngredientSpec.of("bamboo"))));

        List<RecipeSpec> curated = RecipeCurator.curate(recipes);

        assertEquals(
                recipes,
                curated,
                "Бревно → доски (1 → 4) и доски → палки не имеют обратных рецептов: куратор не вправе их трогать, "
                        + "иначе у дерева пропадёт путь к палкам");
    }

    @Test
    void curate_RecipeConsumingItsOwnResult_IsDropped() {
        RecipeSpec selfFed = RecipeSpec.shapeless(
                "dye_mix", "red_dye", 2, List.of(IngredientSpec.of("red_dye", "pink_dye"), IngredientSpec.of("poppy")));
        RecipeSpec fine = RecipeSpec.shapeless("red_dye_from_poppy", "red_dye", 1, List.of(IngredientSpec.of("poppy")));

        Set<String> kept = ids(RecipeCurator.curate(List.of(selfFed, fine)));

        assertEquals(
                Set.of("red_dye_from_poppy"),
                kept,
                "Рецепт, которому нужен собственный результат, обязан отбрасываться: он не создаёт предмет из "
                        + "сырья, а только зацикливает резолвер");
    }

    @Test
    void curate_UnrelatedRecipes_AreKeptInOrder() {
        List<RecipeSpec> recipes = List.of(
                RecipeSpec.shapeless("a", "item_a", 1, List.of(IngredientSpec.of("raw_a"))),
                smelting("b", "item_b", "raw_b"),
                RecipeSpec.shapeless("c", "item_c", 2, List.of(IngredientSpec.of("raw_c"))));

        assertEquals(
                recipes,
                RecipeCurator.curate(recipes),
                "Правило (г): рецепты, не подпавшие под отбор, остаются без изменений и в прежнем порядке");
    }
}
