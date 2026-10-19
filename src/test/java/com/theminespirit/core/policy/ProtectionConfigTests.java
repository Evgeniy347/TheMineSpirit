package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ProtectionConfigTests {
    private static final String ORIGIN = "test-protection.json";

    private static String json(String deny, String natural, String threshold) {
        return "{\"deny\": " + deny + ", \"natural\": " + natural + ", \"buildingThreshold\": " + threshold
                + ", \"maxUnprotectVolume\": 4096}";
    }

    static Stream<Arguments> brokenFiles() {
        return Stream.of(
                Arguments.of("литерал в обоих списках", json("[\"stone\"]", "[\"stone\"]", "6"), "'minecraft:stone' попал и в deny, и в natural"),
                Arguments.of("литерал natural под шаблоном deny", json("[\"*_log\"]", "[\"oak_log\"]", "6"), "'minecraft:oak_log' попал и в deny, и в natural"),
                Arguments.of("литерал deny под шаблоном natural", json("[\"diamond_ore\"]", "[\"*_ore\"]", "6"), "'minecraft:diamond_ore' попал и в deny, и в natural"),
                Arguments.of("одинаковые шаблоны", json("[\"*_log\"]", "[\"*_log\"]", "6"), "'minecraft:*_log' попал и в deny, и в natural"),
                Arguments.of("неизвестное поле", "{\"deny\": [\"a\"], \"natural\": [\"b\"], \"buildingThreshold\": 6, \"maxUnprotectVolume\": 4096, \"extra\": 1}", "неизвестное поле 'extra'"),
                Arguments.of("порог 0", json("[\"a\"]", "[\"b\"]", "0"), "buildingThreshold 0 вне диапазона 1..26"),
                Arguments.of("порог 27", json("[\"a\"]", "[\"b\"]", "27"), "buildingThreshold 27 вне диапазона 1..26"),
                Arguments.of("порог дробный", json("[\"a\"]", "[\"b\"]", "6.5"), "ожидается целое число"),
                Arguments.of("предел объёма 0", "{\"deny\": [\"a\"], \"natural\": [\"b\"], \"buildingThreshold\": 6, \"maxUnprotectVolume\": 0}", "maxUnprotectVolume 0 меньше 1"),
                Arguments.of("пустой deny", json("[]", "[\"b\"]", "6"), "у deny ожидается непустой список"),
                Arguments.of("шаблон с заглавной буквой", json("[\"Stone\"]", "[\"b\"]", "6"), "неверный шаблон"),
                Arguments.of("звёздочка в natural признала бы всё природным", json("[\"a\"]", "[\"*\"]", "6"), "совпадает со всеми блоками"),
                Arguments.of("не JSON", "{deny", "не разобран JSON")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("brokenFiles")
    void parse_BrokenFile_ThrowsNamingFileAndProblem(String name, String content, String expectedPart) {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> ProtectionConfig.parse(content, ORIGIN),
                "Файл «" + name + "» обязан отклоняться при загрузке: тихая подмена конфигурации оставила бы блоки без защиты");

        assertTrue(error.getMessage().contains(ORIGIN) && error.getMessage().contains(expectedPart),
                "Ошибка файла «" + name + "» называет файл и проблему «" + expectedPart + "». Фактическое: " + error.getMessage());
    }

    @Test
    void parse_ValidFile_BuildsProtectionWithGivenThreshold() {
        BlockProtection protection = ProtectionConfig.parse(json("[\"*_door\"]", "[\"stone\"]", "3"), ORIGIN);

        assertEquals(3, protection.buildingThreshold(), "Порог берётся из файла");
        assertTrue(protection.checkOwn("minecraft:oak_door", false) instanceof BlockProtection.Refused,
                "Шаблон deny с пространством имён minecraft: обязан запрещать дверь");
        assertTrue(protection.checkOwn("minecraft:stone", false) instanceof BlockProtection.Allowed,
                "Блок вне шаблона deny обязан оставаться разрешённым");
    }

    @Test
    void load_ShippedFile_IsValid() {
        BlockProtection protection = ProtectionConfig.load();

        assertEquals(6, protection.buildingThreshold(), "Поставляемый protection.json читается без ошибок и несёт порог 6");
        assertEquals(4096, protection.maxUnprotectVolume(), "Поставляемый protection.json несёт предел объёма разрешённой области 4096");
    }
}
