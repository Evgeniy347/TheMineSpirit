package com.theminespirit.core.sleep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SleepRuleTests {
    @Test
    void isNight_FractionAtThreshold_IsNight() {
        assertTrue(SleepRule.isNight(0.52),
                "Доля суток на пороге ночи — уже ночь: граница обязана сработать на этом же тике");
    }

    @Test
    void nightDayFraction_Default_MatchesDesign() {
        assertEquals(0.52, SleepRule.NIGHT_DAY_FRACTION,
                "Порог ночи — 0.52 (после вечернего 0.5): смена числа здесь сдвинула бы момент, когда духи ложатся, "
                        + "и тест это стережёт");
    }

    @Test
    void isNight_FractionBelowThreshold_IsDay() {
        assertFalse(SleepRule.isNight(0.51),
                "До порога ночи ещё день: иначе дух ложится спать на закате");
    }

    @Test
    void isNight_NightFractionFromGameTest_IsNight() {
        assertTrue(SleepRule.isNight(SleepRule.dayFraction(13000L)),
                "13000 тиков (ночь в GameTest) обязаны быть ночью для правила сна");
    }

    @Test
    void isNight_NoonFractionFromGameTest_IsDay() {
        assertFalse(SleepRule.isNight(SleepRule.dayFraction(6000L)),
                "6000 тиков (полдень в GameTest) обязаны быть днём: иначе дух спит среди дня");
    }

    @Test
    void dayFraction_TimePastFullDay_WrapsIntoZeroOne() {
        assertEquals(0.25, SleepRule.dayFraction(24_000L + 6000L),
                "Доля суток обязана считаться по остатку от суток: иначе второй день сдвинет порог ночи");
    }

    @Test
    void isNight_FractionOutsideZeroOne_Throws() {
        assertThrows(IllegalArgumentException.class, () -> SleepRule.isNight(1.5),
                "Доля суток вне 0..1 означала бы непонятное время дня: ошибка обязана быть явной");
    }

    @Test
    void timeToSleep_NightCalmInsideNoOrder_IsTrue() {
        assertTrue(SleepRule.timeToSleep(new SleepFacts(true, false, true, false)),
                "Ночью, без опасности, внутри поселения и не в обороне дух обязан спать");
    }

    @Test
    void timeToSleep_Day_IsFalse() {
        assertFalse(SleepRule.timeToSleep(new SleepFacts(false, false, true, false)),
                "Днём спать нельзя: иначе дух ложится вместо работы");
    }

    @Test
    void timeToSleep_Danger_IsFalse() {
        assertFalse(SleepRule.timeToSleep(new SleepFacts(true, true, true, false)),
                "Под угрозой или в бою не спят: иначе духа застанут врасплох в кровати");
    }

    @Test
    void timeToSleep_OutsideSettlement_IsFalse() {
        assertFalse(SleepRule.timeToSleep(new SleepFacts(true, false, false, false)),
                "Вне поселения не спят: кроватей там нет, а ночной поводок держит духа внутри");
    }

    @Test
    void timeToSleep_AssignedDefence_IsFalse() {
        assertFalse(SleepRule.timeToSleep(new SleepFacts(true, false, true, true)),
                "Назначенный на оборону не спит: место часового ночью не может быть пустым");
    }

    @Test
    void timeToSleep_NullFacts_Throws() {
        assertThrows(NullPointerException.class, () -> SleepRule.timeToSleep(null),
                "Правило без фактов сравнивать нечего: ошибка обязана быть явной, а не молчаливым сном");
    }
}
