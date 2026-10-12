package com.theminespirit.core.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.planner.ResourceKnowledge;
import java.util.List;
import java.util.Optional;
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
    void stationFor_KnowledgeWithoutStations_DefaultsToEmpty() {
        ResourceKnowledge knowledge = new FuelKnowledge();

        assertEquals(Optional.empty(), knowledge.stationFor("wooden_pickaxe"),
                "По умолчанию знания не требуют станций: чужие реализации ResourceKnowledge не должны ломаться и "
                        + "не вправе внезапно получить шаг получения верстака в плане");
    }
}
