package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TownRulesTests {
    @Test
    void load_FieldFlag_MarksOnlyFarmPlots() {
        TownRules rules = TownRules.load();

        assertTrue(
                rules.entry("wheat_field").orElseThrow().field(),
                "Домашняя грядка — площадка поля: защита поля берёт её из плана, а не угадывает по улицам"
                        + " Иначе тест load_FieldFlag_MarksOnlyFarmPlots перестанет ловить эту поломку. При смене load_FieldFlag_MarksOnlyFarmPlots проверь это утверждение.");
        assertTrue(
                rules.entry("field_a").orElseThrow().field(),
                "Пашня field_a — площадка поля"
                        + " Иначе тест load_FieldFlag_MarksOnlyFarmPlots перестанет ловить эту поломку. При смене load_FieldFlag_MarksOnlyFarmPlots проверь это утверждение.");
        assertTrue(
                rules.entry("field_b").orElseThrow().field(),
                "Пашня field_b — площадка поля"
                        + " Иначе тест load_FieldFlag_MarksOnlyFarmPlots перестанет ловить эту поломку. При смене load_FieldFlag_MarksOnlyFarmPlots проверь это утверждение.");
        assertFalse(
                rules.entry("hut").orElseThrow().field(),
                "Хижина — не поле: флаг ставится только пашням"
                        + " Иначе тест load_FieldFlag_MarksOnlyFarmPlots перестанет ловить эту поломку. При смене load_FieldFlag_MarksOnlyFarmPlots проверь это утверждение.");
    }

    @Test
    void load_SlopeMax_FitsLevelingShift() {
        TownRules rules = TownRules.load();

        assertTrue(
                rules.ground().slopeMax() <= PlotLeveling.MAX_SHIFT,
                "Предел уклона площадки " + rules.ground().slopeMax() + " больше сдвига выравнивания "
                        + PlotLeveling.MAX_SHIFT + ": постройка встанет на уступ, который выравнивание уже не снимет"
                        + " Иначе тест load_SlopeMax_FitsLevelingShift перестанет ловить эту поломку. При смене load_SlopeMax_FitsLevelingShift проверь это утверждение.");
    }
}
