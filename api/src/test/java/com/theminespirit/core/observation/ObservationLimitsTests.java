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

        assertEquals(
                16,
                limits.maxGroundItems(),
                "Число выпавших предметов в наблюдении — геймдизайн: если его меняют осознанно, правят и тест, "
                        + "иначе ответ стратегу разрастётся незаметно"
                        + " При смене defaults_DefaultLimits_HaveExpectedNumbers проверь это утверждение.");
        assertEquals(
                8,
                limits.maxContainers(),
                "Число контейнеров в наблюдении — геймдизайн: правка числа меняет объём ответа и нагрузку на скан"
                        + " Иначе тест defaults_DefaultLimits_HaveExpectedNumbers перестанет ловить эту поломку. При смене defaults_DefaultLimits_HaveExpectedNumbers проверь это утверждение.");
        assertEquals(
                27,
                limits.maxContainerItems(),
                "Записей на контейнер не больше полного одиночного сундука (27 слотов) — иначе содержимое двойного "
                        + "сундука раздувает ответ"
                        + " При смене defaults_DefaultLimits_HaveExpectedNumbers проверь это утверждение.");
    }

    @Test
    void parse_NumbersInJson_ReadsAllFourLimits() {
        ObservationLimits limits = ObservationLimits.parse(
                "{\"maxGroundItems\":4,\"maxContainers\":2,\"maxContainerItems\":10,\"maxOpenBlocks\":6}", "test");

        assertEquals(
                new ObservationLimits(4, 2, 10, 6),
                limits,
                "Парсер обязан читать все три предела из данных, иначе /reload не меняет размер ответа"
                        + " При смене parse_NumbersInJson_ReadsAllFourLimits проверь это утверждение.");
    }

    static Stream<Arguments> invalidNumbers() {
        return Stream.of(
                Arguments.of(
                        "{\"maxGroundItems\":0,\"maxContainers\":2,\"maxContainerItems\":10,\"maxOpenBlocks\":6}",
                        "maxGroundItems"),
                Arguments.of(
                        "{\"maxGroundItems\":4,\"maxContainers\":0,\"maxContainerItems\":10,\"maxOpenBlocks\":6}",
                        "maxContainers"),
                Arguments.of(
                        "{\"maxGroundItems\":4,\"maxContainers\":2,\"maxContainerItems\":0,\"maxOpenBlocks\":6}",
                        "maxContainerItems"),
                Arguments.of(
                        "{\"maxGroundItems\":9999,\"maxContainers\":2,\"maxContainerItems\":10,\"maxOpenBlocks\":6}",
                        "maxGroundItems"),
                Arguments.of(
                        "{\"maxGroundItems\":4,\"maxContainers\":2,\"maxContainerItems\":10,\"maxOpenBlocks\":0}",
                        "maxOpenBlocks"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("invalidNumbers")
    void parse_NonPositiveOrHugeLimit_RejectedWithFieldName(String json, String field) {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ObservationLimits.parse(json, "test"),
                "Предел вне [1, 512] обязан отвергаться на загрузке: иначе наблюдение либо пустое, либо огромное"
                        + " При смене parse_NonPositiveOrHugeLimit_RejectedWithFieldName проверь это утверждение.");
        assertTrue(
                error.getMessage().contains(field),
                "Отказ называет поле данных, которое надо поправить: " + error.getMessage()
                        + " Иначе тест parse_NonPositiveOrHugeLimit_RejectedWithFieldName перестанет ловить эту поломку. При смене parse_NonPositiveOrHugeLimit_RejectedWithFieldName проверь это утверждение.");
    }
}
