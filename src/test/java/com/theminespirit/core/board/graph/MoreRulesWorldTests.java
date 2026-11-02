package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class MoreRulesWorldTests {
    private static final MoreRules.Player AWAY = new MoreRules.Player(false, false, false);
    private static final MoreRules.Clock DAY = new MoreRules.Clock(false, false);

    @Test
    void forest_TreesLowWithoutSaplings_Signal() {
        MoreRules.Signals signals = withResources(new MoreRules.Resources(2, 0, 4, 10, true));

        assertTrue(
                MoreRules.signals(signals).contains(MoreRules.FOREST_DEPLETED),
                "Деревьев мало, саженцев в земле нет, запас есть — пора восстанавливать лес");
    }

    @Test
    void forest_SaplingsPlanted_NoSignal() {
        MoreRules.Signals signals = withResources(new MoreRules.Resources(2, 3, 4, 10, true));

        assertFalse(
                MoreRules.signals(signals).contains(MoreRules.FOREST_DEPLETED),
                "Саженцы уже растут — повторно не сажаем");
    }

    @Test
    void forest_NoSaplingStock_NoSignal() {
        MoreRules.Signals signals = withResources(new MoreRules.Resources(2, 0, 0, 10, true));

        assertFalse(
                MoreRules.signals(signals).contains(MoreRules.FOREST_DEPLETED),
                "Сажать нечего — сначала нужен запас саженцев");
    }

    @Test
    void ore_MineBuiltWithoutOre_Signal() {
        MoreRules.Signals signals = withResources(new MoreRules.Resources(10, 0, 0, 0, true));

        assertTrue(
                MoreRules.signals(signals).contains(MoreRules.ORE_DEPLETED),
                "Шахта есть, руды рядом нет — искать новый пласт");
    }

    @Test
    void ore_WithoutMine_NoSignal() {
        MoreRules.Signals signals = withResources(new MoreRules.Resources(10, 0, 0, 0, false));

        assertFalse(
                MoreRules.signals(signals).contains(MoreRules.ORE_DEPLETED),
                "Без шахты истощение руды не задача поселения");
    }

    @Test
    void ore_Present_NoSignal() {
        MoreRules.Signals signals = withResources(new MoreRules.Resources(10, 0, 0, 5, true));

        assertFalse(MoreRules.signals(signals).contains(MoreRules.ORE_DEPLETED), "Руда рядом есть — не истощена");
    }

    @Test
    void security_FenceGap_Signal() {
        assertTrue(
                MoreRules.signals(withSecurity(new MoreRules.Security(1, 0, 0, false)))
                        .contains(MoreRules.FENCE_GAP),
                "Пролом ограды — вход защиты поселения");
    }

    @Test
    void security_OpenDoorDay_NoSignal() {
        assertFalse(
                MoreRules.signals(withSecurity(new MoreRules.Security(0, 1, 0, false)))
                        .contains(MoreRules.DOOR_OPEN_NIGHT),
                "Днём открытая дверь не угроза");
    }

    @Test
    void security_OpenDoorNight_Signal() {
        Set<String> signals = MoreRules.signals(withSecurity(new MoreRules.Security(0, 1, 0, true)));

        assertTrue(signals.contains(MoreRules.DOOR_OPEN_NIGHT), "Ночью открытая дверь — вход защиты");
        assertTrue(signals.contains("more:open_doors:1"), "Число открытых дверей передаётся дальше");
    }

    @Test
    void security_Hopper_Signal() {
        assertTrue(
                MoreRules.signals(withSecurity(new MoreRules.Security(0, 0, 1, false)))
                        .contains(MoreRules.HOPPER_UNCOVERED),
                "Открытая воронка — вход защиты поселения");
    }

    @Test
    void reserves_UnprotectedWithSpareArmor_Signal() {
        Set<String> signals = MoreRules.signals(withReserves(new MoreRules.Reserves(1, 2, 0)));

        assertTrue(signals.contains(MoreRules.RESERVE_UNUSED), "Есть запасная броня и дух без неё — поделиться");
        assertTrue(signals.contains("more:spare_armor:2"), "Запас брони передаётся дальше");
    }

    @Test
    void reserves_DuplicateActivities_Signal() {
        Set<String> signals = MoreRules.signals(withReserves(new MoreRules.Reserves(0, 0, 2)));

        assertTrue(signals.contains(MoreRules.WORK_DUPLICATE), "Двое на одной работе — не дублировать");
        assertTrue(signals.contains("more:duplicates:2"), "Число дублей передаётся дальше");
    }

    @Test
    void strategicGoals_GoalsPresent_SignalWithCount() {
        Set<String> signals = MoreRules.signals(withGoals(2));

        assertTrue(signals.contains(MoreRules.STRATEGIC_GOAL), "Цель стратега — вход с весом");
        assertTrue(signals.contains("more:goals:2"), "Число целей передаётся дальше");
    }

    private static MoreRules.Signals withResources(MoreRules.Resources resources) {
        return new MoreRules.Signals(
                AWAY, 0, DAY, 0, 0, new MoreRules.Drops(0, 0), resources, quietSecurity(), quietReserves(), 0);
    }

    private static MoreRules.Signals withSecurity(MoreRules.Security security) {
        return new MoreRules.Signals(
                AWAY, 0, DAY, 0, 0, new MoreRules.Drops(0, 0), okResources(), security, quietReserves(), 0);
    }

    private static MoreRules.Signals withReserves(MoreRules.Reserves reserves) {
        return new MoreRules.Signals(
                AWAY, 0, DAY, 0, 0, new MoreRules.Drops(0, 0), okResources(), quietSecurity(), reserves, 0);
    }

    private static MoreRules.Signals withGoals(int goals) {
        return new MoreRules.Signals(
                AWAY, 0, DAY, 0, 0, new MoreRules.Drops(0, 0), okResources(), quietSecurity(), quietReserves(), goals);
    }

    private static MoreRules.Resources okResources() {
        return new MoreRules.Resources(10, 0, 0, 10, false);
    }

    private static MoreRules.Security quietSecurity() {
        return new MoreRules.Security(0, 0, 0, false);
    }

    private static MoreRules.Reserves quietReserves() {
        return new MoreRules.Reserves(0, 0, 0);
    }
}
