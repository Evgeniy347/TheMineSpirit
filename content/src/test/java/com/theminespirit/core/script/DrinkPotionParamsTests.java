package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DrinkPotionParamsTests {
    @Test
    void from_TypePresent_KeepsIt() {
        DrinkPotionParams params = DrinkPotionParams.from(Map.of("type", "healing"));

        assertEquals(
                "healing",
                params.type(),
                "drink_potion обязан сохранить тип зелья: по нему скрипт ищет зелье в инвентаре"
                        + " Иначе тест from_TypePresent_KeepsIt перестанет ловить эту поломку. При смене from_TypePresent_KeepsIt проверь это утверждение.");
    }

    @Test
    void from_TypeWithSpaces_IsKeptForScriptNormalization() {
        DrinkPotionParams params = DrinkPotionParams.from(Map.of("type", "fire-resistance"));

        assertEquals(
                "fire-resistance",
                params.type(),
                "drink_potion обязан сохранить тип как есть: нормализацию имени делает скрипт"
                        + " Иначе тест from_TypeWithSpaces_IsKeptForScriptNormalization перестанет ловить эту поломку. При смене from_TypeWithSpaces_IsKeptForScriptNormalization проверь это утверждение.");
    }

    @Test
    void from_MissingType_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> DrinkPotionParams.from(Map.of()),
                "drink_potion без типа обязан отказывать: неизвестно, что пить"
                        + " Иначе тест from_MissingType_IsRejected перестанет ловить эту поломку. При смене from_MissingType_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("type"),
                "Отказ обязан называть параметр 'type'. Фактическое: " + error.getMessage()
                        + " Иначе тест from_MissingType_IsRejected перестанет ловить эту поломку. При смене from_MissingType_IsRejected проверь это утверждение.");
    }

    @Test
    void from_BlankType_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DrinkPotionParams.from(Map.of("type", " ")),
                "drink_potion с пустым типом обязан отказывать: пустое имя не совпадёт ни с одним зельем"
                        + " Иначе тест from_BlankType_IsRejected перестанет ловить эту поломку. При смене from_BlankType_IsRejected проверь это утверждение.");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> DrinkPotionParams.from(null),
                "DrinkPotionParams.from(null) обязан отказывать: без карты параметров скрипт не разобрать"
                        + " Иначе тест from_NullParams_IsRejected перестанет ловить эту поломку. При смене from_NullParams_IsRejected проверь это утверждение.");
    }
}
