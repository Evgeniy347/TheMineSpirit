package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ParamSpecTests {
    @Test
    void bool_WithDefault_KeepsTypeAndDefault() {
        ParamSpec spec = ParamSpec.bool("replant", false, true);

        assertEquals(
                ParamType.BOOL,
                spec.type(),
                "bool-параметр обязан иметь тип BOOL: по нему строится схема в capabilities");
        assertEquals(
                true,
                spec.defaultValue(),
                "Умолчание bool обязано сохраняться: валидатор подставляет его в план, а ИИ видит его в capabilities");
        assertEquals(
                false, spec.required(), "Параметр с умолчанием, объявленный необязательным, остаётся необязательным");
    }

    @Test
    void bool_WithoutDefault_HasNoDefault() {
        assertEquals(
                null,
                ParamSpec.bool("replant", false).defaultValue(),
                "Перегрузка без умолчания обязана оставлять defaultValue пустым: старые скрипты не должны получить значение молча");
    }

    @Test
    void enumeration_WithDefault_KeepsValuesAndDefault() {
        ParamSpec spec = ParamSpec.enumeration("species", false, List.of("any", "oak", "birch"), "any");

        assertEquals(
                List.of("any", "oak", "birch"),
                spec.values(),
                "Допустимые значения enum обязаны сохраняться в порядке объявления: ИИ читает их из capabilities");
        assertEquals("any", spec.defaultValue(), "Умолчание enum обязано сохраняться");
    }

    @Test
    void enumeration_DefaultOutsideValues_IsRejectedWithBothNamed() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ParamSpec.enumeration("species", false, List.of("oak", "birch"), "spruce"),
                "Умолчание вне списка enum обязано отвергаться при создании: иначе валидатор подставил бы недопустимое значение");

        assertTrue(
                error.getMessage().contains("spruce") && error.getMessage().contains("species"),
                "Сообщение обязано называть параметр и недопустимое умолчание. Фактическое: " + error.getMessage());
    }
}
