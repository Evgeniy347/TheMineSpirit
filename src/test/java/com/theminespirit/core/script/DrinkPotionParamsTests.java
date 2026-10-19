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
                "drink_potion обязан сохранить тип зелья: по нему скрипт ищет зелье в инвентаре");
    }

    @Test
    void from_TypeWithSpaces_IsKeptForScriptNormalization() {
        DrinkPotionParams params = DrinkPotionParams.from(Map.of("type", "fire-resistance"));

        assertEquals(
                "fire-resistance",
                params.type(),
                "drink_potion обязан сохранить тип как есть: нормализацию имени делает скрипт");
    }

    @Test
    void from_MissingType_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> DrinkPotionParams.from(Map.of()),
                "drink_potion без типа обязан отказывать: неизвестно, что пить");

        assertTrue(
                error.getMessage().contains("type"),
                "Отказ обязан называть параметр 'type'. Фактическое: " + error.getMessage());
    }

    @Test
    void from_BlankType_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DrinkPotionParams.from(Map.of("type", " ")),
                "drink_potion с пустым типом обязан отказывать: пустое имя не совпадёт ни с одним зельем");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> DrinkPotionParams.from(null),
                "DrinkPotionParams.from(null) обязан отказывать: без карты параметров скрипт не разобрать");
    }
}
