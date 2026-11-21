package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DemandRulesTests {

    @Test
    void load_Classpath_MatchesDefaults() {
        DemandRules rules = DemandRules.load();

        assertEquals(
                1,
                rules.bedsReserve(),
                "Запас кроватей для игрока — число из settlement_demand.json: "
                        + "иначе поселение считает спальные места по-своему"
                        + " При смене load_Classpath_MatchesDefaults проверь это утверждение.");
        assertEquals(
                20,
                rules.storageFreePercentMin(),
                "Порог свободного склада 20% — число из данных"
                        + " Иначе тест load_Classpath_MatchesDefaults перестанет ловить эту поломку. При смене load_Classpath_MatchesDefaults проверь это утверждение.");
        assertEquals(
                1,
                rules.darkCellsMin(),
                "Тёмная клетка — число из данных"
                        + " Иначе тест load_Classpath_MatchesDefaults перестанет ловить эту поломку. При смене load_Classpath_MatchesDefaults проверь это утверждение.");
        assertTrue(
                rules.stageEnabled(),
                "Этап даёт нужду, пока это включено в settlement_demand.json"
                        + " Иначе тест load_Classpath_MatchesDefaults перестанет ловить эту поломку. При смене load_Classpath_MatchesDefaults проверь это утверждение.");
        assertEquals(
                new DemandRules(1, 20, 1, true),
                rules,
                "Классpath " + DemandRules.RESOURCE
                        + " обязан совпадать с кодом: расхождение тихо изменит выбор следующей стройки"
                        + " Иначе тест load_Classpath_MatchesDefaults перестанет ловить эту поломку. При смене load_Classpath_MatchesDefaults проверь это утверждение.");
    }

    @Test
    void next_BedsBelowSpiritsPlusReserve_ReturnsNextHouseLevel() {
        Optional<DemandRules.Need> need = rules().next(facts(3, 3, 10, 100, 1, 0, Map.of(), 0));

        assertEquals(
                Optional.of(new DemandRules.Need(DemandRules.Kind.BEDS, "house", 2)),
                need,
                "Трём духам и игроку нужно 4 кровати (духи + запас из данных): при трёх доска строит дом 2"
                        + " Иначе тест next_BedsBelowSpiritsPlusReserve_ReturnsNextHouseLevel перестанет ловить эту поломку. При смене next_BedsBelowSpiritsPlusReserve_ReturnsNextHouseLevel проверь это утверждение.");
        assertEquals(
                Optional.empty(),
                rules().next(facts(3, 4, 10, 100, 1, 0, Map.of(), 0)),
                "Четыре кровати закрывают нужду: лишний дом не заказывается"
                        + " Иначе тест next_BedsBelowSpiritsPlusReserve_ReturnsNextHouseLevel перестанет ловить эту поломку. При смене next_BedsBelowSpiritsPlusReserve_ReturnsNextHouseLevel проверь это утверждение.");
    }

    @Test
    void next_StorageBelowFreePercentMin_ReturnsNextStorageLevel() {
        Optional<DemandRules.Need> need = rules().next(facts(0, 1, 10, 19, 0, 1, Map.of(), 0));

        assertEquals(
                Optional.of(new DemandRules.Need(DemandRules.Kind.STORAGE, "storage", 2)),
                need,
                "Свободно 19% при пороге 20% из данных — нужен следующий склад, а не ожидание"
                        + " Иначе тест next_StorageBelowFreePercentMin_ReturnsNextStorageLevel перестанет ловить эту поломку. При смене next_StorageBelowFreePercentMin_ReturnsNextStorageLevel проверь это утверждение.");
        assertEquals(
                Optional.empty(),
                rules().next(facts(0, 1, 10, 20, 0, 1, Map.of(), 0)),
                "Ровно 20% свободного склада — порог из данных достигнут, новый склад не нужен"
                        + " Иначе тест next_StorageBelowFreePercentMin_ReturnsNextStorageLevel перестанет ловить эту поломку. При смене next_StorageBelowFreePercentMin_ReturnsNextStorageLevel проверь это утверждение.");
    }

    @Test
    void next_StageMissing_ReturnsFirstBuildingOfStageTable() {
        Map<String, Integer> missing = new LinkedHashMap<>();
        missing.put("barn", 1);
        missing.put("well", 1);

        Optional<DemandRules.Need> need = rules().next(facts(0, 4, 10, 100, 1, 1, missing, 0));

        assertEquals(
                Optional.of(new DemandRules.Need(DemandRules.Kind.STAGE, "barn", 1)),
                need,
                "Остаток этапа идёт по порядку таблицы stages.json: первой берётся недостающая постройка"
                        + " Иначе тест next_StageMissing_ReturnsFirstBuildingOfStageTable перестанет ловить эту поломку. При смене next_StageMissing_ReturnsFirstBuildingOfStageTable проверь это утверждение.");
    }

    @Test
    void next_DarkCellsAtDataThreshold_ReturnsLighting() {
        Optional<DemandRules.Need> need = rules().next(facts(0, 4, 10, 100, 1, 1, Map.of(), 1));

        assertEquals(
                Optional.of(new DemandRules.Need(DemandRules.Kind.LIGHT, "LIGHTING", 1)),
                need,
                "Есть тёмные клетки при пороге из данных — доска ставит свет, иначе двор остаётся тёмным"
                        + " При смене next_DarkCellsAtDataThreshold_ReturnsLighting проверь это утверждение.");
    }

    @Test
    void next_AllNeedsMet_ReturnsNothing() {
        assertEquals(
                Optional.empty(),
                rules().next(facts(0, 4, 10, 100, 1, 1, Map.of(), 0)),
                "Кровати, склад, этап и свет закрыты — доска не выдумывает следующую стройку"
                        + " Иначе тест next_AllNeedsMet_ReturnsNothing перестанет ловить эту поломку. При смене next_AllNeedsMet_ReturnsNothing проверь это утверждение.");
    }

    @Test
    void next_BedsAndStageBothMissing_PrefersBeds() {
        Optional<DemandRules.Need> need = rules().next(facts(3, 3, 10, 100, 1, 1, Map.of("barn", 1), 0));

        assertEquals(
                DemandRules.Kind.BEDS,
                need.map(DemandRules.Need::kind).orElse(null),
                "Порядок нужд из repair.md: спальные места важнее остатка этапа"
                        + " Иначе тест next_BedsAndStageBothMissing_PrefersBeds перестанет ловить эту поломку. При смене next_BedsAndStageBothMissing_PrefersBeds проверь это утверждение.");
    }

    @Test
    void next_StageDisabled_SkipsStageNeed() {
        DemandRules rules = new DemandRules(1, 20, 1, false);

        assertEquals(
                Optional.empty(),
                rules.next(facts(0, 4, 10, 100, 1, 1, Map.of("barn", 1), 0)),
                "Выключенный в данных этап не даёт задания, даже если остаток не пуст"
                        + " Иначе тест next_StageDisabled_SkipsStageNeed перестанет ловить эту поломку. При смене next_StageDisabled_SkipsStageNeed проверь это утверждение.");
    }

    @Test
    void parse_UnknownField_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> DemandRules.parse("""
                                {"bedsReserve": 1, "storageFreePercentMin": 20, "darkCellsMin": 1,
                                 "stageEnabled": true, "beauty": true}""", "test"),
                "Неизвестное поле правил нужды обязано отклоняться: опечатка сделала бы правило мёртвым"
                        + " Иначе тест parse_UnknownField_Throws перестанет ловить эту поломку. При смене parse_UnknownField_Throws проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("beauty"),
                "Ошибка обязана называть неизвестное поле. Фактическое: " + error.getMessage()
                        + " Иначе тест parse_UnknownField_Throws перестанет ловить эту поломку. При смене parse_UnknownField_Throws проверь это утверждение.");
    }

    @Test
    void parse_StoragePercentOutsideRange_Throws() {
        assertThrows(
                IllegalStateException.class,
                () -> DemandRules.parse("""
                                {"bedsReserve": 1, "storageFreePercentMin": 101, "darkCellsMin": 1,
                                 "stageEnabled": true}""", "test"),
                "Порог свободного склада выше 100% не наступит: файл обязан отклоняться"
                        + " Иначе тест parse_StoragePercentOutsideRange_Throws перестанет ловить эту поломку. При смене parse_StoragePercentOutsideRange_Throws проверь это утверждение.");
    }

    private static DemandRules rules() {
        return DemandRules.load();
    }

    private static DemandRules.Facts facts(
            int spirits,
            int beds,
            int storageSlots,
            int storageFreePercent,
            int houseLevel,
            int storageLevel,
            Map<String, Integer> stageMissing,
            int darkCells) {
        return new DemandRules.Facts(
                spirits, beds, storageSlots, storageFreePercent, houseLevel, storageLevel, stageMissing, darkCells);
    }
}
