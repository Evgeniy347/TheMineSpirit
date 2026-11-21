package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.script.api.ParamSpec;
import com.theminespirit.core.script.api.ParamType;
import java.util.List;
import org.junit.jupiter.api.Test;

class ParamSpecTests {
    @Test
    void bool_WithDefault_KeepsTypeAndDefault() {
        ParamSpec spec = ParamSpec.bool("replant", false, true);

        assertEquals(
                ParamType.BOOL,
                spec.type(),
                "bool-параметр обязан иметь тип BOOL: по нему строится схема в capabilities"
                        + " Иначе тест bool_WithDefault_KeepsTypeAndDefault перестанет ловить эту поломку. При смене bool_WithDefault_KeepsTypeAndDefault проверь это утверждение.");
        assertEquals(
                true,
                spec.defaultValue(),
                "Умолчание bool обязано сохраняться: валидатор подставляет его в план, а ИИ видит его в capabilities"
                        + " Иначе тест bool_WithDefault_KeepsTypeAndDefault перестанет ловить эту поломку. При смене bool_WithDefault_KeepsTypeAndDefault проверь это утверждение.");
        assertEquals(
                false,
                spec.required(),
                "Параметр с умолчанием, объявленный необязательным, остаётся необязательным"
                        + " Иначе тест bool_WithDefault_KeepsTypeAndDefault перестанет ловить эту поломку. При смене bool_WithDefault_KeepsTypeAndDefault проверь это утверждение.");
    }

    @Test
    void bool_WithoutDefault_HasNoDefault() {
        assertEquals(
                null,
                ParamSpec.bool("replant", false).defaultValue(),
                "Перегрузка без умолчания обязана оставлять defaultValue пустым: старые скрипты не должны получить значение молча"
                        + " Иначе тест bool_WithoutDefault_HasNoDefault перестанет ловить эту поломку. При смене bool_WithoutDefault_HasNoDefault проверь это утверждение.");
    }

    @Test
    void enumeration_WithDefault_KeepsValuesAndDefault() {
        ParamSpec spec = ParamSpec.enumeration("species", false, List.of("any", "oak", "birch"), "any");

        assertEquals(
                List.of("any", "oak", "birch"),
                spec.values(),
                "Допустимые значения enum обязаны сохраняться в порядке объявления: ИИ читает их из capabilities"
                        + " Иначе тест enumeration_WithDefault_KeepsValuesAndDefault перестанет ловить эту поломку. При смене enumeration_WithDefault_KeepsValuesAndDefault проверь это утверждение.");
        assertEquals(
                "any",
                spec.defaultValue(),
                "Умолчание enum обязано сохраняться"
                        + " Иначе тест enumeration_WithDefault_KeepsValuesAndDefault перестанет ловить эту поломку. При смене enumeration_WithDefault_KeepsValuesAndDefault проверь это утверждение.");
    }

    @Test
    void enumeration_DefaultOutsideValues_IsRejectedWithBothNamed() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ParamSpec.enumeration("species", false, List.of("oak", "birch"), "spruce"),
                "Умолчание вне списка enum обязано отвергаться при создании: иначе валидатор подставил бы недопустимое значение"
                        + " При смене enumeration_DefaultOutsideValues_IsRejectedWithBothNamed проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("spruce") && error.getMessage().contains("species"),
                "Сообщение обязано называть параметр и недопустимое умолчание. Фактическое: " + error.getMessage()
                        + " Иначе тест enumeration_DefaultOutsideValues_IsRejectedWithBothNamed перестанет ловить эту поломку. При смене enumeration_DefaultOutsideValues_IsRejectedWithBothNamed проверь это утверждение.");
    }
}
