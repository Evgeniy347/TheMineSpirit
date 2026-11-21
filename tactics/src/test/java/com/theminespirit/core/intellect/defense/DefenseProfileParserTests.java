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
                "Явная карта обязана читаться: иначе крипер классифицируется профилем, а не данными"
                        + " При смене parse_ValidJson_ReadsAllSections проверь это утверждение.");
        assertEquals(
                TargetClass.MELEE,
                profile.fallback(),
                "Fallback обязан читаться из default: иначе неизвестный тип останется без класса"
                        + " При смене parse_ValidJson_ReadsAllSections проверь это утверждение.");
        assertEquals(
                2,
                profile.priority().size(),
                "Оба порядка специализаций обязаны читаться: иначе одна из них получит чужой приоритет"
                        + " При смене parse_ValidJson_ReadsAllSections проверь это утверждение.");
        assertEquals(
                List.of(TargetClass.SKELETON, TargetClass.MELEE, TargetClass.RANGED),
                profile.priorityWithoutArcher().get(DefenderRole.MELEE),
                "Порядок без лучника обязан читаться: иначе мечник не узнает, что скелет теперь первый"
                        + " При смене parse_ValidJson_ReadsAllSections проверь это утверждение.");
    }

    @Test
    void parse_MissingDefault_ThrowsWithPath() {
        String json = "{\"classes\": {\"minecraft:zombie\": \"MELEE\"},"
                + "\"priority\": {\"ARCHER\": [\"MELEE\"], \"MELEE\": [\"MELEE\"]},"
                + "\"priorityWithoutArcher\": {\"MELEE\": [\"MELEE\"]}}";

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> DefenseProfileParser.fromJson(json),
                "Таблица без default обязана падать: без класса для неизвестного типа новый враг не оценить"
                        + " Иначе тест parse_MissingDefault_ThrowsWithPath перестанет ловить эту поломку. При смене parse_MissingDefault_ThrowsWithPath проверь это утверждение.");

        assertTrue(
                failure.getMessage().contains("default"),
                "Ошибка обязана называть default: иначе непонятно, какой записи не хватает. Факт"
                        + " При смене parse_MissingDefault_ThrowsWithPath проверь это утверждение.");
    }

    @Test
    void parse_MissingPriority_ThrowsWithPath() {
        String json =
                "{\"classes\": {\"default\": \"MELEE\"}," + "\"priorityWithoutArcher\": {\"MELEE\": [\"MELEE\"]}}";

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> DefenseProfileParser.fromJson(json),
                "Таблица без priority обязана падать: без порядка целей бьют кого попало"
                        + " Иначе тест parse_MissingPriority_ThrowsWithPath перестанет ловить эту поломку. При смене parse_MissingPriority_ThrowsWithPath проверь это утверждение.");

        assertTrue(
                failure.getMessage().contains("priority"),
                "Ошибка обязана называть priority: иначе непонятно, какой секции не хватает. Факт"
                        + " При смене parse_MissingPriority_ThrowsWithPath проверь это утверждение.");
    }

    @Test
    void parse_DuplicateClassInPriority_ThrowsWithPath() {
        String json = "{\"classes\": {\"default\": \"MELEE\"},"
                + "\"priority\": {\"ARCHER\": [\"MELEE\", \"MELEE\"], \"MELEE\": [\"MELEE\"]},"
                + "\"priorityWithoutArcher\": {\"MELEE\": [\"MELEE\"]}}";

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> DefenseProfileParser.fromJson(json),
                "Дубль класса в приоритете обязан падать: повтор ломает тай-брейк целей"
                        + " При смене parse_DuplicateClassInPriority_ThrowsWithPath проверь это утверждение.");

        assertTrue(
                failure.getMessage().contains("MELEE"),
                "Ошибка обязана называть задвоенный класс: иначе дубль не найти. Факт"
                        + " При смене parse_DuplicateClassInPriority_ThrowsWithPath проверь это утверждение.");
    }

    @Test
    void parse_EmptyPriorityList_ThrowsWithPath() {
        String json = "{\"classes\": {\"default\": \"MELEE\"},"
                + "\"priority\": {\"ARCHER\": [], \"MELEE\": [\"MELEE\"]},"
                + "\"priorityWithoutArcher\": {\"MELEE\": [\"MELEE\"]}}";

        assertThrows(
                IllegalStateException.class,
                () -> DefenseProfileParser.fromJson(json),
                "Пустой порядок специализации обязан падать: иначе специализация останется без целей"
                        + " При смене parse_EmptyPriorityList_ThrowsWithPath проверь это утверждение.");
    }

    @Test
    void parse_UnknownTargetClass_ThrowsWithPath() {
        String json = "{\"classes\": {\"minecraft:zombie\": \"BOSS\", \"default\": \"MELEE\"},"
                + "\"priority\": {\"ARCHER\": [\"MELEE\"], \"MELEE\": [\"MELEE\"]},"
                + "\"priorityWithoutArcher\": {\"MELEE\": [\"MELEE\"]}}";

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> DefenseProfileParser.fromJson(json),
                "Неизвестный класс цели обязан падать: иначе опечатка даст цели чужой приоритет"
                        + " При смене parse_UnknownTargetClass_ThrowsWithPath проверь это утверждение.");

        assertTrue(
                failure.getMessage().contains("BOSS"),
                "Ошибка обязана называть неизвестное значение: иначе опечатку не найти. Факт"
                        + " При смене parse_UnknownTargetClass_ThrowsWithPath проверь это утверждение.");
    }

    @Test
    void parse_UnknownField_ThrowsWithPath() {
        String json = "{\"classes\": {\"default\": \"MELEE\"}, \"priority\": "
                + "{\"ARCHER\": [\"MELEE\"], \"MELEE\": [\"MELEE\"]},"
                + "\"priorityWithoutArcher\": {\"MELEE\": [\"MELEE\"]}, \"bosses\": []}";

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> DefenseProfileParser.fromJson(json),
                "Неизвестное поле обязано падать: иначе опечатка в схеме молча потеряет секцию"
                        + " При смене parse_UnknownField_ThrowsWithPath проверь это утверждение.");

        assertTrue(
                failure.getMessage().contains("bosses"),
                "Ошибка обязана называть лишнее поле: иначе опечатку не найти. Факт"
                        + " При смене parse_UnknownField_ThrowsWithPath проверь это утверждение.");
    }

    @Test
    void parse_BadTypeKey_ThrowsWithPath() {
        String json = "{\"classes\": {\"zombie\": \"MELEE\", \"default\": \"MELEE\"},"
                + "\"priority\": {\"ARCHER\": [\"MELEE\"], \"MELEE\": [\"MELEE\"]},"
                + "\"priorityWithoutArcher\": {\"MELEE\": [\"MELEE\"]}}";

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> DefenseProfileParser.fromJson(json),
                "Ключ без minecraft: обязан падать: иначе тип не совпадёт с id сущности из мира"
                        + " При смене parse_BadTypeKey_ThrowsWithPath проверь это утверждение.");

        assertTrue(
                failure.getMessage().contains("zombie"),
                "Ошибка обязана называть плохой ключ: иначе его не найти. Факт"
                        + " При смене parse_BadTypeKey_ThrowsWithPath проверь это утверждение.");
    }

    @Test
    void parse_AllRolesCovered_MeleeAndArcherPresent() {
        DefenseProfile profile = DefenseProfileParser.fromJson(DefenseProfileTests.VALID_JSON);

        assertTrue(
                profile.priority().containsKey(DefenderRole.ARCHER)
                        && profile.priority().containsKey(DefenderRole.MELEE),
                "Порядок обязан быть у обеих специализаций: иначе одна получит чужой приоритет. Факт: "
                        + profile.priority().keySet()
                        + " При смене parse_AllRolesCovered_MeleeAndArcherPresent проверь это утверждение.");
        assertTrue(
                profile.priorityWithoutArcher().containsKey(DefenderRole.MELEE),
                "Порядок без лучника обязан быть у мечника: иначе мечник не узнает про скелета первым. Факт: "
                        + profile.priorityWithoutArcher().keySet()
                        + " При смене parse_AllRolesCovered_MeleeAndArcherPresent проверь это утверждение.");
    }

    @Test
    void parse_DuplicateJsonKey_Throws() {
        String json = "{\"classes\": {\"default\": \"MELEE\"}, \"classes\": {\"default\": \"MELEE\"},"
                + "\"priority\": {\"ARCHER\": [\"MELEE\"], \"MELEE\": [\"MELEE\"]},"
                + "\"priorityWithoutArcher\": {\"MELEE\": [\"MELEE\"]}}";

        assertThrows(
                IllegalStateException.class,
                () -> DefenseProfileParser.fromJson(json),
                "Дубль ключа JSON обязан падать: иначе одна из секций молча потеряется"
                        + " При смене parse_DuplicateJsonKey_Throws проверь это утверждение.");
    }
}
