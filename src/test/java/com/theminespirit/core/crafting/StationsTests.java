package com.theminespirit.core.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.planner.ResourceKnowledge;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StationsTests {
    private static final IngredientSpec PLANKS = IngredientSpec.of("oak_planks");

    @Test
    void forRecipe_ThreeByThreeCraft_CraftingTable() {
        RecipeSpec pickaxe = RecipeSpec.shaped("wooden_pickaxe", "wooden_pickaxe", 1, 3, 3,
                List.of(PLANKS, PLANKS, PLANKS, IngredientSpec.of("stick"), IngredientSpec.of("stick")));

        assertEquals(Optional.of("crafting_table"), Stations.forRecipe(pickaxe),
                "Рецепт 3×3 требует верстак: имя станции совпадает с id блока в игре, по нему станцию ищут в мире");
    }

    @Test
    void forRecipe_InventoryCraft_NoStation() {
        RecipeSpec sticks = RecipeSpec.shaped("stick", "stick", 4, 1, 2, List.of(PLANKS, PLANKS));

        assertEquals(Optional.empty(), Stations.forRecipe(sticks),
                "Рецепт 2×2 идёт в инвентаре духа: станция не нужна, иначе резолвер строит верстак ради палок");
    }

    @Test
    void forRecipe_Smelting_Furnace() {
        RecipeSpec smelt = RecipeSpec.smelting("iron_ingot", "iron_ingot", 1, IngredientSpec.of("raw_iron"), 200);

        assertEquals(Optional.of("furnace"), Stations.forRecipe(smelt),
                "Плавке нужна обычная печь: доменная и коптильня в этой версии не используются");
    }

    @Test
    void preferredSmeltingStation_FoodWithSmoker_PrefersSmoker() {
        assertEquals(Optional.of("smoker"), Stations.preferredSmeltingStation("cooked_beef", Set.of("furnace", "smoker")),
                "Коптильня быстрее готовит еду: при её наличии дух обязан выбрать её, а не обычную печь");
    }

    @Test
    void preferredSmeltingStation_FoodWithoutSmoker_FallsBackToFurnace() {
        assertEquals(Optional.of("furnace"), Stations.preferredSmeltingStation("cooked_beef", Set.of("furnace")),
                "Без коптильни готовая еда идёт в обычную печь: иначе дух стоит без дела при пустом костре");
    }

    @Test
    void preferredSmeltingStation_FoodOnlyCampfire_UsesCampfire() {
        assertEquals(Optional.of("campfire"), Stations.preferredSmeltingStation("cooked_beef", Set.of("campfire")),
                "Без печи и коптильни еду готовят на костре: иначе дух не может накормить поселение в поле");
    }

    @Test
    void preferredSmeltingStation_NonFood_PrefersFurnaceOverSmoker() {
        assertEquals(Optional.of("furnace"), Stations.preferredSmeltingStation("charcoal", Set.of("furnace", "smoker")),
                "Уголь и руда плавятся в обычной печи: коптильня для еды, её не используют ради угля");
    }

    @Test
    void stationFor_KnowledgeWithoutStations_DefaultsToEmpty() {
        ResourceKnowledge knowledge = new FuelKnowledge();

        assertEquals(Optional.empty(), knowledge.stationFor("wooden_pickaxe"),
                "По умолчанию знания не требуют станций: чужие реализации ResourceKnowledge не должны ломаться и "
                        + "не вправе внезапно получить шаг получения верстака в плане");
    }
}
