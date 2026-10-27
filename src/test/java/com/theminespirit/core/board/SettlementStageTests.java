package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.SchematicTextParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SettlementStageTests {
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

    @Test
    void of_NothingBuilt_FirstStage() {
        assertEquals(1, SettlementStage.of(new BoardLedger()), "До построек поселение на первом этапе");
    }

    @Test
    void of_HouseLevelTwoAlone_StaysFirstStage() {
        BoardLedger ledger = new BoardLedger();
        ledger.note(BoardCase.BUILD_HOUSE, 1);
        ledger.note(BoardCase.BUILD_HOUSE, 2);

        assertEquals(1, SettlementStage.of(ledger), "Этап — по всем постройкам этапа, а не по уровню дома");
    }

    @Test
    void of_AllFirstStageBuilt_SecondStage() {
        BoardLedger ledger = firstStageBuilt();

        assertEquals(
                2,
                SettlementStage.of(ledger),
                "Расчистка, шахта, забор, сад, дом, склад и улицы 1 — переход на этап 2");
        assertEquals(
                Map.of(BoardCase.BUILD_MINE, 2, BoardCase.BUILD_WAREHOUSE, 2, BoardCase.BUILD_STREETS, 2),
                SettlementStage.missing(ledger),
                "Остаток этапа 2 — постройки уровня 2 из stages.json");
    }

    @Test
    void missing_FirstStageWithoutFence_ListsFence() {
        BoardLedger ledger = new BoardLedger();
        for (BoardCase boardCase : FIRST_STAGE) {
            if (boardCase != BoardCase.BUILD_FENCE) {
                ledger.note(boardCase, 1);
            }
        }

        assertEquals(1, SettlementStage.of(ledger), "Без забора первый этап не пройден");
        assertEquals(Map.of(BoardCase.BUILD_FENCE, 1), SettlementStage.missing(ledger), "Остаток — забор");
    }

    @Test
    void allows_LevelAboveStage_Locked() {
        BoardLedger ledger = new BoardLedger();

        assertTrue(SettlementStage.allows(BoardCase.BUILD_MINE, 1, ledger), "Уровень 1 открыт с первого этапа");
        assertFalse(SettlementStage.allows(BoardCase.BUILD_MINE, 2, ledger), "Шахта 2 — только с этапа 2");
        assertFalse(SettlementStage.allows(BoardCase.BUILD_HOUSE, 2, ledger), "Дом 2 до этапа 2 не строится");
        assertTrue(SettlementStage.allows(BoardCase.BUILD_MINE, 2, firstStageBuilt()), "Этап 2 открыл шахту 2");
    }

    @Test
    void submit_LevelAboveStage_StageLocked() {
        Board board = new Board(() -> 0L);
        BoardOutcome outcome = board.submit(mine(2));

        assertEquals(BoardOutcome.STAGE_LOCKED, outcome, "Доска не ставит постройку уровня выше этапа");
        assertTrue(board.find("mine_level_2").isEmpty(), "Запертая постройка не попадает в очередь");
    }

    @Test
    void submit_StageReached_Accepted() {
        Board board = new Board(() -> 0L);
        for (BoardCase boardCase : FIRST_STAGE) {
            board.ledger().note(boardCase, 1);
        }

        assertEquals(BoardOutcome.ACCEPTED, board.submit(mine(2)), "Этап 2 открывает шахту 2");
    }

    @Test
    void rules_StagesOutOfOrder_Rejected() {
        String json = "{\"stages\":[{\"stage\":2,\"buildings\":{}}]}";

        assertThrows(IllegalStateException.class, () -> StageRules.parse(json, "test"), "Этапы — подряд с 1");
    }

    @Test
    void rules_Classpath_LevelsWithinCatalog() {
        for (StageRules.Stage stage : StageRules.load().stages()) {
            stage.buildings()
                    .forEach((boardCase, level) -> assertTrue(
                            level <= BuildingLevels.maxLevel(boardCase),
                            "Этап " + stage.stage() + ": " + boardCase + " " + level + " выше уровней board.json — "
                                    + "этап не пройти"));
        }
    }

    @Test
    void rules_Classpath_HouseLevelSchemeOfItsStage() throws IOException {
        StageRules rules = StageRules.load();
        Path root = Path.of("src/main/resources/" + PresetLibrary.SCHEMATICS_ROOT);

        for (int level = 1; level <= 2; level++) {
            String scheme = rules.scheme(BoardCase.BUILD_HOUSE, level).orElseThrow();
            Path file = root.resolve(scheme + ".txt");
            Optional<Integer> stage = SchematicTextParser.parse(Files.readString(file), file.toString())
                    .header()
                    .stage();
            assertEquals(
                    Optional.of(level),
                    stage,
                    "Дом уровня " + level + " (" + scheme + ") — схема этапа " + level + ": хижина этапа 1 строится из"
                            + " дерева рядом, каменный дом — следующий этап (решение 54b п.1)");
        }
        assertEquals(Optional.of("hut_1"), rules.scheme(BoardCase.BUILD_HOUSE, 1), "Дом этапа 1 — хижина");
    }

    @Test
    void parse_SchemeOfUnknownCase_Rejected() {
        String json = "{\"stages\": [{\"stage\": 1, \"buildings\": {}}], \"schemes\": {\"NO_SUCH\": [\"hut_1\"]}}";

        assertThrows(
                IllegalStateException.class,
                () -> StageRules.parse(json, "тест"),
                "Схема неизвестного кейса — ошибка данных, а не молча пропущенная строка");
    }

    @Test
    void logCap_ByStage_DoublesFromSixtyFour() {
        assertEquals(64, SettlementStage.logCap(1), "Первый этап — 64 бревна (владелец 23.10)");
        assertEquals(128, SettlementStage.logCap(2), "Каждый следующий этап удваивает предел");
        assertEquals(256, SettlementStage.logCap(3), "Каждый следующий этап удваивает предел");
    }

    private static BoardLedger firstStageBuilt() {
        BoardLedger ledger = new BoardLedger();
        FIRST_STAGE.forEach(boardCase -> ledger.note(boardCase, 1));
        return ledger;
    }

    private static BoardTicket mine(int level) {
        return new BoardTicket(
                "mine_level_" + level,
                BoardCase.BUILD_MINE,
                Optional.empty(),
                50,
                0L,
                level,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
    }
}
