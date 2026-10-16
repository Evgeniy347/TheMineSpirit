package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class BoardLogTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static BoardLogEntry entry() {
        return new BoardLogEntry(BoardLog.Event.DONE, 1000, "mine_1", BoardCase.BUILD_MINE, 1,
                "always", 50, 50, List.of("pen_caught_animal"), "11111111-1111-1111-1111-111111111111",
                "hash-1", 24_000);
    }

    @Test
    void write_Parse_RoundTrips() {
        BoardLogEntry entry = entry();

        BoardLogEntry parsed = BoardLog.parse(BoardLog.write(entry));

        assertEquals(entry, parsed,
                "Запись → строка → запись равна исходной: журнал читается после перезапуска без "
                        + "потерь, иначе «выполнено» не восстановить");
    }

    @Test
    void write_EveryEvent_Serializes() {
        for (BoardLog.Event event : BoardLog.Event.values()) {
            BoardLogEntry entry = new BoardLogEntry(event, 10, "t_1", BoardCase.FILL_CRATER, 1,
                    "crater:1:2:3", 35, 35, List.of(), null, "hash", 20);

            assertEquals(entry, BoardLog.parse(BoardLog.write(entry)),
                    "Событие " + event + " сериализуется и читается обратно: каждый переход статуса "
                            + "обязан попадать в журнал");
        }
    }

    @Test
    void write_AllFields_Present() throws Exception {
        String line = BoardLog.write(entry());
        JsonNode root = MAPPER.readTree(line);

        for (String field : List.of("event", "tick", "ticketId", "case", "level", "cause",
                "basePriority", "effectivePriority", "modifiers", "configHash", "worldTime")) {
            assertTrue(root.has(field) && !root.get(field).isNull(),
                    "Поле '" + field + "' обязано присутствовать в строке журнала: " + line);
        }
        assertEquals("DONE", root.get("event").asText(),
                "Событие записано по имени: по нему после перезапуска отличают выполнение от отмены");
        assertEquals("mine_1", root.get("ticketId").asText(), "id задания в журнале назван");
        assertEquals("BUILD_MINE", root.get("case").asText(), "Кейс задания в журнале назван");
        assertEquals("hash-1", root.get("configHash").asText(), "configHash в журнале назван");
    }

    @Test
    void write_NullSpirit_FieldIsNull() throws Exception {
        String line = BoardLog.write(entry());
        JsonNode root = MAPPER.readTree(line);

        assertTrue(root.has("spirit"), "Поле spirit присутствует даже когда духа нет: его отсутствие "
                + "читается как null, а не как потерянное поле");
    }

    @Test
    void parse_BrokenLine_ThrowsNamingLine() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> BoardLog.parse("{не json", 7),
                "Битая строка обязана падать с номером строки: битый хвост журнала не должен "
                        + "молча превращаться в задание");

        assertTrue(failure.getMessage().contains("7"),
                "Сообщение обязано называть номер строки: " + failure.getMessage());
    }

    @Test
    void parse_MissingField_Throws() throws Exception {
        String line = BoardLog.write(entry()).replace("\"configHash\":\"hash-1\",", "");

        assertThrows(IllegalStateException.class, () -> BoardLog.parse(line, 3),
                "Запись без поля configHash обязана падать: без версии конфигурации запись "
                        + "несравнима со временем и восстановление по ней неверно");
    }

    @Test
    void parse_MissingConfigHash_Throws() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new BoardLogEntry(BoardLog.Event.DONE, 1, "t", BoardCase.FILL_CRATER, 1,
                        "always", 0, 0, List.of(), null, null, 1),
                "configHash обязателен и на записи: запись без версии конфигурации не пишется");

        assertTrue(failure.getMessage().contains("configHash"),
                "Сообщение обязано называть configHash: " + failure.getMessage());
    }

    @Test
    void write_LongLine_Throws() {
        BoardLogEntry longEntry = new BoardLogEntry(BoardLog.Event.DONE, 1, "t".repeat(9000),
                BoardCase.FILL_CRATER, 1, "always", 0, 0, List.of(), null, "hash", 1);

        assertThrows(IllegalStateException.class, () -> BoardLog.write(longEntry),
                "Слишком длинная строка обязана падать: одна запись журнала читается одним проходом, "
                        + "а гигантская строка разорвала бы чтение файла");
    }

    @Test
    void parse_NullLine_Throws() {
        assertThrows(IllegalStateException.class, () -> BoardLog.parse(null),
                "Пустая строка обязана падать: запись журнала не может быть пустой");
        assertThrows(IllegalStateException.class, () -> BoardLog.parse("   "),
                "Строка из пробелов обязана падать: это не запись журнала");
    }

    @Test
    void parse_TooLongLine_Throws() {
        String line = "x".repeat(9000);

        assertThrows(IllegalStateException.class, () -> BoardLog.parse(line, 1),
                "Слишком длинная строка обязана падать: оборванная запись не должна молча "
                        + "становиться заданием");
    }

    @Test
    void parse_UnknownEvent_Throws() {
        String line = BoardLog.write(entry()).replace("\"event\":\"DONE\"", "\"event\":\"EXPLODED\"");

        assertThrows(IllegalStateException.class, () -> BoardLog.parse(line, 2),
                "Неизвестное событие обязано падать: чужое слово в поле event молча испортило бы "
                        + "восстановление");
    }

    @Test
    void parse_EmptyModifiers_ReadsAsList() {
        BoardLogEntry entry = new BoardLogEntry(BoardLog.Event.CANCELLED, 1, "t", BoardCase.FILL_CRATER,
                1, "crater:1:2:3", 35, 35, List.of(), null, "hash", 1);

        assertEquals(List.of(), BoardLog.parse(BoardLog.write(entry)).modifiers(),
                "Пустой список модификаторов читается как пустой: журнал не выдумывает сработавших "
                        + "модификаторов");
    }

    @Test
    void parse_NullSpirit_ReadsAsNull() {
        BoardLogEntry entry = new BoardLogEntry(BoardLog.Event.ASSIGNED, 1, "t", BoardCase.FILL_CRATER,
                1, "crater:1:2:3", 35, 35, List.of(), null, "hash", 1);

        assertNull(BoardLog.parse(BoardLog.write(entry)).spirit(),
                "Отсутствующий дух читается как null: задание могло ждать любого свободного духа");
    }

    @Test
    void parse_UnknownCase_Throws() {
        String line = BoardLog.write(entry()).replace("\"case\":\"BUILD_MINE\"", "\"case\":\"BUILD_MINES\"");

        assertThrows(IllegalStateException.class, () -> BoardLog.parse(line, 4),
                "Неизвестный кейс обязан падать: опечатка в записи журнала не должна молча "
                        + "восстановиться");
    }

    @Test
    void write_FieldOrder_IsStable() {
        String first = BoardLog.write(entry());
        String second = BoardLog.write(entry());

        assertEquals(first, second,
                "Порядок полей стабилен: строка журнала детерминирована, иначе сравнение записей "
                        + "между сессиями плывёт");
        assertTrue(first.startsWith("{\"event\":"),
                "Первое поле — event: запись читается с события, а не с середины объекта");
    }

    @Test
    void entry_InvalidValues_AreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new BoardLogEntry(BoardLog.Event.DONE, 1, "t", BoardCase.FILL_CRATER, 1, "c", 0,
                        0, List.of(), null, "hash", -1),
                "Отрицательное мировое время обязано падать");
        assertThrows(IllegalArgumentException.class,
                () -> new BoardLogEntry(BoardLog.Event.DONE, 1, "t", BoardCase.FILL_CRATER, 0, "c", 0,
                        0, List.of(), null, "hash", 1),
                "Нулевой уровень обязан падать");
        assertThrows(IllegalArgumentException.class,
                () -> new BoardLogEntry(BoardLog.Event.DONE, 1, "t", BoardCase.FILL_CRATER, 1, "c", 0,
                        0, List.of(""), null, "hash", 1),
                "Пустое имя модификатора обязано падать");
    }

    @Property
    void write_AnyEntry_RoundTrips(@ForAll("events") BoardLog.Event event,
            @ForAll("times") long tick, @ForAll("ids") String ticketId,
            @ForAll("cases") BoardCase boardCase, @ForAll("levels") int level,
            @ForAll("ids") String cause, @ForAll("priorities") int base,
            @ForAll("priorities") int effective, @ForAll("modifierLists") List<String> modifiers,
            @ForAll("spirits") String spirit, @ForAll("times") long worldTime) {
        BoardLogEntry entry = new BoardLogEntry(event, tick, ticketId, boardCase, level, cause, base,
                effective, modifiers, spirit, "hash", worldTime);

        assertEquals(entry, BoardLog.parse(BoardLog.write(entry)),
                "Любая запись журнала читается обратно без потерь: журнал переживает перезапуск");
    }

    @Property
    void write_AnyEntry_StaysWithinLineLimit(@ForAll("events") BoardLog.Event event,
            @ForAll("ids") String ticketId, @ForAll("cases") BoardCase boardCase,
            @ForAll("levels") int level, @ForAll("ids") String cause,
            @ForAll("priorities") int base, @ForAll("priorities") int effective,
            @ForAll("modifierLists") List<String> modifiers, @ForAll("spirits") String spirit) {
        BoardLogEntry entry = new BoardLogEntry(event, 1, ticketId, boardCase, level, cause, base,
                effective, modifiers, spirit, "hash", 1);

        String line = BoardLog.write(entry);

        assertFalse(line.length() > 8192,
                "Строка журнала в пределах лимита: " + line.length());
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
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(6)
                .list().ofMaxSize(3);
    }

    @Provide
    Arbitrary<String> spirits() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(8)
                .injectNull(0.2);
    }
}
