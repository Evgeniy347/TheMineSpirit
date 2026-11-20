package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ProtectedZoneParserTests {
    @Test
    void parse_ValidLine_ProtectsTheBoxInThatDimension() {
        ProtectedZones zones = ProtectedZoneParser.parse(List.of("minecraft:overworld 10 60 -5 20 80 5"));

        assertTrue(
                zones.contains("minecraft:overworld", new BlockCoord(10, 60, -5)),
                "Угол зоны входит в неё, иначе край постройки не защищён. При смене parse проверь оба угла включительно");
        assertTrue(
                zones.contains("minecraft:overworld", new BlockCoord(20, 80, 5)),
                "Противоположный угол входит в зону, иначе зона усечена по одной из осей и часть постройки "
                        + "остаётся без защиты. При смене parse проверь максимум по каждой оси");
        assertFalse(
                zones.contains("minecraft:overworld", new BlockCoord(21, 70, 0)),
                "Блок за гранью зоны не защищён, иначе запрос защиты расширяет границы и закрывает работу за "
                        + "пределами разметки. При смене parse проверь клетку сразу за максимумом");
        assertFalse(
                zones.contains("minecraft:the_nether", new BlockCoord(15, 70, 0)),
                "Другое измерение не защищено тем же боксом, иначе зона Оверворлда блокирует работу в Незере. "
                        + "При смене ключа проверь измерение в строке");
    }

    @Test
    void parse_CornersInReverseOrder_AreNormalized() {
        ProtectedZones zones = ProtectedZoneParser.parse(List.of("minecraft:overworld 20 80 5 10 60 -5"));

        assertTrue(
                zones.contains("minecraft:overworld", new BlockCoord(15, 70, 0)),
                "Углы можно записать в любом порядке: зона строится по минимумам и максимумам, "
                        + "иначе часть разметки окажется пустой. При смене parse проверь обратный порядок координат");
    }

    @Test
    void parse_ExtraSpaces_AreTolerated() {
        ProtectedZones zones = ProtectedZoneParser.parse(List.of("  minecraft:overworld   0 0 0   1 1 1  "));

        assertEquals(
                1,
                zones.size(),
                "Лишние пробелы вокруг частей не ошибка, иначе ручная разметка зон падает при старте. "
                        + "При смене split проверь двойные пробелы и пробелы по краям");
    }

    @Test
    void parse_EmptyList_GivesNoZones() {
        assertEquals(
                0,
                ProtectedZoneParser.parse(List.of()).size(),
                "Пустой список — нет защищённых зон, и это не ошибка старта, иначе сервер без разметки не "
                        + "поднимется. При смене parse проверь пустой конфиг без исключений и с размером 0");
    }

    static Stream<Arguments> brokenLines() {
        return Stream.of(
                Arguments.of("мало частей", "minecraft:overworld 1 2 3 4 5", "найдено 6"),
                Arguments.of("много частей", "minecraft:overworld 1 2 3 4 5 6 7", "найдено 8"),
                Arguments.of(
                        "измерение без пространства имён",
                        "overworld 1 2 3 4 5 6",
                        "измерение 'overworld' записано неверно"),
                Arguments.of(
                        "дробная координата", "minecraft:overworld 1 2.5 3 4 5 6", "координата '2.5' не целое число"),
                Arguments.of(
                        "буква вместо координаты", "minecraft:overworld 1 2 x 4 5 6", "координата 'x' не целое число"),
                Arguments.of("пустая строка", "", "найдено 1"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("brokenLines")
    void parse_BrokenLine_ThrowsNamingTheLineAndProblem(String name, String line, String expectedPart) {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ProtectedZoneParser.parse(List.of("minecraft:overworld 0 0 0 1 1 1", line)),
                "Строка «" + name + "» обязана отклоняться при старте: тихий пропуск оставил бы постройки без защиты. "
                        + "При смене parse проверь каждую битую строку таблицы brokenLines");

        assertTrue(
                error.getMessage().startsWith("protectedZones, строка №2")
                        && error.getMessage().contains(expectedPart),
                "Ошибка называет номер строки и проблему «" + expectedPart + "». Фактическое: " + error.getMessage()
                        + ". При смене формата ошибки проверь, что номер строки считается с единицы, а не с нуля");
    }

    @Test
    void parse_MoreThanSixtyFourLines_ThrowsWithCount() {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 65; i++) {
            lines.add("minecraft:overworld " + i + " 0 0 " + i + " 1 1");
        }

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ProtectedZoneParser.parse(lines),
                "65 зон — сверх предела 64, иначе разметка растёт без ограничения и проверка защиты тормозит тик");

        assertTrue(
                error.getMessage().startsWith("Защищённых зон 65"),
                "Ошибка называет число зон, иначе непонятно, сколько строк лишние. Фактическое: " + error.getMessage()
                        + ". При смене предела сверь границу 64 и 65");
    }
}
