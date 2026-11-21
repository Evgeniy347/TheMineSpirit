package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.DescentData;
import com.theminespirit.core.mining.DescentDirection;
import com.theminespirit.core.mining.SafeMineMode;
import com.theminespirit.core.script.api.CatalogLimits;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SafeMineParamsTests {
    @Test
    void from_ModeAndDirectionOnly_FillsDefaultsFromData() {
        SafeMineParams params = SafeMineParams.from(Map.of("mode", "staircase", "direction", "east"));

        assertEquals(
                SafeMineMode.STAIRCASE,
                params.mode(),
                "Форма обязана читаться из обязательного параметра"
                        + " Иначе тест from_ModeAndDirectionOnly_FillsDefaultsFromData перестанет ловить эту поломку. При смене from_ModeAndDirectionOnly_FillsDefaultsFromData проверь это утверждение.");
        assertEquals(
                DescentDirection.EAST,
                params.direction(),
                "Направление обязано читаться из параметра"
                        + " Иначе тест from_ModeAndDirectionOnly_FillsDefaultsFromData перестанет ловить эту поломку. При смене from_ModeAndDirectionOnly_FillsDefaultsFromData проверь это утверждение.");
        assertEquals(
                DescentData.loadDefault().targetY(),
                params.y(),
                "Целевой этаж по умолчанию — из данных: числа не дублируются в коде"
                        + " Иначе тест from_ModeAndDirectionOnly_FillsDefaultsFromData перестанет ловить эту поломку. При смене from_ModeAndDirectionOnly_FillsDefaultsFromData проверь это утверждение.");
        assertEquals(
                CatalogLimits.MINE_LENGTH_DEFAULT,
                params.length(),
                "Длина штольни по умолчанию — из каталога, а не из шаблона fallback"
                        + " Иначе тест from_ModeAndDirectionOnly_FillsDefaultsFromData перестанет ловить эту поломку. При смене from_ModeAndDirectionOnly_FillsDefaultsFromData проверь это утверждение.");
        assertEquals(
                DescentData.loadDefault().torchInterval(),
                params.torchInterval(),
                "Интервал факелов по умолчанию — из данных шахты: шахта освещается по таблице, а не константой"
                        + " Иначе тест from_ModeAndDirectionOnly_FillsDefaultsFromData перестанет ловить эту поломку. При смене from_ModeAndDirectionOnly_FillsDefaultsFromData проверь это утверждение.");
    }

    @Test
    void from_WithoutMode_Throws() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SafeMineParams.from(Map.of("direction", "east")),
                "Без формы шахты параметры обязаны отклоняться: неизвестно, копать вниз или вбок"
                        + " Иначе тест from_WithoutMode_Throws перестанет ловить эту поломку. При смене from_WithoutMode_Throws проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("mode"),
                "Отказ обязан называть пропущенный параметр mode"
                        + " Иначе тест from_WithoutMode_Throws перестанет ловить эту поломку. При смене from_WithoutMode_Throws проверь это утверждение.");
    }

    @Test
    void from_UnknownMode_Throws() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SafeMineParams.from(Map.of("mode", "spiral", "direction", "east")),
                "Неизвестная форма шахты обязана отклоняться до старта"
                        + " Иначе тест from_UnknownMode_Throws перестанет ловить эту поломку. При смене from_UnknownMode_Throws проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("spiral"),
                "Отказ обязан называть неизвестную форму"
                        + " Иначе тест from_UnknownMode_Throws перестанет ловить эту поломку. При смене from_UnknownMode_Throws проверь это утверждение.");
    }

    @Test
    void from_LengthOutOfRange_Throws() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SafeMineParams.from(Map.of("mode", "tunnel", "direction", "east", "length", 0)),
                "Длина штольни вне диапазона обязана отклоняться до чтения мира"
                        + " Иначе тест from_LengthOutOfRange_Throws перестанет ловить эту поломку. При смене from_LengthOutOfRange_Throws проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("Длина штольни"),
                "Отказ обязан называть длину штольни"
                        + " Иначе тест from_LengthOutOfRange_Throws перестанет ловить эту поломку. При смене from_LengthOutOfRange_Throws проверь это утверждение.");
    }

    @Test
    void from_ExplicitValues_OverrideDefaults() {
        SafeMineParams params = SafeMineParams.from(
                Map.of("mode", "tunnel", "direction", "north", "y", -59, "length", 10, "torchInterval", 5));

        assertEquals(
                SafeMineMode.TUNNEL,
                params.mode(),
                "Явная форма обязана победить умолчание"
                        + " Иначе тест from_ExplicitValues_OverrideDefaults перестанет ловить эту поломку. При смене from_ExplicitValues_OverrideDefaults проверь это утверждение.");
        assertEquals(
                10,
                params.length(),
                "Явная длина обязана победить умолчание каталога"
                        + " Иначе тест from_ExplicitValues_OverrideDefaults перестанет ловить эту поломку. При смене from_ExplicitValues_OverrideDefaults проверь это утверждение.");
        assertEquals(
                5,
                params.torchInterval(),
                "Явный интервал факелов обязан победить данные"
                        + " Иначе тест from_ExplicitValues_OverrideDefaults перестанет ловить эту поломку. При смене from_ExplicitValues_OverrideDefaults проверь это утверждение.");
        assertEquals(
                -59,
                params.y(),
                "Явный целевой этаж обязан победить данные"
                        + " Иначе тест from_ExplicitValues_OverrideDefaults перестанет ловить эту поломку. При смене from_ExplicitValues_OverrideDefaults проверь это утверждение.");
    }

    @Test
    void from_TorchIntervalOutOfRange_Throws() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SafeMineParams.from(Map.of("mode", "tunnel", "direction", "east", "torchInterval", 100)),
                "Интервал факелов вне диапазона обязан отклоняться до чтения мира"
                        + " Иначе тест from_TorchIntervalOutOfRange_Throws перестанет ловить эту поломку. При смене from_TorchIntervalOutOfRange_Throws проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("Интервал факелов"),
                "Отказ обязан называть интервал факелов"
                        + " Иначе тест from_TorchIntervalOutOfRange_Throws перестанет ловить эту поломку. При смене from_TorchIntervalOutOfRange_Throws проверь это утверждение.");
    }
}
