package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ExploreParamsTests {
    @Test
    void from_NoParams_FillsRadiusAndBudgetFromCatalogDefaults() {
        ExploreParams params = ExploreParams.from(Map.of());

        assertEquals(
                16,
                params.radius(),
                "explore без радиуса обязан взять умолчание каталога 16: без него неизвестен размер области"
                        + " Иначе тест from_NoParams_FillsRadiusAndBudgetFromCatalogDefaults перестанет ловить эту поломку. При смене from_NoParams_FillsRadiusAndBudgetFromCatalogDefaults проверь это утверждение.");
        assertEquals(
                16,
                params.budget(),
                "explore без бюджета обязан взять умолчание каталога 16: бюджет ограничивает число точек обхода"
                        + " Иначе тест from_NoParams_FillsRadiusAndBudgetFromCatalogDefaults перестанет ловить эту поломку. При смене from_NoParams_FillsRadiusAndBudgetFromCatalogDefaults проверь это утверждение.");
    }

    @Test
    void from_ExplicitValues_ReturnsThem() {
        ExploreParams params = ExploreParams.from(Map.of("radius", 8, "budget", 32));

        assertEquals(
                8,
                params.radius(),
                "explore обязан принять радиус обхода из параметров"
                        + " Иначе тест from_ExplicitValues_ReturnsThem перестанет ловить эту поломку. При смене from_ExplicitValues_ReturnsThem проверь это утверждение.");
        assertEquals(
                32,
                params.budget(),
                "explore обязан принять бюджет точек обхода из параметров"
                        + " Иначе тест from_ExplicitValues_ReturnsThem перестанет ловить эту поломку. При смене from_ExplicitValues_ReturnsThem проверь это утверждение.");
    }

    @Test
    void radius_OutsideCatalogBounds_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExploreParams(0, 16),
                "explore с нулевым радиусом обязан отказывать: области для обхода нет"
                        + " Иначе тест radius_OutsideCatalogBounds_IsRejected перестанет ловить эту поломку. При смене radius_OutsideCatalogBounds_IsRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExploreParams(33, 16),
                "explore с радиусом больше 32 обязан отказывать: больший обход кладёт серверный тик"
                        + " Иначе тест radius_OutsideCatalogBounds_IsRejected перестанет ловить эту поломку. При смене radius_OutsideCatalogBounds_IsRejected проверь это утверждение.");
    }

    @Test
    void budget_OutsideCatalogBounds_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExploreParams(8, 0),
                "explore с нулевым бюджетом обязан отказывать: без точек обход ничего не исследует"
                        + " Иначе тест budget_OutsideCatalogBounds_IsRejected перестанет ловить эту поломку. При смене budget_OutsideCatalogBounds_IsRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExploreParams(8, 65),
                "explore с бюджетом больше 64 обязан отказывать: длинный обход режется на части"
                        + " Иначе тест budget_OutsideCatalogBounds_IsRejected перестанет ловить эту поломку. При смене budget_OutsideCatalogBounds_IsRejected проверь это утверждение.");
    }

    @Test
    void from_WrongBudgetType_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ExploreParams.from(Map.of("budget", "много")),
                "explore с нецелым бюджетом обязан отказывать: строку нельзя тихо превратить в число точек"
                        + " Иначе тест from_WrongBudgetType_IsRejected перестанет ловить эту поломку. При смене from_WrongBudgetType_IsRejected проверь это утверждение.");
    }
}
