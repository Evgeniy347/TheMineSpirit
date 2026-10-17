package com.theminespirit.core.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CampfireMaterialsTests {
    @Test
    void cost_LogsSticksAndCoal_ConsumesVanillaRecipe() {
        Optional<CampfireMaterials.Cost> cost = CampfireMaterials.cost(
                Map.of("oak_log", 3, "stick", 3, "coal", 1));

        assertTrue(cost.isPresent(), "Брёвна, палки и уголь — полный набор для костра");
        assertEquals(Map.of("oak_log", 3, "stick", 3, "coal", 1), cost.get().consumed(),
                "Костёр стоит ровно три бревна, три палки и один уголь: лишнее остаётся в инвентаре");
    }

    @Test
    void cost_DifferentLogs_MixesThem() {
        Optional<CampfireMaterials.Cost> cost = CampfireMaterials.cost(
                Map.of("oak_log", 2, "birch_log", 1, "stick", 3, "charcoal", 1));

        assertTrue(cost.isPresent(), "Рецепт костра принимает любые брёвна: смесь пород допустима");
        assertEquals(2, cost.get().consumed().getOrDefault("oak_log", 0),
                "На костёр уходят именно три бревна: два дуба");
        assertEquals(1, cost.get().consumed().getOrDefault("birch_log", 0),
                "Одна берёза дополняет три бревна без лишнего расхода");
        assertEquals(1, cost.get().consumed().getOrDefault("charcoal", 0),
                "Древесный уголь заменяет обычный в рецепте костра");
    }

    @Test
    void cost_NetherStemsAndStrippedWood_AreLogs() {
        assertTrue(CampfireMaterials.isLog("minecraft:crimson_stem"), "Незерский стебель — бревно");
        assertTrue(CampfireMaterials.isLog("stripped_oak_wood"), "Обтёсанная древесина — бревно");
        assertTrue(CampfireMaterials.isLog("bamboo_block"), "Бамбуковый блок входит в тег брёвен");
        assertTrue(!CampfireMaterials.isLog("oak_planks"), "Доски — не брёвна: костёр из них не собрать");
    }

    @Test
    void cost_MissingSticks_IsEmpty() {
        assertEquals(Optional.empty(), CampfireMaterials.cost(Map.of("oak_log", 3, "stick", 2, "coal", 1)),
                "Двух палок на костёр мало: без полного набора ставить нечего");
    }

    @Test
    void cost_TooFewLogs_IsEmpty() {
        assertEquals(Optional.empty(), CampfireMaterials.cost(Map.of("oak_log", 2, "stick", 3, "coal", 1)),
                "Двух брёвен на костёр мало: рецепт требует три");
    }

    @Test
    void cost_NoCoalOrCharcoal_IsEmpty() {
        assertEquals(Optional.empty(), CampfireMaterials.cost(Map.of("oak_log", 3, "stick", 3)),
                "Без угля или древесного угля костёр не собрать");
    }

    @Test
    void cost_NullInventory_IsRejected() {
        assertThrows(IllegalArgumentException.class, () -> CampfireMaterials.cost(null),
                "Материалы без описи не считаются: null означает потерю данных, а не пустой запас");
    }
}
