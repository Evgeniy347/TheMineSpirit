package com.theminespirit.core.tactics.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TacticsAdapterSettingsTests {
    private static final String GOOD = "{\"evalIntervalTicks\": 10, \"maxEnemies\": 16, \"factRadius\": 32, "
            + "\"factChunkBudget\": 64, \"retreatHoldTicks\": 200, \"journalCapacity\": 32, "
            + "\"journalMaxPerSecond\": 1}";

    @Test
    void defaults_DocumentedNumbers_MatchShippedResource() {
        TacticsAdapterSettings defaults = TacticsAdapterSettings.defaults();

        assertEquals(List.of(10, 16, 32, 64, 200, 32, 1),
                List.of(defaults.evalIntervalTicks(), defaults.maxEnemies(), defaults.factRadius(),
                        defaults.factChunkBudget(), defaults.retreatHoldTicks(), defaults.journalCapacity(),
                        defaults.journalMaxPerSecond()),
                "Умолчания адаптера: снятие фактов раз в 10 тиков, не более 16 врагов, радиус поиска 32, бюджет 64 "
                        + "чтения блока, удержание отхода 200 тиков, журнал на 32 записи не чаще одной в секунду. "
                        + "Изменили число — обновите adapter.json и docs/tactics/README.md");
        assertEquals(TacticsAdapterSettings.defaults(), TacticsAdapterSettings.loadDefault(),
                "adapter.json обязан повторять умолчания: иначе числа кода и данных разойдутся молча");
    }

    static Stream<Arguments> acceptedBounds() {
        return Stream.of(
                Arguments.of("evalIntervalTicks", 1), Arguments.of("evalIntervalTicks", 6000),
                Arguments.of("maxEnemies", 1), Arguments.of("maxEnemies", 16),
                Arguments.of("factRadius", 1), Arguments.of("factRadius", 256),
                Arguments.of("factChunkBudget", 1), Arguments.of("factChunkBudget", 1024),
                Arguments.of("retreatHoldTicks", 1), Arguments.of("retreatHoldTicks", 6000),
                Arguments.of("journalCapacity", 1), Arguments.of("journalCapacity", 256),
                Arguments.of("journalMaxPerSecond", 1), Arguments.of("journalMaxPerSecond", 20));
    }

    @ParameterizedTest(name = "{0} = {1}")
    @MethodSource("acceptedBounds")
    void constructor_ValueAtBoundary_IsAccepted(String field, int value) {
        TacticsAdapterSettings settings = with(field, value);

        assertEquals(value, read(settings, field),
                "Граница поля " + field + " входит в допустимый диапазон: конструктор держит границы чисел, а не парсер");
    }

    static Stream<Arguments> rejectedBounds() {
        return Stream.of(
                Arguments.of("evalIntervalTicks", 0), Arguments.of("evalIntervalTicks", 6001),
                Arguments.of("maxEnemies", 0), Arguments.of("maxEnemies", 17),
                Arguments.of("factRadius", 0), Arguments.of("factRadius", 257),
                Arguments.of("factChunkBudget", 0), Arguments.of("factChunkBudget", 1025),
                Arguments.of("retreatHoldTicks", 0), Arguments.of("retreatHoldTicks", 6001),
                Arguments.of("journalCapacity", 0), Arguments.of("journalCapacity", 257),
                Arguments.of("journalMaxPerSecond", 0), Arguments.of("journalMaxPerSecond", 21));
    }

    @ParameterizedTest(name = "{0} = {1}")
    @MethodSource("rejectedBounds")
    void constructor_ValueOutsideBounds_IsRejected(String field, int value) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> with(field, value),
                "Число адаптера вне границ обязано отклоняться конструктором: иначе чтения блоков или время "
                        + "удержания отхода уйдут за бюджет тика");

        assertTrue(error.getMessage().contains(field),
                "Сообщение называет поле " + field + ", чтобы правка шла по подсказке; получили: " + error.getMessage());
    }

    @Test
    void fromJson_GoodText_LoadsAllFields() {
        assertEquals(TacticsAdapterSettings.defaults(), TacticsAdapterSettings.fromJson(GOOD),
                "Полный adapter.json читается в запись без подстановок: все семь полей обязательны");
    }

    @Test
    void fromJson_UnknownField_IsRejectedWithFileAndField() {
        String text = GOOD.replace("}", ", \"extra\": 1}");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> TacticsAdapterSettings.fromJson(text),
                "Лишнее поле в adapter.json — ошибка, а не молчаливый пропуск: иначе опечатка в имени поля "
                        + "оставит настройку умолчанию");

        assertTrue(error.getMessage().contains("adapter.json") && error.getMessage().contains("extra"),
                "Сообщение называет файл и поле; получили: " + error.getMessage());
    }

    @Test
    void fromJson_MissingField_IsRejectedWithField() {
        String text = GOOD.replace("\"factRadius\": 32, ", "");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> TacticsAdapterSettings.fromJson(text),
                "Без факт-радиуса адаптер не знает, кого считать врагом: загрузка обязана назвать пропущенное поле");

        assertTrue(error.getMessage().contains("factRadius"),
                "Сообщение называет факт-радиус; получили: " + error.getMessage());
    }

    @Test
    void fromJson_WrongType_IsRejectedWithField() {
        String text = GOOD.replace("\"factRadius\": 32", "\"factRadius\": \"32\"");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> TacticsAdapterSettings.fromJson(text),
                "Число в кавычках — ошибка схемы: парсер читает число без границ, но тип проверяет");

        assertTrue(error.getMessage().contains("factRadius"),
                "Сообщение называет поле с неверным типом; получили: " + error.getMessage());
    }

    @Test
    void fromJson_OutOfRangeValue_IsRejectedWithFileAndField() {
        String text = GOOD.replace("\"factRadius\": 32", "\"factRadius\": 300");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> TacticsAdapterSettings.fromJson(text),
                "Факт-радиус 300 вне границ конструктора: загрузка обязана отклонить файл, а не чинить число молча");

        assertTrue(error.getMessage().contains("adapter.json") && error.getMessage().contains("factRadius"),
                "Сообщение называет файл и поле; получили: " + error.getMessage());
    }

    @Test
    void fromJson_DuplicateKey_IsRejected() {
        String text = GOOD.replace("\"factRadius\": 32", "\"factRadius\": 32, \"factRadius\": 32");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> TacticsAdapterSettings.fromJson(text),
                "Дубль ключа — ошибка: какое из двух значений верно, неизвестно");

        assertTrue(error.getMessage().contains("adapter.json"),
                "Сообщение называет файл; получили: " + error.getMessage());
    }

    @Test
    void fromJson_MalformedText_IsRejected() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> TacticsAdapterSettings.fromJson("{"),
                "Сломанный JSON отклоняется на разборе с указанием файла, а не падает позже в бою");

        assertTrue(error.getMessage().contains("adapter.json"),
                "Сообщение называет файл; получили: " + error.getMessage());
    }

    @Test
    void fromJson_ArrayRoot_IsRejected() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> TacticsAdapterSettings.fromJson("[]"),
                "Корень adapter.json — объект, а не массив: иначе поля неоткуда взять");

        assertTrue(error.getMessage().contains("adapter.json") && error.getMessage().contains("объект"),
                "Сообщение называет файл и требует объект в корне; получили: " + error.getMessage());
    }

    private static TacticsAdapterSettings with(String field, int value) {
        TacticsAdapterSettings defaults = TacticsAdapterSettings.defaults();
        return switch (field) {
            case "evalIntervalTicks" -> new TacticsAdapterSettings(value, defaults.maxEnemies(),
                    defaults.factRadius(), defaults.factChunkBudget(), defaults.retreatHoldTicks(),
                    defaults.journalCapacity(), defaults.journalMaxPerSecond());
            case "maxEnemies" -> new TacticsAdapterSettings(defaults.evalIntervalTicks(), value,
                    defaults.factRadius(), defaults.factChunkBudget(), defaults.retreatHoldTicks(),
                    defaults.journalCapacity(), defaults.journalMaxPerSecond());
            case "factRadius" -> new TacticsAdapterSettings(defaults.evalIntervalTicks(), defaults.maxEnemies(),
                    value, defaults.factChunkBudget(), defaults.retreatHoldTicks(), defaults.journalCapacity(),
                    defaults.journalMaxPerSecond());
            case "factChunkBudget" -> new TacticsAdapterSettings(defaults.evalIntervalTicks(), defaults.maxEnemies(),
                    defaults.factRadius(), value, defaults.retreatHoldTicks(), defaults.journalCapacity(),
                    defaults.journalMaxPerSecond());
            case "retreatHoldTicks" -> new TacticsAdapterSettings(defaults.evalIntervalTicks(), defaults.maxEnemies(),
                    defaults.factRadius(), defaults.factChunkBudget(), value, defaults.journalCapacity(),
                    defaults.journalMaxPerSecond());
            case "journalCapacity" -> new TacticsAdapterSettings(defaults.evalIntervalTicks(), defaults.maxEnemies(),
                    defaults.factRadius(), defaults.factChunkBudget(), defaults.retreatHoldTicks(), value,
                    defaults.journalMaxPerSecond());
            case "journalMaxPerSecond" -> new TacticsAdapterSettings(defaults.evalIntervalTicks(),
                    defaults.maxEnemies(), defaults.factRadius(), defaults.factChunkBudget(),
                    defaults.retreatHoldTicks(), defaults.journalCapacity(), value);
            default -> throw new IllegalArgumentException("Неизвестное поле настройки адаптера: " + field);
        };
    }

    private static int read(TacticsAdapterSettings settings, String field) {
        return switch (field) {
            case "evalIntervalTicks" -> settings.evalIntervalTicks();
            case "maxEnemies" -> settings.maxEnemies();
            case "factRadius" -> settings.factRadius();
            case "factChunkBudget" -> settings.factChunkBudget();
            case "retreatHoldTicks" -> settings.retreatHoldTicks();
            case "journalCapacity" -> settings.journalCapacity();
            case "journalMaxPerSecond" -> settings.journalMaxPerSecond();
            default -> throw new IllegalArgumentException("Неизвестное поле настройки адаптера: " + field);
        };
    }
}
