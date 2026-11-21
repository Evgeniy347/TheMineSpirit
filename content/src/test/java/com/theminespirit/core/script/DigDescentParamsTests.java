package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.DescentData;
import com.theminespirit.core.mining.DescentDirection;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DigDescentParamsTests {

    @Test
    void from_DirectionAndY_ReturnsBoth() {
        DigDescentParams params = DigDescentParams.from(Map.of("direction", "east", "y", 12));

        assertEquals(
                DescentDirection.EAST,
                params.direction(),
                "east обязан разбираться в восток"
                        + " Иначе тест from_DirectionAndY_ReturnsBoth перестанет ловить эту поломку. При смене from_DirectionAndY_ReturnsBoth проверь это утверждение.");
        assertEquals(
                12,
                params.y(),
                "y из плана обязан проходить в параметры без изменений"
                        + " Иначе тест from_DirectionAndY_ReturnsBoth перестанет ловить эту поломку. При смене from_DirectionAndY_ReturnsBoth проверь это утверждение.");
    }

    @Test
    void from_MissingDirection_Throws() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> DigDescentParams.from(Map.of("y", 12)),
                "Параметры без direction обязаны отклоняться до запуска спуска"
                        + " Иначе тест from_MissingDirection_Throws перестанет ловить эту поломку. При смене from_MissingDirection_Throws проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("direction"),
                "Пропуск direction обязан называть имя параметра: без направления спуск невозможен"
                        + " Иначе тест from_MissingDirection_Throws перестанет ловить эту поломку. При смене from_MissingDirection_Throws проверь это утверждение.");
    }

    @Test
    void from_UnknownDirection_Throws() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> DigDescentParams.from(Map.of("direction", "upwards")),
                "Параметры с неизвестным направлением обязаны отклоняться до запуска спуска"
                        + " Иначе тест from_UnknownDirection_Throws перестанет ловить эту поломку. При смене from_UnknownDirection_Throws проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("Неизвестное направление спуска"),
                "Опечатка в направлении обязана отклоняться с перечнем допустимых"
                        + " Иначе тест from_UnknownDirection_Throws перестанет ловить эту поломку. При смене from_UnknownDirection_Throws проверь это утверждение.");
    }

    @Test
    void from_MissingY_UsesDataDefault() {
        DigDescentParams params = DigDescentParams.from(Map.of("direction", "north"));

        assertEquals(
                -58,
                params.y(),
                "Без y спуск обязан идти до целевого этажа из данных -58: факт зафиксирован литералом"
                        + " Иначе тест from_MissingY_UsesDataDefault перестанет ловить эту поломку. При смене from_MissingY_UsesDataDefault проверь это утверждение.");
        assertEquals(
                DescentData.defaults().targetY(),
                params.y(),
                "Default y обязан совпадать с данными спуска: два источника умолчания разошлись бы у игроков"
                        + " Иначе тест from_MissingY_UsesDataDefault перестанет ловить эту поломку. При смене from_MissingY_UsesDataDefault проверь это утверждение.");
    }

    @Test
    void from_YOutsideWorld_Throws() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> DigDescentParams.from(Map.of("direction", "north", "y", 320)),
                "Параметры с целью выше потолка мира обязаны отклоняться до запуска спуска"
                        + " Иначе тест from_YOutsideWorld_Throws перестанет ловить эту поломку. При смене from_YOutsideWorld_Throws проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("вне мира"),
                "Цель выше потолка мира обязана отклоняться до запуска"
                        + " Иначе тест from_YOutsideWorld_Throws перестанет ловить эту поломку. При смене from_YOutsideWorld_Throws проверь это утверждение.");
    }

    @Test
    void from_EachDirectionName_RoundTrips() {
        for (String name : DescentDirection.names()) {
            DigDescentParams params = DigDescentParams.from(Map.of("direction", name));

            assertEquals(
                    name,
                    params.direction().name().toLowerCase(Locale.ROOT),
                    "Имя '" + name + "' обязано пережить разбор без потерь: каталог и ядро говорят на одном языке"
                            + " Иначе тест from_EachDirectionName_RoundTrips перестанет ловить эту поломку. При смене from_EachDirectionName_RoundTrips проверь это утверждение.");
        }
    }
}
