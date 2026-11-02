package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StageWorldGateTests {
    private static final List<BoardCase> FIRST_STAGE = List.of(
            BoardCase.CLEAR_GROUNDS,
            BoardCase.BUILD_MINE,
            BoardCase.BUILD_FENCE,
            BoardCase.BUILD_ORCHARD,
            BoardCase.BUILD_HOUSE,
            BoardCase.STOCK_CHESTS,
            BoardCase.CRAFT_FURNACE,
            BoardCase.LIGHTING,
            BoardCase.BUILD_STREETS);
    private static final String LIVE_STAGE_ONE = "/live/observe18/stage-one.txt";

    @Test
    void gate_LiveObserve18JournalTwo_WorldKeepsFirstStage() throws IOException {
        BoardLedger ledger = journalStageTwo();
        StageWorld world = world(live());

        StageGate gate = SettlementStage.gate(ledger, world);

        assertEquals(2, SettlementStage.of(ledger), "Журнал observe18 считает этап 1 закрытым");
        assertEquals(
                1,
                gate.stage(),
                "В мире шахта 96% и двор 91%: этап 1 закрыт не целиком, гейт обязан оставить его открытым");
        assertEquals(
                Map.of("BUILD_MINE", 1, "STOCK_CHESTS", 1),
                gate.missing(),
                "Причина на доске называет неготовые части мира: шахту и двор");
        assertFalse(
                SettlementStage.allows(BoardCase.BUILD_MINE, 2, gate.stage()),
                "Пока этап 1 не закрыт целиком, шахта уровня 2 не открывается");
        assertTrue(
                SettlementStage.allows(BoardCase.BUILD_MINE, 2, SettlementStage.of(ledger)),
                "Журнальный гейт без мира по-прежнему открыл бы шахту 2 — его и заменяет гейт мира");
    }

    @Test
    void gate_WorldComplete_OpensSecondStage() throws IOException {
        StageGate gate = SettlementStage.gate(journalStageTwo(), world(complete(live())));

        assertEquals(2, gate.stage(), "Все части этапа 1 стоят в мире — этап 2 открыт");
        assertTrue(gate.missing().containsKey("BUILD_WAREHOUSE"), "Остаток этапа 2 считает склад уровня 2");
        assertEquals(2, gate.missing().get("BUILD_WAREHOUSE"), "Склад этапа 2 — второго уровня");
    }

    @Test
    void gate_WorldSilent_KeepsJournal() {
        StageGate gate = SettlementStage.gate(journalStageTwo(), StageWorld.ANY);

        assertEquals(2, gate.stage(), "Мир без данных не запирает этап: решает журнал");
    }

    @Test
    void submit_StageGateOverride_LocksNextStage() {
        Board board = new Board(() -> 0L);
        FIRST_STAGE.forEach(boardCase -> board.ledger().note(boardCase, 1));
        assertEquals(
                BoardOutcome.ACCEPTED,
                board.submit(mine(2, "mine_level_2")),
                "По журналу этап 2 открыт и задание шахты 2 принимается");
        board.ledger().gate(new StageGate(1, Map.of("BUILD_MINE", 1)));

        assertEquals(
                BoardOutcome.STAGE_LOCKED,
                board.submit(mine(2, "mine_level_2_again")),
                "Гейт мира запирает уровень 2, пока этап 1 не закрыт целиком");
        assertEquals(1, board.ledger().stage(), "Доска показывает этап мира, а не журнала");
    }

    private static BoardLedger journalStageTwo() {
        BoardLedger ledger = new BoardLedger();
        FIRST_STAGE.forEach(boardCase -> ledger.note(boardCase, 1));
        ledger.note(BoardCase.BUILD_MINE, 2);
        ledger.note(BoardCase.BUILD_HOUSE, 2);
        return ledger;
    }

    private static BoardTicket mine(int level, String id) {
        return new BoardTicket(
                id,
                BoardCase.BUILD_MINE,
                Optional.empty(),
                50,
                0L,
                level,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
    }

    private static Map<String, int[]> live() throws IOException {
        return fixture(LIVE_STAGE_ONE);
    }

    private static Map<String, int[]> complete(Map<String, int[]> source) {
        Map<String, int[]> complete = new LinkedHashMap<>();
        source.forEach((name, counts) -> complete.put(name, new int[] {counts[1], counts[1]}));
        return complete;
    }

    private static Map<String, int[]> fixture(String resource) throws IOException {
        Map<String, int[]> parsed = new LinkedHashMap<>();
        try (InputStream stream = StageWorldGateTests.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("Нет живой фикстуры " + resource);
            }
            for (String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .toList()) {
                String text = line.trim();
                if (text.isEmpty() || text.startsWith("#")) {
                    continue;
                }
                int eq = text.indexOf('=');
                int slash = text.indexOf('/');
                parsed.put(text.substring(0, eq), new int[] {
                    Integer.parseInt(text.substring(eq + 1, slash)), Integer.parseInt(text.substring(slash + 1))
                });
            }
        }
        return parsed;
    }

    private static StageWorld world(Map<String, int[]> progress) {
        return new StageWorld() {
            @Override
            public boolean built(BoardCase boardCase, int level) {
                int[] counts = progress.get(boardCase.name());
                return level != 1 || counts == null || (counts[1] > 0 && counts[0] >= counts[1]);
            }

            @Override
            public boolean builtPlan(String id, int level) {
                return true;
            }
        };
    }
}
