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
    void preferredSmeltingStation_NonFoodOnlyCampfire_NothingFound() {
        assertEquals(Optional.empty(), Stations.preferredSmeltingStation("charcoal", Set.of("campfire")),
                "Костёр готовит только еду: непищевой продукт на нём не получится, поэтому станции нет");
    }

    @Test
    void preferredSmeltingStation_NonFoodOnlySmoker_NothingFound() {
        assertEquals(Optional.empty(), Stations.preferredSmeltingStation("glass", Set.of("smoker")),
                "Коптильня готовит только еду: плавить стекло в ней нельзя, подменять печь нельзя");
    }

    @Test
    void preferredSmeltingStation_FoodWithQualifiedId_StillUsesFoodOrder() {
        assertEquals(Optional.of("campfire"),
                Stations.preferredSmeltingStation("minecraft:cooked_beef", Set.of("campfire")),
                "Полное имя продукта с namespace не должно прятать еду: иначе дух не выберет костёр");
    }

    @Test
    void smeltingCandidates_Food_SmokerThenFurnaceThenCampfire() {
        assertEquals(List.of("smoker", "furnace", "campfire"), Stations.smeltingCandidates("cooked_beef"),
                "Порядок станций еды задан правилом: коптильня быстрее, печь обычная, костёр — последний");
    }

    @Test
    void smeltingCandidates_NonFood_FurnaceOnly() {
        assertEquals(List.of("furnace"), Stations.smeltingCandidates("charcoal"),
                "Непищевой продукт плавит только печь: коптильня и костёр еду не заменяют");
    }

    @Test
    void campfireSlots_StationRule_Four() {
        assertEquals(4, Stations.CAMPFIRE_SLOTS,
                "На костре готовится не больше четырёх предметов за раз: больше слотов в ванильном костре нет");
    }

    @Test
    void stationFor_KnowledgeWithoutStations_DefaultsToEmpty() {
        ResourceKnowledge knowledge = new FuelKnowledge();

        assertEquals(Optional.empty(), knowledge.stationFor("wooden_pickaxe"),
                "По умолчанию знания не требуют станций: чужие реализации ResourceKnowledge не должны ломаться и "
                        + "не вправе внезапно получить шаг получения верстака в плане");
    }
}
