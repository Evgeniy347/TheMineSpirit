package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SchematicTextParserTestsPart2 {

    private static SchematicDocument parse(String text) {
        return SchematicTextParser.parse(text, "тест.txt");
    }

    private static IllegalStateException rejected(String text, String why) {
        return assertThrows(
                IllegalStateException.class,
                () -> parse(text),
                why
                        + " Иначе тест механику перестанет ловить эту поломку. При смене механику проверь это утверждение. Иначе поломка пройдёт незамеченной в прогоне.");
    }

    private static String box(String legend, String... layers) {
        StringBuilder text = new StringBuilder("size: 2x")
                .append(layers.length)
                .append("x2\nlegend:\n")
                .append(legend)
                .append("layers:\n");
        for (int y = 0; y < layers.length; y++) {
            text.append("  - y").append(y).append(": |\n");
            for (String row : layers[y].split("/", -1)) {
                text.append("      ").append(row).append('\n');
            }
        }
        return text.toString();
    }

    @Test
    void parse_DuplicateKey_IsRejected() {
        IllegalStateException error = rejected(
                "size: 1x1x1\nsize: 2x1x1\n",
                "Повтор ключа обязан отвергаться: второе значение молча перекрыло бы первое");

        assertTrue(
                error.getMessage().contains("строка 2"),
                "Ошибка называет строку повтора: " + error.getMessage()
                        + " Иначе тест parse_DuplicateKey_IsRejected перестанет ловить эту поломку. При смене parse_DuplicateKey_IsRejected проверь это утверждение.");
    }

    @Test
    void parse_UnknownKey_ListsAllowedKeys() {
        IllegalStateException error = rejected("sise: 1x1x1\n", "Опечатка в ключе обязана отвергаться");

        assertTrue(
                error.getMessage().contains("sise") && error.getMessage().contains("size"),
                "Ошибка называет опечатку и допустимые ключи: " + error.getMessage()
                        + " Иначе тест parse_UnknownKey_ListsAllowedKeys перестанет ловить эту поломку. При смене parse_UnknownKey_ListsAllowedKeys проверь это утверждение.");
    }

    @Test
    void parse_EntranceFrontWithoutDoor_IsRejected() {
        rejected(
                "size: 1x1x1\nentrance: front\nlegend:\n  '#': oak_planks\nlayers:\n  - y0: |\n      #\n",
                "entrance: front без двери во фронте обязан отвергаться: вход неизвестен");
    }

    @Test
    void parse_AirInLegend_IsRejected() {
        rejected(box("  'a': air\n", "a./.."), "Воздух в легенде не задаётся: для него есть точка");
    }
}
