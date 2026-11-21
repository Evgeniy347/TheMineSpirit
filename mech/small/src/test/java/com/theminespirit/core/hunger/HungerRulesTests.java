package com.theminespirit.core.hunger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class HungerRulesTests {
    private static final String VALID = """
            {"regenFoodThreshold": 18, "regenIntervalTicks": 80, "regenExhaustion": 6.0,
             "starveIntervalTicks": 80, "starveFloor": 1.0, "eatFoodThreshold": 14,
             "sprintExhaustionPerMeter": 0.1, "combatExhaustionPerAttack": 0.1,
             "digExhaustionPerBlock": 0.005, "damageExhaustionPerHit": 0.1}
            """;

    @Test
    void parse_ValidJson_MatchesDefaults() {
        assertEquals(
                HungerRules.defaults(),
                HungerRules.parse(VALID, "hunger.json"),
                "Файл данных обязан совпадать с умолчаниями, иначе быстрое ядро и адаптер расходятся в числах и "
                        + "голод ведёт себя по-разному. При смене HungerRules.parse проверь строку загрузки ниже в "
                        + "тесте");
    }

    @Test
    void load_ClasspathResource_Reads() {
        assertEquals(
                HungerRules.defaults(),
                HungerRules.load(),
                "Файл data/theminespirit/hunger/hunger.json обязан читаться и совпадать с умолчаниями: скорости "
                        + "истощения — данные, а не ветки в коде, иначе баланс меняется без данных. При смене "
                        + "HungerRules.load проверь строку умолчаний ниже в тесте");
    }

    @Test
    void loadDefault_WithoutBinding_ReadsClasspath() {
        assertEquals(
                HungerRules.load(),
                HungerRules.loadDefault(),
                "Без подмены таблицы потребитель обязан получать файл data/theminespirit/hunger/hunger.json, иначе "
                        + "до первого /reload числа брались бы из ниоткуда. При смене loadDefault проверь строку выше "
                        + "в тесте");
    }

    @Test
    void parse_UnknownField_Throws() {
        String json = VALID.replace("\"regenExhaustion\": 6.0", "\"regenExhaustion\": 6.0, \"вкус\": 1");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> HungerRules.parse(json, "hunger.json"),
                "Неизвестное поле ломает загрузку: опечатка в данных не должна молча ложиться в умолчания, иначе "
                        + "настройка не применяется. При смене parse проверь строку имени ниже в тесте");

        assertEquals(
                true,
                error.getMessage().contains("вкус"),
                "Сообщение называет лишнее поле: " + error.getMessage() + ", иначе игрок не найдёт опечатку "
                        + "и данные не исправит. При "
                        + "смене текста проверь строку порога ниже в тесте");
    }

    @Test
    void parse_OutOfRangeThreshold_Throws() {
        assertThrows(
                IllegalStateException.class,
                () -> HungerRules.parse(
                        VALID.replace("\"regenFoodThreshold\": 18", "\"regenFoodThreshold\": 25"), "hunger.json"),
                "Порог восстановления выше шкалы голода отвергается, иначе дух никогда не лечился бы. При смене "
                        + "parse проверь строку нуля ниже в тесте");
    }

    @Test
    void parse_ZeroInterval_Throws() {
        assertThrows(
                IllegalStateException.class,
                () -> HungerRules.parse(
                        VALID.replace("\"starveIntervalTicks\": 80", "\"starveIntervalTicks\": 0"), "hunger.json"),
                "Нулевой интервал голода отвергается, иначе урон капал бы каждый тик. При смене parse проверь строку "
                        + "расхода ниже в тесте");
    }

    @Test
    void parse_NegativeRate_Throws() {
        assertThrows(
                IllegalStateException.class,
                () -> HungerRules.parse(
                        VALID.replace("\"combatExhaustionPerAttack\": 0.1", "\"combatExhaustionPerAttack\": -1"),
                        "hunger.json"),
                "Отрицательный расход отвергается: голод не отматывается назад, иначе расход кормит духа. При смене "
                        + "parse проверь строку выше в тесте");
    }
}
