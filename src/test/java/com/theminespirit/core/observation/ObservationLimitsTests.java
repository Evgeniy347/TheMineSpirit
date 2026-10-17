package com.theminespirit.core.observation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ObservationLimitsTests {
    @Test
    void defaults_DefaultLimits_HaveExpectedNumbers() {
        ObservationLimits limits = ObservationLimits.defaults();

        assertEquals(16, limits.maxGroundItems(),
                "Число выпавших предметов в наблюдении — геймдизайн: если его меняют осознанно, правят и тест, "
                        + "иначе ответ стратегу разрастётся незаметно");
        assertEquals(8, limits.maxContainers(),
                "Число контейнеров в наблюдении — геймдизайн: правка числа меняет объём ответа и нагрузку на скан");
        assertEquals(27, limits.maxContainerItems(),
                "Записей на контейнер не больше полного одиночного сундука (27 слотов) — иначе содержимое двойного "
                        + "сундука раздувает ответ");
    }

    @Test
    void parse_NumbersInJson_ReadsAllThreeLimits() {
        ObservationLimits limits = ObservationLimits.parse(
                "{\"maxGroundItems\":4,\"maxContainers\":2,\"maxContainerItems\":10}", "test");

        assertEquals(new ObservationLimits(4, 2, 10), limits,
                "Парсер обязан читать все три предела из данных, иначе /reload не меняет размер ответа");
    }

    static Stream<Arguments> invalidNumbers() {
        return Stream.of(
                Arguments.of("{\"maxGroundItems\":0,\"maxContainers\":2,\"maxContainerItems\":10}", "maxGroundItems"),
                Arguments.of("{\"maxGroundItems\":4,\"maxContainers\":0,\"maxContainerItems\":10}", "maxContainers"),
                Arguments.of("{\"maxGroundItems\":4,\"maxContainers\":2,\"maxContainerItems\":0}", "maxContainerItems"),
                Arguments.of("{\"maxGroundItems\":9999,\"maxContainers\":2,\"maxContainerItems\":10}", "maxGroundItems"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("invalidNumbers")
    void parse_NonPositiveOrHugeLimit_RejectedWithFieldName(String json, String field) {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> ObservationLimits.parse(json, "test"),
                "Предел вне [1, 512] обязан отвергаться на загрузке: иначе наблюдение либо пустое, либо огромное");
        assertTrue(error.getMessage().contains(field),
                "Отказ называет поле данных, которое надо поправить: " + error.getMessage());
    }
}
