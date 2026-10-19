package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DropTableTests {
    private static final String ORIGIN = "test-drops.json";

    static Stream<Arguments> brokenFiles() {
        return Stream.of(
                Arguments.of("не JSON", "{drops", "не разобран JSON"),
                Arguments.of("верхний уровень — список", "[]", "не объект"),
                Arguments.of("пустая таблица", "{}", "таблица пуста"),
                Arguments.of("пустой список блоков", "{\"raw_iron\": []}", "ожидается непустой список"),
                Arguments.of("блок не строка", "{\"raw_iron\": [1]}", "элемент не строка"),
                Arguments.of("ключ-шаблон", "{\"*\": [\"iron_ore\"]}", "не является предметом"),
                Arguments.of("блок-шаблон", "{\"raw_iron\": [\"*_ore\"]}", "не является конкретным id"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("brokenFiles")
    void parse_BrokenFile_ThrowsNamingFileAndProblem(String name, String content, String expectedPart) {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> DropTable.parse(content, ORIGIN),
                "Файл «" + name + "» обязан отклоняться при загрузке: тихая подмена таблицы оставила бы "
                        + "дропы без блоков-источников, и acquire не смог бы добыть руду");

        assertTrue(
                error.getMessage().contains(ORIGIN) && error.getMessage().contains(expectedPart),
                "Ошибка файла «" + name + "» называет файл и проблему «" + expectedPart + "». Фактическое: "
                        + error.getMessage());
    }

    @Test
    void load_ShippedFile_CoversOverworldOresAndCobblestone() {
        DropTable table = DropTable.load();

        assertEquals(
                Optional.of(List.of("iron_ore", "deepslate_iron_ore")),
                table.blocksFor("raw_iron"),
                "Поставляемый drops.json обязан описывать raw_iron через железную и глубинную руду: без этого "
                        + "acquire iron_ingot с нуля не резолвится");
        assertEquals(
                Optional.of(List.of("stone")),
                table.blocksFor("cobblestone"),
                "Поставляемый drops.json обязан описывать cobblestone через камень: булыжник — это ломаный камень");
        assertEquals(
                Optional.of(List.of("coal_ore", "deepslate_coal_ore")),
                table.blocksFor("coal"),
                "Поставляемый drops.json обязан описывать уголь через оба слоя руды");
        assertEquals(
                Optional.of(List.of("diamond_ore", "deepslate_diamond_ore")),
                table.blocksFor("diamond"),
                "Поставляемый drops.json обязан описывать алмаз через оба слоя руды");
    }

    @Test
    void blocksFor_MinecraftPrefixedDrop_ReturnsSameBlocks() {
        DropTable table = DropTable.parse("{\"raw_iron\": [\"iron_ore\"]}", ORIGIN);

        assertEquals(
                Optional.of(List.of("iron_ore")),
                table.blocksFor("minecraft:raw_iron"),
                "Полный id предмета обязан находить те же источники, что и короткий: резолвер работает "
                        + "с обоими написаниями");
    }

    @Test
    void blocksFor_UnknownDrop_ReturnsEmpty() {
        DropTable table = DropTable.parse("{\"raw_iron\": [\"iron_ore\"]}", ORIGIN);

        assertEquals(
                Optional.empty(),
                table.blocksFor("stick"),
                "Неизвестный предмет обязан получить пустой ответ: резолвер тогда честно решит, что способа нет");
    }
}
