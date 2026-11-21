package com.theminespirit.core.perception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MemoryPointTests {
    @ParameterizedTest
    @ValueSource(strings = {"resource", "danger", "base"})
    void parse_LowercaseId_RoundTripsThroughId(String id) {
        assertEquals(
                id,
                MemoryType.parse(id).id(),
                "тип точки в API записывается строчными: parse и id обязаны быть обратны друг другу"
                        + " Иначе тест parse_LowercaseId_RoundTripsThroughId перестанет ловить эту поломку. При смене parse_LowercaseId_RoundTripsThroughId проверь это утверждение.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"RESOURCE", "Danger", "", "ore", " base"})
    void parse_NotLowercaseId_IsRejectedWithAllowedList(String id) {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> MemoryType.parse(id),
                "тип '" + id + "' обязан отклоняться: допустимы только resource, danger, base строчными"
                        + " Иначе тест parse_NotLowercaseId_IsRejectedWithAllowedList перестанет ловить эту поломку. При смене parse_NotLowercaseId_IsRejectedWithAllowedList проверь это утверждение.");

        assertEquals(
                true,
                error.getMessage().contains("[resource, danger, base]"),
                "отказ обязан перечислять допустимые типы: " + error.getMessage()
                        + " Иначе тест parse_NotLowercaseId_IsRejectedWithAllowedList перестанет ловить эту поломку. При смене parse_NotLowercaseId_IsRejectedWithAllowedList проверь это утверждение.");
    }

    @Test
    void ids_AllTypes_AreLowercaseInDeclarationOrder() {
        assertEquals(
                List.of("resource", "danger", "base"),
                MemoryType.ids(),
                "список типов для каталога обязан идти в порядке объявления и строчными"
                        + " Иначе тест ids_AllTypes_AreLowercaseInDeclarationOrder перестанет ловить эту поломку. При смене ids_AllTypes_AreLowercaseInDeclarationOrder проверь это утверждение.");
    }

    @Test
    void constructor_BadNameOrDimension_IsRejected() {
        BlockCoord pos = new BlockCoord(0, 0, 0);
        String tooLongName = "n".repeat(33);
        String tooLongDimension = "d".repeat(65);

        assertEquals(
                32,
                MemoryPoint.MAX_NAME_LENGTH,
                "предел имени согласован: 32 символа"
                        + " Иначе тест constructor_BadNameOrDimension_IsRejected перестанет ловить эту поломку. При смене constructor_BadNameOrDimension_IsRejected проверь это утверждение.");
        assertEquals(
                64,
                MemoryPoint.MAX_DIMENSION_LENGTH,
                "предел измерения согласован: 64 символа"
                        + " Иначе тест constructor_BadNameOrDimension_IsRejected перестанет ловить эту поломку. При смене constructor_BadNameOrDimension_IsRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> new MemoryPoint(" ", MemoryType.BASE, "d", pos),
                "пустое имя обязано отклоняться: по нему точку вспоминают"
                        + " Иначе тест constructor_BadNameOrDimension_IsRejected перестанет ловить эту поломку. При смене constructor_BadNameOrDimension_IsRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> new MemoryPoint(tooLongName, MemoryType.BASE, "d", pos),
                "имя длиннее 32 обязано отклоняться: память ограничена по объёму"
                        + " Иначе тест constructor_BadNameOrDimension_IsRejected перестанет ловить эту поломку. При смене constructor_BadNameOrDimension_IsRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> new MemoryPoint("a", MemoryType.BASE, "", pos),
                "пустое измерение обязано отклоняться: точка помнит, где её запомнили"
                        + " Иначе тест constructor_BadNameOrDimension_IsRejected перестанет ловить эту поломку. При смене constructor_BadNameOrDimension_IsRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> new MemoryPoint("a", MemoryType.BASE, tooLongDimension, pos),
                "измерение длиннее 64 обязано отклоняться: память ограничена по объёму"
                        + " Иначе тест constructor_BadNameOrDimension_IsRejected перестанет ловить эту поломку. При смене constructor_BadNameOrDimension_IsRejected проверь это утверждение.");
        assertEquals(
                "n".repeat(32),
                new MemoryPoint("n".repeat(32), MemoryType.BASE, "d", pos).name(),
                "имя ровно в 32 символа допустимо: предел — граница, а не запрет"
                        + " Иначе тест constructor_BadNameOrDimension_IsRejected перестанет ловить эту поломку. При смене constructor_BadNameOrDimension_IsRejected проверь это утверждение.");
    }
}
