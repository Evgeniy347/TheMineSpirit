package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GotoParamsTests {
    @Test
    void from_AllParamsPresent_ReturnsCoordinates() {
        GotoParams params = GotoParams.from(Map.of("x", 10, "y", 64, "z", -3));

        assertEquals(
                new GotoParams(10, 64, -3),
                params,
                "goto обязан принять целые x,y,z и сохранить их: по ним адаптер ставит цель движения духа"
                        + " Иначе тест from_AllParamsPresent_ReturnsCoordinates перестанет ловить эту поломку. При смене from_AllParamsPresent_ReturnsCoordinates проверь это утверждение.");
    }

    @Test
    void from_LongNumbers_AreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> GotoParams.from(Map.of("x", 10L, "y", 64L, "z", -3L)),
                "маппинг обязан принимать только Integer: валидатор плана приводит тип INT к Integer, "
                        + "а long означает, что значение не прошло валидатор"
                        + " Иначе тест from_LongNumbers_AreRejected перестанет ловить эту поломку. При смене from_LongNumbers_AreRejected проверь это утверждение.");
    }

    @Test
    void from_MissingParam_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> GotoParams.from(Map.of("x", 1, "y", 2)),
                "goto без z обязан отказывать: без вертикали скрипт не знает, на каком уровне цель"
                        + " Иначе тест from_MissingParam_IsRejected перестанет ловить эту поломку. При смене from_MissingParam_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("z"),
                "отказ обязан называть недостающий параметр 'z'. Фактическое: " + error.getMessage()
                        + " Иначе тест from_MissingParam_IsRejected перестанет ловить эту поломку. При смене from_MissingParam_IsRejected проверь это утверждение.");
    }

    @Test
    void from_WrongParamType_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> GotoParams.from(Map.of("x", 1, "y", 2, "z", "upper")),
                "goto с нецелой координатой обязан отказывать: строку нельзя тихо привести к позиции духа"
                        + " Иначе тест from_WrongParamType_IsRejected перестанет ловить эту поломку. При смене from_WrongParamType_IsRejected проверь это утверждение.");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> GotoParams.from(null),
                "GotoParams.from(null) обязан отказывать: без карты параметров маппинг только читает состояние мира"
                        + " Иначе тест from_NullParams_IsRejected перестанет ловить эту поломку. При смене from_NullParams_IsRejected проверь это утверждение.");
    }
}
