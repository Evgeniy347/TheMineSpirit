package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LiveFixtureTests {
    @Test
    void parse_SkipsCommentsFeetAndBlank_ReadsCells() throws IOException {
        String text = """
                # снимок 06.11
                feet 0 0 0

                1 2 3 SOFT
                -4 5 -6 HARD
                """;

        Map<BlockCoord, BlockKind> cells = parse(text, "фикстура.live");

        assertEquals(
                Map.of(new BlockCoord(1, 2, 3), BlockKind.SOFT, new BlockCoord(-4, 5, -6), BlockKind.HARD),
                cells,
                "Живая фикстура — это только строки клеток: комментарий, строка feet и пустая строка клетками не "
                        + "считаются, иначе разбор падал бы на каждой живой фикстуре");
    }

    @Test
    void parse_LineWithoutFourFields_NamesLineAndSource() {
        String text = """
                feet 0 0 0
                1 2 3 SOFT
                1 2 SOFT
                """;

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> parse(text, "яма.live"),
                "Строка живой фикстуры из трёх полей обязана падать с ошибкой: иначе клетка мира молча теряется");

        assertTrue(
                error.getMessage().contains("Строка 3"),
                "Ошибка обязана называть место в фикстуре, иначе при разборе снимка непонятно, какую строку править: "
                        + error.getMessage());
        assertTrue(
                error.getMessage().contains("яма.live"),
                "Ошибка обязана называть фикстуру: у ямы и башни формат общий, и без имени файла непонятно, "
                        + "чей снимок править: " + error.getMessage());
    }

    @Test
    void parse_UnknownKind_NamesKind() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> parse("0 0 0 NETHER\n", "яма.live"),
                "Неизвестный вид блока обязан падать: иначе живая фикстура молча даёт дырявую модель мира");

        assertTrue(
                error.getMessage().contains("NETHER"),
                "Ошибка обязана называть неизвестный вид блока: молча пропущенная клетка сделала бы модель мира "
                        + "дырявой: " + error.getMessage());
    }

    @Test
    void load_RealFixture_IsReadByBothWorlds() throws IOException {
        Map<BlockCoord, BlockKind> cells = LiveFixture.load("/live/pit-warehouse-0611.live");

        assertFalse(cells.isEmpty(), "Живая фикстура ямы обязана читаться общим разбором: путь к ресурсу сломан");
    }

    private static Map<BlockCoord, BlockKind> parse(String text, String source) throws IOException {
        return LiveFixture.parse(new BufferedReader(new StringReader(text)), source);
    }
}
