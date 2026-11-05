package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HungerPolicyTests {
    private static final int THRESHOLD = 7;

    @Test
    void evaluate_FullAndPeaceful_Clear() {
        assertEquals(
                new PolicyVerdict.Clear(),
                HungerPolicy.evaluate(20, THRESHOLD, true, false),
                "Сытый дух не ест: рефлекс голода молчит до порога обеда");
    }

    @Test
    void evaluate_AtThresholdWithFood_Eats() {
        PolicyVerdict verdict = HungerPolicy.evaluate(THRESHOLD, THRESHOLD, true, false);

        assertTrue(
                verdict instanceof PolicyVerdict.Reflex,
                "На самом пороге обеда дух ест: иначе порог читается как «строго ниже»");
        assertEquals(
                PolicyId.HUNGER,
                ((PolicyVerdict.Reflex) verdict).policy(),
                "Рефлекс помечен политикой HUNGER: по ней идут лог и исключения");
        assertEquals(
                new ReflexAction.Eat(),
                ((PolicyVerdict.Reflex) verdict).action(),
                "Действие рефлекса — еда: исполнитель кормит духа лучшим из инвентаря");
    }

    @Test
    void evaluate_AboveThreshold_Clear() {
        assertEquals(
                new PolicyVerdict.Clear(),
                HungerPolicy.evaluate(THRESHOLD + 1, THRESHOLD, true, false),
                "Выше порога рефлекс молчит даже с полной сумкой еды: порог приходит данными, а не константой");
    }

    @Test
    void evaluate_HungryWithoutFood_Clear() {
        assertEquals(
                new PolicyVerdict.Clear(),
                HungerPolicy.evaluate(0, THRESHOLD, false, false),
                "Голодный без еды не получает рефлекс: исполнителю нечего дать, а рефлекс упёрся бы в тайм-аут");
    }

    @Test
    void evaluate_HungryInCombat_Clear() {
        assertEquals(
                new PolicyVerdict.Clear(),
                HungerPolicy.evaluate(0, THRESHOLD, true, true),
                "В бою дух не отвлекается на еду: рефлекс не должен вырывать контроль у тактики");
    }
}
