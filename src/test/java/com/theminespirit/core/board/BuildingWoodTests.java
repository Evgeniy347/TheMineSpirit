package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BuildingWoodTests {
    private static final WoodRules RULES = new WoodRules(24);
    private static final String HOUSE = BuildingWoods.key(BoardCase.BUILD_HOUSE, new BlockCoord(0, 64, 0));
    private static final String FENCE = BuildingWoods.key(BoardCase.BUILD_FENCE, new BlockCoord(10, 64, 0));

    @Test
    void choose_TwoBuildingsDifferentStock_EachKeepsOwnSpecies() {
        BuildingWood spruce = BuildingWood.choose(Map.of("spruce_log", 8), Map.of(), "minecraft:plains", RULES);
        BuildingWoods woods = BuildingWoods.NONE.with(HOUSE, spruce);
        BuildingWood birch = BuildingWood.choose(Map.of("birch_planks", 30), Map.of(), "minecraft:plains", RULES);
        woods = woods.with(FENCE, birch);
        assertEquals(
                "spruce",
                woods.of(HOUSE).orElseThrow(),
                "Порода хижины выбирается по её запасу и пишется на неё: иначе хижина и палисадник делят одну породу");
        assertEquals(
                "birch",
                woods.of(FENCE).orElseThrow(),
                "Палисадник, начатый при другом запасе, берёт свою породу: порода решается на постройку");
    }

    @Test
    void with_StockSpentAfterChoice_SpeciesStays() {
        BuildingWoods woods = BuildingWoods.NONE.with(
                HOUSE, BuildingWood.choose(Map.of("spruce_log", 10), Map.of(), "minecraft:plains", RULES));
        BuildingWood later = BuildingWood.choose(
                Map.of("spruce_log", 1, "oak_log", 9), Map.of("oak_log", 20), "minecraft:plains", RULES);
        assertEquals(
                "spruce",
                woods.with(HOUSE, later).of(HOUSE).orElseThrow(),
                "Расход запаса не меняет породу начатой постройки: смена посреди стройки даёт пёструю хижину");
    }

    @Test
    void choose_NoStockNoTrees_FallsToTreesThenBiome() {
        assertEquals(
                "jungle",
                BuildingWood.choose(Map.of("oak_log", 2), Map.of("jungle_log", 5), "minecraft:taiga", RULES)
                        .species(),
                "Мал запас — решают деревья вокруг: рубить придётся их");
        assertEquals(
                "spruce",
                BuildingWood.choose(Map.of(), Map.of(), "minecraft:taiga", RULES)
                        .species(),
                "Нет ни запаса, ни деревьев — решает биом: тайге ель");
    }

    @Test
    void rebuilt_ChosenBuilding_ChoosesAgain() {
        BuildingWoods woods = BuildingWoods.NONE.with(
                HOUSE, BuildingWood.choose(Map.of("spruce_log", 10), Map.of(), "minecraft:plains", RULES));
        BuildingWood birch = BuildingWood.choose(Map.of("birch_log", 10), Map.of(), "minecraft:plains", RULES);
        assertEquals(
                "birch",
                woods.rebuilt(HOUSE).with(HOUSE, birch).of(HOUSE).orElseThrow(),
                "Перестройка снимает выбор: новая хижина выбирает породу заново");
    }
}
