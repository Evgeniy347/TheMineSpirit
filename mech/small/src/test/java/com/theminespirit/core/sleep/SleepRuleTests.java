package com.theminespirit.core.sleep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SleepRuleTests {
    @Test
    void isNight_FractionAtThreshold_IsNight() {
        assertTrue(
                SleepRule.isNight(0.52),
                "Доля суток на пороге ночи — уже ночь: граница обязана сработать на этом же тике, иначе ночь "
                        + "наступает позже. При смене SleepRule.isNight проверь строку порога ниже в тесте");
    }

    @Test
    void nightDayFraction_Default_MatchesDesign() {
        assertEquals(
                0.52,
                SleepRule.NIGHT_DAY_FRACTION,
                "Порог ночи — 0.52 (после вечернего 0.5): смена числа здесь сдвинула бы момент, когда духи ложатся, "
                        + "иначе поведение меняется молча. При смене NIGHT_DAY_FRACTION проверь строку ниже в тесте");
    }

    @Test
    void isNight_FractionBelowThreshold_IsDay() {
        assertFalse(
                SleepRule.isNight(0.51),
                "До порога ночи ещё день, иначе дух ложится спать на закате и пропускает вечернюю работу. При смене "
                        + "порога проверь строку 13000 ниже в тесте");
    }

    @Test
    void isNight_NightFractionFromGameTest_IsNight() {
        assertTrue(
                SleepRule.isNight(SleepRule.dayFraction(13000L)),
                "13000 тиков (ночь в GameTest) обязаны быть ночью для правила сна, иначе GameTest сон не проверяет. "
                        + "При смене dayFraction проверь строку полудня ниже в тесте");
    }

    @Test
    void isNight_NoonFractionFromGameTest_IsDay() {
        assertFalse(
                SleepRule.isNight(SleepRule.dayFraction(6000L)),
                "6000 тиков (полдень в GameTest) обязаны быть днём, иначе дух спит среди дня. При смене isNight "
                        + "проверь строку остатка ниже в тесте");
    }

    @Test
    void dayFraction_TimePastFullDay_WrapsIntoZeroOne() {
        assertEquals(
                0.25,
                SleepRule.dayFraction(24_000L + 6000L),
                "Доля суток обязана считаться по остатку от суток, иначе второй день сдвинет порог ночи. При смене "
                        + "dayFraction проверь строку вне диапазона ниже в тесте");
    }

    @Test
    void isNight_FractionOutsideZeroOne_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SleepRule.isNight(1.5),
                "Доля суток вне 0..1 означала бы непонятное время дня: ошибка обязана быть явной, иначе время молча "
                        + "считается днём. При смене isNight проверь строку сна ниже в тесте");
    }

    @Test
    void timeToSleep_NightCalmInsideNoOrder_IsTrue() {
        assertTrue(
                SleepRule.timeToSleep(new SleepFacts(true, false, true, false)),
                "Ночью, без опасности, внутри поселения и не в обороне дух обязан спать, иначе он не отдыхает. При "
                        + "смене timeToSleep проверь строку дня ниже в тесте");
    }

    @Test
    void timeToSleep_Day_IsFalse() {
        assertFalse(
                SleepRule.timeToSleep(new SleepFacts(false, false, true, false)),
                "Днём спать нельзя, иначе дух ложится вместо работы. При смене timeToSleep проверь строку опасности "
                        + "ниже в тесте");
    }

    @Test
    void timeToSleep_Danger_IsFalse() {
        assertFalse(
                SleepRule.timeToSleep(new SleepFacts(true, true, true, false)),
                "Под угрозой или в бою не спят, иначе духа застанут врасплох в кровати. При смене проверки опасности "
                        + "проверь строку вне поселения ниже в тесте");
    }

    @Test
    void timeToSleep_OutsideSettlement_IsFalse() {
        assertFalse(
                SleepRule.timeToSleep(new SleepFacts(true, false, false, false)),
                "Вне поселения не спят: кроватей там нет, а ночной поводок держит духа внутри, иначе сон за границей. "
                        + "При смене timeToSleep проверь строку обороны ниже в тесте");
    }

    @Test
    void timeToSleep_AssignedDefence_IsFalse() {
        assertFalse(
                SleepRule.timeToSleep(new SleepFacts(true, false, true, true)),
                "Назначенный на оборону не спит: место часового ночью не может быть пустым, иначе поселение без "
                        + "охраны. При смене timeToSleep проверь строку null ниже в тесте");
    }

    @Test
    void timeToSleep_NullFacts_Throws() {
        assertThrows(
                NullPointerException.class,
                () -> SleepRule.timeToSleep(null),
                "Правило без фактов сравнивать нечего: ошибка обязана быть явной, а не молчаливым сном. При смене "
                        + "проверки проверь строку выше в тесте");
    }
}
