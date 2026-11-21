package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.board.api.BoardCase;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class BoardLogPart2Tests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static BoardLogEntry entry() {
        return new BoardLogEntry(
                BoardLog.Event.DONE,
                1000,
                "mine_1",
                BoardCase.BUILD_MINE,
                1,
                "always",
                50,
                50,
                List.of("pen_caught_animal"),
                "11111111-1111-1111-1111-111111111111",
                "hash-1",
                24_000);
    }

    @Provide
    Arbitrary<BoardLog.Event> events() {
        return Arbitraries.of(BoardLog.Event.values());
    }

    @Provide
    Arbitrary<Long> times() {
        return Arbitraries.longs().between(0, 1_000_000L);
    }

    @Provide
    Arbitrary<String> ids() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(8);
    }

    @Provide
    Arbitrary<BoardCase> cases() {
        return Arbitraries.of(BoardCase.values());
    }

    @Provide
    Arbitrary<Integer> levels() {
        return Arbitraries.integers().between(1, 4);
    }

    @Provide
    Arbitrary<Integer> priorities() {
        return Arbitraries.integers().between(0, BoardTuning.MAX_PRIORITY);
    }

    @Provide
    Arbitrary<List<String>> modifierLists() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1)
                .ofMaxLength(6)
                .list()
                .ofMaxSize(3);
    }

    @Provide
    Arbitrary<String> spirits() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1)
                .ofMaxLength(8)
                .injectNull(0.2);
    }

    @Test
    void write_EveryEvent_Serializes() {
        for (BoardLog.Event event : BoardLog.Event.values()) {
            BoardLogEntry entry = new BoardLogEntry(
                    event, 10, "t_1", BoardCase.FILL_CRATER, 1, "crater:1:2:3", 35, 35, List.of(), null, "hash", 20);
            assertEquals(
                    entry,
                    BoardLog.parse(BoardLog.write(entry)),
                    "Событие " + event + " сериализуется и читается обратно: каждый переход статуса "
                            + "обязан попадать в журнал Иначе тест write_EveryEvent_Serializes перестанет ловить эту поломку. При смене write_EveryEvent_Serializes проверь это утверждение.");
        }
    }

    @Test
    void write_AllFields_Present() throws Exception {
        String line = BoardLog.write(entry());
        JsonNode root = MAPPER.readTree(line);
        for (String field : List.of(
                "event",
                "tick",
                "ticketId",
                "case",
                "level",
                "cause",
                "basePriority",
                "effectivePriority",
                "modifiers",
                "configHash",
                "worldTime")) {
            assertTrue(
                    root.has(field) && !root.get(field).isNull(),
                    "Поле '" + field
                            + "' обязано присутствовать в строке журнала:  Иначе тест write_AllFields_Present перестанет ловить эту поломку. При смене write_AllFields_Present проверь это утверждение."
                            + line);
        }
        assertEquals(
                "DONE",
                root.get("event").asText(),
                "Событие записано по имени: по нему после перезапуска отличают выполнение от отмены Иначе тест write_AllFields_Present перестанет ловить эту поломку. При смене write_AllFields_Present проверь это утверждение.");
        assertEquals(
                "mine_1",
                root.get("ticketId").asText(),
                "id задания в журнале назван Иначе тест write_AllFields_Present перестанет ловить эту поломку. При смене write_AllFields_Present проверь это утверждение.");
        assertEquals(
                "BUILD_MINE",
                root.get("case").asText(),
                "Кейс задания в журнале назван Иначе тест write_AllFields_Present перестанет ловить эту поломку. При смене write_AllFields_Present проверь это утверждение.");
        assertEquals(
                "hash-1",
                root.get("configHash").asText(),
                "configHash в журнале назван Иначе тест write_AllFields_Present перестанет ловить эту поломку. При смене write_AllFields_Present проверь это утверждение.");
    }

    @Test
    void write_NullSpirit_FieldIsNull() throws Exception {
        String line = BoardLog.write(entry());
        JsonNode root = MAPPER.readTree(line);
        assertTrue(
                root.has("spirit"),
                "Поле spirit присутствует даже когда духа нет: его отсутствие "
                        + "читается как null, а не как потерянное поле Иначе тест write_NullSpirit_FieldIsNull перестанет ловить эту поломку. При смене write_NullSpirit_FieldIsNull проверь это утверждение.");
    }

    @Test
    void parse_BrokenLine_ThrowsNamingLine() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> BoardLog.parse("{не json", 7),
                "Битая строка обязана падать с номером строки: битый хвост журнала не должен "
                        + "молча превращаться в задание Иначе тест parse_BrokenLine_ThrowsNamingLine перестанет ловить эту поломку. При смене parse_BrokenLine_ThrowsNamingLine проверь это утверждение.");
        assertTrue(
                failure.getMessage().contains("7"),
                "Сообщение обязано называть номер строки:  Иначе тест parse_BrokenLine_ThrowsNamingLine перестанет ловить эту поломку. При смене parse_BrokenLine_ThrowsNamingLine проверь это утверждение."
                        + failure.getMessage());
    }
}
