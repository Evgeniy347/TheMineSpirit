package com.theminespirit.core.engine;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConditionRegistryTests {
    @Test
    void find_KnownCheck_ReturnsRegisteredEvaluator() {
        ConditionEvaluator evaluator = params -> true;
        ConditionRegistry registry = ConditionRegistry.of(Map.of("is_day", evaluator));

        assertSame(
                evaluator,
                registry.find("is_day").orElseThrow(),
                "find обязан вернуть именно зарегистрированный вычислитель: иначе условие выберет не ту ветку");
    }

    @Test
    void find_UnknownCheck_IsEmpty() {
        ConditionRegistry registry = ConditionRegistry.of(Map.of("is_day", params -> true));

        assertTrue(
                registry.find("ghost").isEmpty(),
                "неизвестная проверка обязана давать пустой Optional: движок провалит condition, а не угадает ветку");
    }

    @Test
    void of_SourceMapMutatedLater_DoesNotChangeRegistry() {
        Map<String, ConditionEvaluator> source = new HashMap<>();
        source.put("is_day", params -> true);
        ConditionRegistry registry = ConditionRegistry.of(source);

        source.clear();

        assertTrue(
                registry.find("is_day").isPresent(),
                "реестр обязан делать защитную копию: иначе правка исходной карты меняет ветвление на ходу");
    }

    @Test
    void of_NullEvaluator_IsRejected() {
        Map<String, ConditionEvaluator> source = new HashMap<>();
        source.put("is_day", null);

        assertThrows(
                NullPointerException.class,
                () -> ConditionRegistry.of(source),
                "реестр с null-вычислителем обязан отвергаться при создании: иначе падение случится в серверном тике");
    }

    @Test
    void of_NullMap_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> ConditionRegistry.of(null),
                "реестр без карты обязан отвергаться: пустой реестр задают пустой картой");
    }
}
