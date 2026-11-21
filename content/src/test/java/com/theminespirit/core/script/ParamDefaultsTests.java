package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.script.api.CatalogLimits;
import com.theminespirit.core.script.api.ParamSpec;
import com.theminespirit.core.script.api.ScriptCatalog;
import com.theminespirit.core.script.api.ScriptSpec;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ParamDefaultsTests {
    private static final ScriptCatalog CATALOG = CoreScripts.defaultCatalog();

    @Test
    void fill_WithdrawWithoutRadius_AddsCatalogRadius() {
        Map<String, Object> filled =
                ParamDefaults.fill(CATALOG, "withdraw", Map.of("container", "nearest", "item", "wheat", "count", 1));

        assertEquals(
                CatalogLimits.CONTAINER_RADIUS_DEFAULT,
                filled.get("radius"),
                "Внутренний вызов withdraw без radius обязан получить умолчание каталога 8: иначе скрипт падает "
                        + "«Не задан обязательный параметр 'radius'» вместо того, чтобы взять приманку"
                        + " При смене fill_WithdrawWithoutRadius_AddsCatalogRadius проверь это утверждение.");
    }

    @Test
    void fill_WithdrawWithExplicitRadius_KeepsIt() {
        Map<String, Object> filled = ParamDefaults.fill(
                CATALOG, "withdraw", Map.of("container", "nearest", "item", "wheat", "count", 1, "radius", 12));

        assertEquals(
                12,
                filled.get("radius"),
                "Явный radius не перетирается умолчанием: план со своим радиусом ищет контейнер там, где просили"
                        + " Иначе тест fill_WithdrawWithExplicitRadius_KeepsIt перестанет ловить эту поломку. При смене fill_WithdrawWithExplicitRadius_KeepsIt проверь это утверждение.");
    }

    @Test
    void fill_ScriptWithRequiredParams_DoesNotInventThem() {
        Map<String, Object> filled = ParamDefaults.fill(CATALOG, "withdraw", Map.of("count", 1));

        assertFalse(
                filled.containsKey("item"),
                "Обязательный параметр не выдумывается: его отсутствие обязан назвать скрипт или валидатор, "
                        + "а не подставленное значение"
                        + " Иначе тест fill_ScriptWithRequiredParams_DoesNotInventThem перестанет ловить эту поломку. При смене fill_ScriptWithRequiredParams_DoesNotInventThem проверь это утверждение.");
    }

    @Test
    void fill_UnknownScript_NamesItInError() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ParamDefaults.fill(CATALOG, "teleport", Map.of()),
                "Неизвестный скрипт нельзя наполнить умолчаниями молча: иначе опечатка вызова спрячется"
                        + " При смене fill_UnknownScript_NamesItInError проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("teleport"),
                "Сообщение обязано называть неизвестный скрипт, фактическое: " + error.getMessage()
                        + " Иначе тест fill_UnknownScript_NamesItInError перестанет ловить эту поломку. При смене fill_UnknownScript_NamesItInError проверь это утверждение.");
        assertTrue(
                error.getMessage().contains("/v1/capabilities"),
                "Сообщение обязано указывать, где сверять имена, фактическое: " + error.getMessage()
                        + " Иначе тест fill_UnknownScript_NamesItInError перестанет ловить эту поломку. При смене fill_UnknownScript_NamesItInError проверь это утверждение.");
    }

    @Test
    void fill_EveryCatalogScript_GetsAllItsDefaults() {
        for (ScriptSpec spec : CATALOG.all()) {
            Map<String, Object> filled = ParamDefaults.fill(CATALOG, spec.id(), Map.of());
            for (ParamSpec param : spec.params()) {
                if (!param.required() && param.defaultValue() != null) {
                    assertEquals(
                            param.defaultValue(),
                            filled.get(param.name()),
                            "У скрипта '" + spec.id() + "' параметр '" + param.name()
                                    + "' обязан получить умолчание при любом вызове: иначе внутренний раннер "
                                    + "падает на отсутствующем необязательном параметре"
                                    + " При смене fill_EveryCatalogScript_GetsAllItsDefaults проверь это утверждение.");
                }
            }
        }
    }
}
