package com.theminespirit.core.intellect.defense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DefenseProfileParserTests {
    @Test
    void parse_ValidJson_ReadsAllSections() {
        DefenseProfile profile = DefenseProfileParser.fromJson(DefenseProfileTests.VALID_JSON);

        assertEquals(
                TargetClass.CREEPER,
                profile.classes().get("minecraft:creeper"),
                "Явная карта обязана читаться: иначе крипер классифицируется профилем, а не данными");
        assertEquals(
                TargetClass.MELEE,
                profile.fallback(),
                "Fallback обязан читаться из default: иначе неизвестный тип останется без класса");
        assertEquals(
                2,
                profile.priority().size(),
                "Оба порядка специализаций обязаны читаться: иначе одна из них получит чужой приоритет");
        assertEquals(
                List.of(TargetClass.SKELETON, TargetClass.MELEE, TargetClass.RANGED),
                profile.priorityWithoutArcher().get(DefenderRole.MELEE),
                "Порядок без лучника обязан читаться: иначе мечник не узнает, что скелет теперь первый");
    }

    @Test
    void parse_MissingDefault_ThrowsWithPath() {
        String json = "{\"classes\": {\"minecraft:zombie\": \"MELEE\"},"
                + "\"priority\": {\"ARCHER\": [\"MELEE\"], \"MELEE\": [\"MELEE\"]},"
                + "\"priorityWithoutArcher\": {\"MELEE\": [\"MELEE\"]}}";

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> DefenseProfileParser.fromJson(json),
                "Таблица без default обязана падать: без класса для неизвестного типа новый враг не оценить");

        assertTrue(
                failure.getMessage().contains("default"),
                "Ошибка обязана называть default: иначе непонятно, какой записи не хватает. Факт");
    }

    @Test
    void parse_MissingPriority_ThrowsWithPath() {
        String json =
                "{\"classes\": {\"default\": \"MELEE\"}," + "\"priorityWithoutArcher\": {\"MELEE\": [\"MELEE\"]}}";

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> DefenseProfileParser.fromJson(json),
                "Таблица без priority обязана падать: без порядка целей бьют кого попало");

        assertTrue(
                failure.getMessage().contains("priority"),
                "Ошибка обязана называть priority: иначе непонятно, какой секции не хватает. Факт");
    }

    @Test
    void parse_DuplicateClassInPriority_ThrowsWithPath() {
        String json = "{\"classes\": {\"default\": \"MELEE\"},"
                + "\"priority\": {\"ARCHER\": [\"MELEE\", \"MELEE\"], \"MELEE\": [\"MELEE\"]},"
                + "\"priorityWithoutArcher\": {\"MELEE\": [\"MELEE\"]}}";

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> DefenseProfileParser.fromJson(json),
                "Дубль класса в приоритете обязан падать: повтор ломает тай-брейк целей");

        assertTrue(
                failure.getMessage().contains("MELEE"),
                "Ошибка обязана называть задвоенный класс: иначе дубль не найти. Факт");
    }

    @Test
    void parse_EmptyPriorityList_ThrowsWithPath() {
        String json = "{\"classes\": {\"default\": \"MELEE\"},"
                + "\"priority\": {\"ARCHER\": [], \"MELEE\": [\"MELEE\"]},"
                + "\"priorityWithoutArcher\": {\"MELEE\": [\"MELEE\"]}}";

        assertThrows(
                IllegalStateException.class,
                () -> DefenseProfileParser.fromJson(json),
                "Пустой порядок специализации обязан падать: иначе специализация останется без целей");
    }

    @Test
    void parse_UnknownTargetClass_ThrowsWithPath() {
        String json = "{\"classes\": {\"minecraft:zombie\": \"BOSS\", \"default\": \"MELEE\"},"
                + "\"priority\": {\"ARCHER\": [\"MELEE\"], \"MELEE\": [\"MELEE\"]},"
                + "\"priorityWithoutArcher\": {\"MELEE\": [\"MELEE\"]}}";

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> DefenseProfileParser.fromJson(json),
                "Неизвестный класс цели обязан падать: иначе опечатка даст цели чужой приоритет");

        assertTrue(
                failure.getMessage().contains("BOSS"),
                "Ошибка обязана называть неизвестное значение: иначе опечатку не найти. Факт");
    }

    @Test
    void parse_UnknownField_ThrowsWithPath() {
        String json = "{\"classes\": {\"default\": \"MELEE\"}, \"priority\": "
                + "{\"ARCHER\": [\"MELEE\"], \"MELEE\": [\"MELEE\"]},"
                + "\"priorityWithoutArcher\": {\"MELEE\": [\"MELEE\"]}, \"bosses\": []}";

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> DefenseProfileParser.fromJson(json),
                "Неизвестное поле обязано падать: иначе опечатка в схеме молча потеряет секцию");

        assertTrue(
                failure.getMessage().contains("bosses"),
                "Ошибка обязана называть лишнее поле: иначе опечатку не найти. Факт");
    }

    @Test
    void parse_BadTypeKey_ThrowsWithPath() {
        String json = "{\"classes\": {\"zombie\": \"MELEE\", \"default\": \"MELEE\"},"
                + "\"priority\": {\"ARCHER\": [\"MELEE\"], \"MELEE\": [\"MELEE\"]},"
                + "\"priorityWithoutArcher\": {\"MELEE\": [\"MELEE\"]}}";

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> DefenseProfileParser.fromJson(json),
                "Ключ без minecraft: обязан падать: иначе тип не совпадёт с id сущности из мира");

        assertTrue(
                failure.getMessage().contains("zombie"),
                "Ошибка обязана называть плохой ключ: иначе его не найти. Факт");
    }

    @Test
    void parse_AllRolesCovered_MeleeAndArcherPresent() {
        DefenseProfile profile = DefenseProfileParser.fromJson(DefenseProfileTests.VALID_JSON);

        assertTrue(
                profile.priority().containsKey(DefenderRole.ARCHER)
                        && profile.priority().containsKey(DefenderRole.MELEE),
                "Порядок обязан быть у обеих специализаций: иначе одна получит чужой приоритет. Факт: "
                        + profile.priority().keySet());
        assertTrue(
                profile.priorityWithoutArcher().containsKey(DefenderRole.MELEE),
                "Порядок без лучника обязан быть у мечника: иначе мечник не узнает про скелета первым. Факт: "
                        + profile.priorityWithoutArcher().keySet());
    }

    @Test
    void parse_DuplicateJsonKey_Throws() {
        String json = "{\"classes\": {\"default\": \"MELEE\"}, \"classes\": {\"default\": \"MELEE\"},"
                + "\"priority\": {\"ARCHER\": [\"MELEE\"], \"MELEE\": [\"MELEE\"]},"
                + "\"priorityWithoutArcher\": {\"MELEE\": [\"MELEE\"]}}";

        assertThrows(
                IllegalStateException.class,
                () -> DefenseProfileParser.fromJson(json),
                "Дубль ключа JSON обязан падать: иначе одна из секций молча потеряется");
    }
}
