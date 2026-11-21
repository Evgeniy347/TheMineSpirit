package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.api.ConfigStamp;
import com.theminespirit.core.reload.api.TableHolder;
import com.theminespirit.core.reload.api.TableStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class BoardLogTests {

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

    @Test
    void write_HashFromTableStatus_RoundTrips() {
        TableHolder<String> holder = new TableHolder<>(
                "board",
                "v1",
                TableStatus.Source.MOD,
                "mod",
                "hash-from-table",
                Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC));
        BoardLogEntry entry = new BoardLogEntry(
                BoardLog.Event.DONE,
                1,
                "t",
                BoardCase.FILL_CRATER,
                1,
                "always",
                0,
                0,
                List.of(),
                null,
                ConfigStamp.of(holder),
                1);

        BoardLogEntry parsed = BoardLog.parse(BoardLog.write(entry));

        assertEquals(
                "hash-from-table",
                parsed.configHash(),
                "configHash берётся из того же источника, что и записи журнала решений: версия "
                        + "конфигурации одна на запись, а не две разные Иначе тест write_HashFromTableStatus_RoundTrips перестанет ловить эту поломку. При смене write_HashFromTableStatus_RoundTrips проверь это утверждение.");
    }

    @Test
    void write_Parse_RoundTrips() {
        BoardLogEntry entry = entry();
        BoardLogEntry parsed = BoardLog.parse(BoardLog.write(entry));
        assertEquals(
                entry,
                parsed,
                "Запись → строка → запись равна исходной: журнал читается после перезапуска без "
                        + "потерь, иначе «выполнено» не восстановить При смене write_Parse_RoundTrips проверь это утверждение.");
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
}
