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
        Optional<CampfireMaterials.Cost> cost = CampfireMaterials.cost(Map.of("oak_log", 3, "stick", 3, "coal", 1));

        assertTrue(
                cost.isPresent(),
                "Брёвна, палки и уголь — полный набор для костра, иначе крафт костра не стартует и лагерь "
                        + "остаётся без огня. При смене CampfireMaterials.cost проверь cost_MissingSticks_IsEmpty");
        assertEquals(
                Map.of("oak_log", 3, "stick", 3, "coal", 1),
                cost.get().consumed(),
                "Костёр стоит ровно три бревна, три палки и один уголь: лишнее остаётся в инвентаре, иначе дух "
                        + "тратит лишние брёвна и запас тает. При смене расчёта проверь cost_DifferentLogs_MixesThem");
    }

    @Test
    void cost_DifferentLogs_MixesThem() {
        Optional<CampfireMaterials.Cost> cost =
                CampfireMaterials.cost(Map.of("oak_log", 2, "birch_log", 1, "stick", 3, "charcoal", 1));

        assertTrue(
                cost.isPresent(),
                "Рецепт костра принимает любые брёвна: смесь пород допустима, иначе план костра отвергается на "
                        + "берёзе. При смене тега брёвен проверь cost_NetherStemsAndStrippedWood_AreLogs");
        assertEquals(
                2,
                cost.get().consumed().getOrDefault("oak_log", 0),
                "На костёр уходят именно три бревна: два дуба, иначе расход не совпадает с рецептом и запас "
                        + "тратится неверно. При смене cost проверь consumed ниже в тесте");
        assertEquals(
                1,
                cost.get().consumed().getOrDefault("birch_log", 0),
                "Одна берёза дополняет три бревна без лишнего расхода, иначе смесь считается неверно и рецепт "
                        + "костра ломается. При смене cost проверь строку oak выше в тесте");
        assertEquals(
                1,
                cost.get().consumed().getOrDefault("charcoal", 0),
                "Древесный уголь заменяет обычный в рецепте костра, иначе костёр не собирается с древесным углём. "
                        + "При смене cost проверь cost_NoCoalOrCharcoal_IsEmpty");
    }

    @Test
    void cost_NetherStemsAndStrippedWood_AreLogs() {
        assertTrue(
                CampfireMaterials.isLog("minecraft:crimson_stem"),
                "Незерский стебель — бревно, иначе костёр из него не собрать и материал пропадает. При смене "
                        + "CampfireMaterials.isLog проверь строку досок ниже в тесте");
        assertTrue(
                CampfireMaterials.isLog("stripped_oak_wood"),
                "Обтёсанная древесина — бревно, иначе план костра теряет материал из инвентаря. При смене isLog "
                        + "проверь тег брёвен ниже в тесте");
        assertTrue(
                CampfireMaterials.isLog("bamboo_block"),
                "Бамбуковый блок входит в тег брёвен, иначе бамбук не считается материалом костра. При смене "
                        + "isLog проверь строку досок ниже в тесте");
        assertTrue(
                !CampfireMaterials.isLog("oak_planks"),
                "Доски — не брёвна: костёр из них не собрать, иначе дух строит костёр из досок и портит запас. "
                        + "При смене isLog проверь строки брёвен выше в тесте");
    }

    @Test
    void cost_MissingSticks_IsEmpty() {
        assertEquals(
                Optional.empty(),
                CampfireMaterials.cost(Map.of("oak_log", 3, "stick", 2, "coal", 1)),
                "Двух палок на костёр мало: без полного набора ставить нечего, иначе дух тратит брёвна впустую. "
                        + "При смене cost проверь cost_TooFewLogs_IsEmpty");
    }

    @Test
    void cost_TooFewLogs_IsEmpty() {
        assertEquals(
                Optional.empty(),
                CampfireMaterials.cost(Map.of("oak_log", 2, "stick", 3, "coal", 1)),
                "Двух брёвен на костёр мало: рецепт требует три, иначе костёр собирается неполным набором и "
                        + "крафт врёт. При смене cost проверь cost_MissingSticks_IsEmpty");
    }

    @Test
    void cost_NoCoalOrCharcoal_IsEmpty() {
        assertEquals(
                Optional.empty(),
                CampfireMaterials.cost(Map.of("oak_log", 3, "stick", 3)),
                "Без угля или древесного угля костёр не собрать, иначе рецепт принимает неполный набор и работа "
                        + "встаёт. При смене cost проверь cost_LogsSticksAndCoal_ConsumesVanillaRecipe");
    }

    @Test
    void cost_NullInventory_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CampfireMaterials.cost(null),
                "Материалы без описи не считаются: null означает потерю данных, а не пустой запас, иначе ошибка "
                        + "описи маскируется под «материалов нет». При смене cost проверь эту строку отказа");
    }
}
