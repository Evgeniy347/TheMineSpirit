package com.theminespirit.core.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GoalTests {
    @Test
    void goal_BlankText_ThrowsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Goal("   ", Map.of()),
                "Цель с пустым текстом обязана отвергаться: по тексту планировщик выбирает сценарий, "
                        + "а пустая цель приведёт к произвольному действию духа");
    }

    @Test
    void goal_NullText_ThrowsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Goal(null, Map.of()),
                "Цель с null-текстом обязана отвергаться: без текста не из чего строить промпт и выбирать fallback-правило");
    }

    @Test
    void goal_NullParams_ThrowsNullPointerException() {
        assertThrows(
                NullPointerException.class,
                () -> new Goal("добыть руду", null),
                "Цель с null-параметрами обязана отвергаться: без карты параметров планировщик не узнает радиус "
                        + "и количество, а подстановка пустой карты молча оставит их по умолчанию");
    }

    @Test
    void goal_ParamsMapMutated_ConstructorKeepsCopy() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("radius", 32);
        Goal goal = new Goal("добыть руду", source);
        source.put("count", 10);

        assertEquals(
                Map.of("radius", 32),
                goal.params(),
                "Goal обязан копировать параметры при создании: мутация исходной карты изменила бы план задним "
                        + "числом, и цель исполнилась бы не так, как её утвердил вызывающий");
    }

    @Test
    void goal_ParamsReturned_IsUnmodifiable() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("count", 10);
        Goal goal = new Goal("добыть руду", source);

        assertThrows(
                UnsupportedOperationException.class,
                () -> goal.params().put("count", 1),
                "params() обязан возвращать неизменяемую карту: иначе движок может незаметно подменить параметры "
                        + "цели, и планировщик будет думать, что решил одну задачу, а исполнит другую");
    }
}
