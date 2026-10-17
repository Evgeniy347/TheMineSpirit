package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ExploreParamsTests {
    @Test
    void from_NoParams_FillsRadiusAndBudgetFromCatalogDefaults() {
        ExploreParams params = ExploreParams.from(Map.of());

        assertEquals(16, params.radius(),
                "explore без радиуса обязан взять умолчание каталога 16: без него неизвестен размер области");
        assertEquals(16, params.budget(),
                "explore без бюджета обязан взять умолчание каталога 16: бюджет ограничивает число точек обхода");
    }

    @Test
    void from_ExplicitValues_ReturnsThem() {
        ExploreParams params = ExploreParams.from(Map.of("radius", 8, "budget", 32));

        assertEquals(8, params.radius(), "explore обязан принять радиус обхода из параметров");
        assertEquals(32, params.budget(), "explore обязан принять бюджет точек обхода из параметров");
    }

    @Test
    void radius_OutsideCatalogBounds_IsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ExploreParams(0, 16),
                "explore с нулевым радиусом обязан отказывать: области для обхода нет");
        assertThrows(IllegalArgumentException.class, () -> new ExploreParams(33, 16),
                "explore с радиусом больше 32 обязан отказывать: больший обход кладёт серверный тик");
    }

    @Test
    void budget_OutsideCatalogBounds_IsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ExploreParams(8, 0),
                "explore с нулевым бюджетом обязан отказывать: без точек обход ничего не исследует");
        assertThrows(IllegalArgumentException.class, () -> new ExploreParams(8, 65),
                "explore с бюджетом больше 64 обязан отказывать: длинный обход режется на части");
    }

    @Test
    void from_WrongBudgetType_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ExploreParams.from(Map.of("budget", "много")),
                "explore с нецелым бюджетом обязан отказывать: строку нельзя тихо превратить в число точек");
    }
}
