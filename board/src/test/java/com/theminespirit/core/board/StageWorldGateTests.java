package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

        StageGate gate = SettlementStage.gate(world);

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
        StageGate gate = SettlementStage.gate(world(complete(live())));

        assertEquals(2, gate.stage(), "Все части этапа 1 стоят в мире — этап 2 открыт");
        assertTrue(gate.missing().containsKey("BUILD_WAREHOUSE"), "Остаток этапа 2 считает склад уровня 2");
        assertEquals(2, gate.missing().get("BUILD_WAREHOUSE"), "Склад этапа 2 — второго уровня");
    }

    @Test
    void gate_WorldEmpty_JournalStillFirst() {
        StageGate gate = SettlementStage.gate(emptyWorld());

        assertEquals(
                1,
                gate.stage(),
                "Журнал observe18 считает этап 1 закрытым, но в мире ничего нет — этап остаётся первым: "
                        + "отметки журнала этап не закрывают");
        assertTrue(
                gate.missing().containsKey("BUILD_MINE"),
                "Причина видна на доске: шахта в мире не подтверждена. Остаток: " + gate.missing());
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

    @Test
    void stages_MandatoryAuditsOfEveryStage_OpenNextOnlyByWorld() {
        StageRules rules = StageRules.load();
        ModelWorld world = new ModelWorld();
        StageConfirmation confirmation = new StageConfirmation(1, 1);

        for (int number = 1; number <= rules.stages().size(); number++) {
            assertEquals(
                    number,
                    SettlementStage.of(world),
                    "Этап " + number + " открыт только после аудита всех предыдущих этапов");
            assertEquals(
                    number, confirmation.gate(world).stage(), "Гейт стоит на подтверждённом аудите этапа " + number);

            built(world, rules.stages().get(number - 1));
            assertEquals(
                    number + 1,
                    SettlementStage.of(world),
                    "Обязательные постройки этапа " + number + " из аудита мира закрывают его целиком; "
                            + "желательные (optional) переход не держат");
        }
        assertEquals(5, SettlementStage.of(world), "После четвёртого этапа все этапы закрыты");
    }

    @Test
    void journal_AllMarksWithoutWorld_StaysFirstStage() {
        StageRules rules = StageRules.load();
        BoardLedger journal = new BoardLedger();
        rules.stages().forEach(stage -> {
            stage.buildings().forEach((boardCase, level) -> journal.note(boardCase, level));
            stage.requires().forEach((id, level) -> notePlan(journal, rules, id, level));
            stage.optional().forEach((id, level) -> notePlan(journal, rules, id, level));
        });

        assertEquals(
                5,
                SettlementStage.of(journal),
                "Журнал «построено» считает все этапы закрытыми; остаток " + SettlementStage.missing(journal));
        assertEquals(
                1,
                new StageConfirmation(1, 1).gate(new ModelWorld()).stage(),
                "Пустой мир при полном журнале оставляет первый этап: отметки журнала этап не закрывают");
    }

    @Test
    void damagedMandatory_SingleBadAudit_KeepsStageThreeAuditsRollBack() {
        StageRules rules = StageRules.load();
        ModelWorld world = new ModelWorld();
        rules.stages().forEach(stage -> built(world, stage));
        StageConfirmation confirmation = new StageConfirmation(5);

        world.removePlan("forge", 1);
        assertEquals(3, SettlementStage.of(world), "Повреждённая кузница не даёт закрыть третий этап");
        assertEquals(5, confirmation.observe(3), "Один плохой аудит этап не откатывает");
        assertEquals(5, confirmation.observe(3), "Второй плохой аудит этап ещё не откатывает");
        assertEquals(3, confirmation.observe(3), "Третий плохой аудит подряд откатывает этап до мира");
        assertEquals(
                3,
                confirmation.observe(3),
                "Подтверждённый откат держится: этап 3 не закрыт, пока кузница не восстановлена");
    }

    private static void built(ModelWorld world, StageRules.Stage stage) {
        stage.buildings().forEach(world::add);
        stage.requires().forEach(world::addPlan);
    }

    private static void notePlan(BoardLedger ledger, StageRules rules, String id, int level) {
        BoardCase boardCase = rules.planCases().get(id);
        if (boardCase == null) {
            ledger.notePlan(id, level);
        } else {
            ledger.note(boardCase, level);
        }
    }

    private static final class ModelWorld implements StageWorld {
        private final Set<String> built = new HashSet<>();

        private void add(BoardCase boardCase, int level) {
            built.add(boardCase.name() + ":" + level);
        }

        private void addPlan(String id, int level) {
            built.add(id + ":" + level);
        }

        private void removePlan(String id, int level) {
            built.remove(id + ":" + level);
        }

        @Override
        public boolean built(BoardCase boardCase, int level) {
            return built.contains(boardCase.name() + ":" + level);
        }

        @Override
        public boolean builtPlan(String id, int level) {
            return built.contains(id + ":" + level);
        }
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
                if (level != 1) {
                    return false;
                }
                int[] counts = progress.get(boardCase.name());
                return counts == null || (counts[1] > 0 && counts[0] >= counts[1]);
            }

            @Override
            public boolean builtPlan(String id, int level) {
                return true;
            }
        };
    }

    private static StageWorld emptyWorld() {
        return new StageWorld() {
            @Override
            public boolean built(BoardCase boardCase, int level) {
                return false;
            }

            @Override
            public boolean builtPlan(String id, int level) {
                return false;
            }
        };
    }
}
