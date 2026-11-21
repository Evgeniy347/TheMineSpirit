package com.theminespirit.neoforge.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ModVersionTests {
    @Test
    void toString_MinorAndBuild_JoinsWithDot() {
        assertEquals(
                "0.1.1991",
                ModVersion.of("0.1", 1991).toString(),
                "Версия — минор и номер сборки через точку: по ней сверяются jar, лог и команда"
                        + " Иначе тест toString_MinorAndBuild_JoinsWithDot перестанет ловить эту поломку. При смене toString_MinorAndBuild_JoinsWithDot проверь это утверждение.");
    }

    @Test
    void of_MinorWithSpaces_Stripped() {
        assertEquals(
                "0.1.1991",
                ModVersion.of(" 0.1 ", 1991).toString(),
                "Пробелы вокруг минора из gradle.properties не должны попадать в имя jar и протокол"
                        + " Иначе тест of_MinorWithSpaces_Stripped перестанет ловить эту поломку. При смене of_MinorWithSpaces_Stripped проверь это утверждение.");
    }

    @Test
    void parse_FullVersion_ReadsMinorAndBuild() {
        ModVersion version = ModVersion.parse("0.1.1991");

        assertEquals(
                "0.1",
                version.minor(),
                "Из 0.1.1991 минор читается как 0.1"
                        + " Иначе тест parse_FullVersion_ReadsMinorAndBuild перестанет ловить эту поломку. При смене parse_FullVersion_ReadsMinorAndBuild проверь это утверждение.");
        assertEquals(
                1991,
                version.build(),
                "Из 0.1.1991 номер сборки читается как 1991"
                        + " Иначе тест parse_FullVersion_ReadsMinorAndBuild перестанет ловить эту поломку. При смене parse_FullVersion_ReadsMinorAndBuild проверь это утверждение.");
    }

    @Test
    void of_ZeroBuild_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ModVersion.of("0.1", 0),
                "Нулевой номер сборки не называют версией: пустая сборка не должна молча стать 0.1.0"
                        + " Иначе тест of_ZeroBuild_Rejected перестанет ловить эту поломку. При смене of_ZeroBuild_Rejected проверь это утверждение.");
    }

    @Test
    void of_NegativeBuild_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ModVersion.of("0.1", -1),
                "Отрицательный номер сборки не бывает: git rev-list --count не может дать минус"
                        + " Иначе тест of_NegativeBuild_Rejected перестанет ловить эту поломку. При смене of_NegativeBuild_Rejected проверь это утверждение.");
    }

    @Test
    void of_MinorWithPatch_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ModVersion.of("0.1.5", 1991),
                "Минор — только два числа (0.1): третье число здесь номер сборки, а не патч"
                        + " Иначе тест of_MinorWithPatch_Rejected перестанет ловить эту поломку. При смене of_MinorWithPatch_Rejected проверь это утверждение.");
    }

    @Test
    void parseBuild_NotNumber_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ModVersion.parseBuild("abc"),
                "Номер сборки из git обязан быть числом, иначе версия не форматируется"
                        + " При смене parseBuild_NotNumber_Rejected проверь это утверждение.");
    }

    @Test
    void parseBuild_TooLarge_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ModVersion.parseBuild("99999999999999999999"),
                "Номер сборки, не влезающий в int, — ошибка, а не молчаливое переполнение"
                        + " Иначе тест parseBuild_TooLarge_Rejected перестанет ловить эту поломку. При смене parseBuild_TooLarge_Rejected проверь это утверждение.");
    }

    @Test
    void parse_WithoutBuild_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ModVersion.parse("0.1."),
                "Версия без номера сборки не описывает сборку: разбор обязан отказать"
                        + " Иначе тест parse_WithoutBuild_Rejected перестанет ловить эту поломку. При смене parse_WithoutBuild_Rejected проверь это утверждение.");
    }

    @Test
    void parse_Text_KeepsMinorAndBuild() {
        assertEquals(
                "0.2.7",
                ModVersion.parse(" 0.2.7 ").toString(),
                "Разбор версии терпит окружающие пробелы, но отдаёт канонический вид 0.2.7"
                        + " Иначе тест parse_Text_KeepsMinorAndBuild перестанет ловить эту поломку. При смене parse_Text_KeepsMinorAndBuild проверь это утверждение.");
    }
}
