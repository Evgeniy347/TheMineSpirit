package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class LightAreaParamsTests {
    @Test
    void from_AllParams_ParsesEveryField() {
        LightAreaParams params = LightAreaParams.from(Map.of("x", 10, "y", 64, "z", -3, "radius", 8));

        assertEquals(10, params.x(), "x обязан читаться из параметра плана");
        assertEquals(64, params.y(), "y обязан читаться из параметра плана");
        assertEquals(-3, params.z(), "z обязан читаться из параметра плана");
        assertEquals(8, params.radius(), "radius обязан читаться из параметра плана");
    }

    @Test
    void from_RadiusBelowMinimum_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> LightAreaParams.from(Map.of("x", 0, "y", 64, "z", 0,
                        "radius", CatalogLimits.LIGHT_RADIUS_MIN - 1)),
                "Радиус ниже минимума каталога обязан падать: иначе план осветит пустую область");
    }

    @Test
    void from_RadiusAboveMaximum_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> LightAreaParams.from(Map.of("x", 0, "y", 64, "z", 0,
                        "radius", CatalogLimits.LIGHT_RADIUS_MAX + 1)),
                "Радиус выше максимума каталога обязан падать: большой радиус кладёт серверный тик");
    }

    @Test
    void from_MissingRadius_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> LightAreaParams.from(Map.of("x", 0, "y", 64, "z", 0)),
                "Без радиуса скрипт не знает область: параметр обязателен");
    }
}
