package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.hunger.HungerRules;
import org.junit.jupiter.api.Test;

class HungerPolicyTests {
    private static final HungerRules RULES = HungerRules.defaults();

    @Test
    void evaluate_FullAndPeaceful_Clear() {
        assertEquals(
                new PolicyVerdict.Clear(),
                HungerPolicy.evaluate(20, RULES, true, false),
                "Сытый дух не ест: рефлекс голода молчит до порога обеда");
    }

    @Test
    void evaluate_AtThresholdWithFood_Eats() {
        PolicyVerdict verdict = HungerPolicy.evaluate(RULES.eatFoodThreshold(), RULES, true, false);

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
    void evaluate_HungryWithoutFood_Clear() {
        assertEquals(
                new PolicyVerdict.Clear(),
                HungerPolicy.evaluate(0, RULES, false, false),
                "Голодный без еды не получает рефлекс: исполнителю нечего дать, а рефлекс упёрся бы в тайм-аут");
    }

    @Test
    void evaluate_HungryInCombat_Clear() {
        assertEquals(
                new PolicyVerdict.Clear(),
                HungerPolicy.evaluate(0, RULES, true, true),
                "В бою дух не отвлекается на еду: рефлекс не должен вырывать контроль у тактики");
    }

    @Test
    void evaluate_NullRules_Throws() {
        assertThrows(
                NullPointerException.class,
                () -> HungerPolicy.evaluate(0, null, true, false),
                "Без правил голода порог обеда взять неоткуда: он приходит из данных");
    }
}
