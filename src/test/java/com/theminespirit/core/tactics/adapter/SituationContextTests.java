package com.theminespirit.core.tactics.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.EnvFacts;
import com.theminespirit.core.tactics.Orders;
import com.theminespirit.core.tactics.SelfFacts;
import com.theminespirit.core.tactics.Situation;
import com.theminespirit.core.tactics.TimeOfDay;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SituationContextTests {
    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidContexts")
    void context_InvalidField_IsRejected(String field, Executable build) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, build,
                "Повреждённый вход адаптера обязан падать сразу с именем поля: иначе он молча дойдёт до ядра и решение "
                        + "будет принято по фиктивным фактам");

        assertTrue(error.getMessage().contains(field),
                "Сообщение называет поле " + field + ", чтобы правка шла по подсказке; получили: "
                        + error.getMessage());
    }

    static Stream<Arguments> invalidContexts() {
        SelfFacts self = AdapterFixtures.self();
        EnvFacts env = AdapterFixtures.env(TimeOfDay.DAY);
        Orders orders = AdapterFixtures.orders(CombatMode.AGGRESSIVE);
        return Stream.of(
                Arguments.of("self", build(null, List.of(), env, orders, 0, 0)),
                Arguments.of("enemies", build(self, null, env, orders, 0, 0)),
                Arguments.of("env", build(self, List.of(), null, orders, 0, 0)),
                Arguments.of("orders", build(self, List.of(), env, null, 0, 0)),
                Arguments.of("ticksSinceHit", build(self, List.of(), env, orders, -1, 0)),
                Arguments.of("ticksSinceStrike", build(self, List.of(), env, orders, 0, Situation.MAX_TICKS + 1)));
    }

    @Test
    void context_SourceEnemyList_IsCopied() {
        List<EnemyFact> source = new ArrayList<>(List.of(AdapterFixtures.enemy("minecraft:zombie", "z1", 3, 0)));
        SituationContext context = new SituationContext(AdapterFixtures.self(), source,
                AdapterFixtures.env(TimeOfDay.DAY), AdapterFixtures.orders(CombatMode.AGGRESSIVE), 0, 0);

        source.clear();

        assertEquals(1, context.enemies().size(),
                "Контекст неизменяем: список врагов копируется, иначе правка сборщика меняла бы факты задним числом");
    }

    @Test
    void collect_NullContext_IsRejected() {
        CoreFactCollector collector = CoreFactCollector.withDefaultSettings();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> collector.collect(null),
                "Сборщик без контекста — ошибка вызова, а не пустая ситуация: тихий пропуск скрыл бы неверный код");

        assertTrue(error.getMessage().contains("collect"),
                "Сообщение называет метод сборщика; получили: " + error.getMessage());
    }

    @Test
    void constructor_NullSettings_IsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new CoreFactCollector(null),
                "Без настроек неизвестен факт-радиус: сборщик обязан отказаться, а не искать врагов по всему миру");

        assertTrue(error.getMessage().contains("settings"),
                "Сообщение называет настройки; получили: " + error.getMessage());
    }

    private static Executable build(SelfFacts self, List<EnemyFact> enemies, EnvFacts env, Orders orders, int sinceHit,
            int sinceStrike) {
        return () -> new SituationContext(self, enemies, env, orders, sinceHit, sinceStrike);
    }
}
