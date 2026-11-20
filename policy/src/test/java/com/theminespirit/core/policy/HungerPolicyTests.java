package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.policy.api.PolicyId;
import org.junit.jupiter.api.Test;

class HungerPolicyTests {
    private static final int THRESHOLD = 7;

    @Test
    void evaluate_FullAndPeaceful_Clear() {
        assertEquals(
                new PolicyVerdict.Clear(),
                HungerPolicy.evaluate(20, THRESHOLD, true, false),
                "Сытый дух не ест: рефлекс голода молчит до порога обеда, иначе еда тратится впустую. "
                        + "При смене evaluate проверь сытость строго выше порога");
    }

    @Test
    void evaluate_AtThresholdWithFood_Eats() {
        PolicyVerdict verdict = HungerPolicy.evaluate(THRESHOLD, THRESHOLD, true, false);

        assertTrue(
                verdict instanceof PolicyVerdict.Reflex,
                "На самом пороге обеда дух ест: иначе порог читается как «строго ниже» и граница уезжает. "
                        + "При смене сравнения проверь ровно порог и порог минус один");
        assertEquals(
                PolicyId.HUNGER,
                ((PolicyVerdict.Reflex) verdict).policy(),
                "Рефлекс помечен политикой HUNGER: по ней идут лог и исключения, иначе причина еды в логе "
                        + "потеряется. При смене PolicyId проверь метку в рефлексе");
        assertEquals(
                new ReflexAction.Eat(),
                ((PolicyVerdict.Reflex) verdict).action(),
                "Действие рефлекса — еда: исполнитель кормит духа лучшим из инвентаря, иное действие оставило бы "
                        + "духа голодным, иначе он умрёт с голоду. При смене HungerPolicy проверь, что действие именно Eat");
    }

    @Test
    void evaluate_AboveThreshold_Clear() {
        assertEquals(
                new PolicyVerdict.Clear(),
                HungerPolicy.evaluate(THRESHOLD + 1, THRESHOLD, true, false),
                "Выше порога рефлекс молчит даже с полной сумкой еды: порог приходит данными, а не константой, "
                        + "иначе смена данных не подействует. При смене evaluate проверь порог+1");
    }

    @Test
    void evaluate_HungryWithoutFood_Clear() {
        assertEquals(
                new PolicyVerdict.Clear(),
                HungerPolicy.evaluate(0, THRESHOLD, false, false),
                "Голодный без еды не получает рефлекс: исполнителю нечего дать, а рефлекс упёрся бы в тайм-аут "
                        + "и держал бы духа впустую, иначе он застрянет до тайм-аута. При смене evaluate проверь пустой инвентарь еды");
    }

    @Test
    void evaluate_HungryInCombat_Clear() {
        assertEquals(
                new PolicyVerdict.Clear(),
                HungerPolicy.evaluate(0, THRESHOLD, true, true),
                "В бою дух не отвлекается на еду: рефлекс не должен вырывать контроль у тактики, иначе бой "
                        + "прерывается посреди замаха. При смене evaluate проверь флаг боя");
    }
}
