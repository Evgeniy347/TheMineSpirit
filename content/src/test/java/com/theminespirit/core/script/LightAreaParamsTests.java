package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.script.api.CatalogLimits;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LightAreaParamsTests {
    @Test
    void from_AllParams_ParsesEveryField() {
        LightAreaParams params = LightAreaParams.from(Map.of("x", 10, "y", 64, "z", -3, "radius", 8));

        assertEquals(
                10,
                params.x(),
                "x обязан читаться из параметра плана"
                        + " Иначе тест from_AllParams_ParsesEveryField перестанет ловить эту поломку. При смене from_AllParams_ParsesEveryField проверь это утверждение.");
        assertEquals(
                64,
                params.y(),
                "y обязан читаться из параметра плана"
                        + " Иначе тест from_AllParams_ParsesEveryField перестанет ловить эту поломку. При смене from_AllParams_ParsesEveryField проверь это утверждение.");
        assertEquals(
                -3,
                params.z(),
                "z обязан читаться из параметра плана"
                        + " Иначе тест from_AllParams_ParsesEveryField перестанет ловить эту поломку. При смене from_AllParams_ParsesEveryField проверь это утверждение.");
        assertEquals(
                8,
                params.radius(),
                "radius обязан читаться из параметра плана"
                        + " Иначе тест from_AllParams_ParsesEveryField перестанет ловить эту поломку. При смене from_AllParams_ParsesEveryField проверь это утверждение.");
    }

    @Test
    void from_RadiusBelowMinimum_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LightAreaParams.from(
                        Map.of("x", 0, "y", 64, "z", 0, "radius", CatalogLimits.LIGHT_RADIUS_MIN - 1)),
                "Радиус ниже минимума каталога обязан падать: иначе план осветит пустую область"
                        + " При смене from_RadiusBelowMinimum_Throws проверь это утверждение.");
    }

    @Test
    void from_RadiusAboveMaximum_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LightAreaParams.from(
                        Map.of("x", 0, "y", 64, "z", 0, "radius", CatalogLimits.LIGHT_RADIUS_MAX + 1)),
                "Радиус выше максимума каталога обязан падать: большой радиус кладёт серверный тик"
                        + " Иначе тест from_RadiusAboveMaximum_Throws перестанет ловить эту поломку. При смене from_RadiusAboveMaximum_Throws проверь это утверждение.");
    }

    @Test
    void from_MissingRadius_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LightAreaParams.from(Map.of("x", 0, "y", 64, "z", 0)),
                "Без радиуса скрипт не знает область: параметр обязателен"
                        + " Иначе тест from_MissingRadius_Throws перестанет ловить эту поломку. При смене from_MissingRadius_Throws проверь это утверждение.");
    }
}
