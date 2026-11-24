package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.SchematicTextParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SettlementStagePart2Tests {
    private static final List<BoardCase> FIRST_STAGE = List.of(
            BoardCase.CLEAR_GROUNDS,
            BoardCase.BUILD_MINE,
            BoardCase.BUILD_ORCHARD,
            BoardCase.BUILD_HOUSE,
            BoardCase.STOCK_CHESTS,
            BoardCase.CRAFT_FURNACE,
            BoardCase.LIGHTING,
            BoardCase.BUILD_STREETS);

    private static BoardLedger firstStageBuilt() {
        BoardLedger ledger = new BoardLedger();
        FIRST_STAGE.forEach(boardCase -> ledger.note(boardCase, 1));
        SettlementStageTestSupport.noteStorage(ledger);
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

    @Test
    void missing_FirstStageWithoutHouse_ListsHouse() {
        BoardLedger ledger = new BoardLedger();
        for (BoardCase boardCase : FIRST_STAGE) {
            if (boardCase != BoardCase.BUILD_HOUSE) {
                ledger.note(boardCase, 1);
            }
        }
        SettlementStageTestSupport.noteStorage(ledger);
        assertEquals(
                1,
                SettlementStage.of(ledger),
                "Без дома первый этап не пройден"
                        + " Иначе тест missing_FirstStageWithoutHouse_ListsHouse перестанет ловить эту поломку. При смене missing_FirstStageWithoutHouse_ListsHouse проверь это утверждение.");
        assertEquals(
                Map.of("BUILD_HOUSE", 1),
                SettlementStage.missing(ledger),
                "Остаток — дом"
                        + " Иначе тест missing_FirstStageWithoutHouse_ListsHouse перестанет ловить эту поломку. При смене missing_FirstStageWithoutHouse_ListsHouse проверь это утверждение.");
    }

    @Test
    void allows_LevelAboveStage_Locked() {
        BoardLedger ledger = new BoardLedger();
        assertTrue(
                SettlementStage.allows(BoardCase.BUILD_MINE, 1, ledger),
                "Уровень 1 открыт с первого этапа Иначе тест allows_LevelAboveStage_Locked перестанет ловить эту поломку. При смене allows_LevelAboveStage_Locked проверь это утверждение.");
        assertFalse(
                SettlementStage.allows(BoardCase.BUILD_MINE, 2, ledger),
                "Шахта 2 — только с этапа 2 Иначе тест allows_LevelAboveStage_Locked перестанет ловить эту поломку. При смене allows_LevelAboveStage_Locked проверь это утверждение.");
        assertFalse(
                SettlementStage.allows(BoardCase.BUILD_HOUSE, 2, ledger),
                "Дом 2 до этапа 2 не строится Иначе тест allows_LevelAboveStage_Locked перестанет ловить эту поломку. При смене allows_LevelAboveStage_Locked проверь это утверждение.");
        assertTrue(
                SettlementStage.allows(BoardCase.BUILD_MINE, 2, firstStageBuilt()),
                "Этап 2 открыл шахту 2 Иначе тест allows_LevelAboveStage_Locked перестанет ловить эту поломку. При смене allows_LevelAboveStage_Locked проверь это утверждение.");
    }

    @Test
    void submit_LevelAboveStage_StageLocked() {
        Board board = new Board(() -> 0L);
        BoardOutcome outcome = board.submit(mine(2));
        assertEquals(
                BoardOutcome.STAGE_LOCKED,
                outcome,
                "Доска не ставит постройку уровня выше этапа Иначе тест submit_LevelAboveStage_StageLocked перестанет ловить эту поломку. При смене submit_LevelAboveStage_StageLocked проверь это утверждение.");
        assertTrue(
                board.find("mine_level_2").isEmpty(),
                "Запертая постройка не попадает в очередь Иначе тест submit_LevelAboveStage_StageLocked перестанет ловить эту поломку. При смене submit_LevelAboveStage_StageLocked проверь это утверждение.");
    }

    @Test
    void submit_StageReached_Accepted() {
        Board board = new Board(() -> 0L);
        for (BoardCase boardCase : FIRST_STAGE) {
            board.ledger().note(boardCase, 1);
        }
        SettlementStageTestSupport.noteStorage(board.ledger());
        assertEquals(
                BoardOutcome.ACCEPTED,
                board.submit(mine(2)),
                "Этап 2 открывает шахту 2 Иначе тест submit_StageReached_Accepted перестанет ловить эту поломку. При смене submit_StageReached_Accepted проверь это утверждение.");
    }

    @Test
    void rules_StagesOutOfOrder_Rejected() {
        String json = "{\"stages\":[{\"stage\":2,\"buildings\":{}}]}";
        assertThrows(
                IllegalStateException.class,
                () -> StageRules.parse(json, "test"),
                "Этапы — подряд с 1 Иначе тест rules_StagesOutOfOrder_Rejected перестанет ловить эту поломку. При смене rules_StagesOutOfOrder_Rejected проверь это утверждение.");
    }

    @Test
    void rules_Classpath_LevelsWithinCatalog() {
        for (StageRules.Stage stage : StageRules.load().stages()) {
            stage.buildings()
                    .forEach(
                            (boardCase, level) -> assertTrue(
                                    level <= BuildingLevels.maxLevel(boardCase),
                                    "Этап " + stage.stage() + ": " + boardCase + " " + level
                                            + " выше уровней board.json — "
                                            + "этап не пройти Иначе тест rules_Classpath_LevelsWithinCatalog перестанет ловить эту поломку. При смене rules_Classpath_LevelsWithinCatalog проверь это утверждение."));
        }
    }

    @Test
    void rules_Classpath_HouseLevelSchemeOfItsStage() throws IOException {
        StageRules rules = StageRules.load();
        Path root = Path.of("mod/src/main/resources/" + PresetLibrary.SCHEMATICS_ROOT);
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
                            + " дерева рядом, каменный дом — следующий этап (решение 54b п.1) Иначе тест rules_Classpath_HouseLevelSchemeOfItsStage перестанет ловить эту поломку. При смене rules_Classpath_HouseLevelSchemeOfItsStage проверь это утверждение.");
        }
        assertEquals(
                Optional.of("hut_1"),
                rules.scheme(BoardCase.BUILD_HOUSE, 1),
                "Дом этапа 1 — хижина Иначе тест rules_Classpath_HouseLevelSchemeOfItsStage перестанет ловить эту поломку. При смене rules_Classpath_HouseLevelSchemeOfItsStage проверь это утверждение.");
    }

    @Test
    void rules_ClasspathStagesThreeAndFour_SplitMandatoryAndOptional() {
        StageRules rules = StageRules.load();
        assertEquals(
                Set.of("player_house", "forge", "bakery", "barracks"),
                rules.stages().get(2).requires().keySet(),
                "Третий этап держат жильё, кузница, пекарня и казарма: жильё, ремесло, еда, оборона Иначе тест rules_ClasspathStagesThreeAndFour_SplitMandatoryAndOptional перестанет ловить эту поломку. При смене rules_ClasspathStagesThreeAndFour_SplitMandatoryAndOptional проверь это утверждение.");
        assertEquals(
                Set.of("kennel", "stable", "weaver"),
                rules.stages().get(2).optional().keySet(),
                "Псарня, конюшня и ткацкая — желательные: переход не держат. Пасека не в списке: её узел графа "
                        + "ставит улей, а не схему постройки плана Иначе тест rules_ClasspathStagesThreeAndFour_SplitMandatoryAndOptional перестанет ловить эту поломку. При смене rules_ClasspathStagesThreeAndFour_SplitMandatoryAndOptional проверь это утверждение.");
        assertEquals(
                Set.of("town_hall", "tavern", "infirmary", "training_yard"),
                rules.stages().get(3).requires().keySet(),
                "Четвёртый этап держат ратуша, таверна, лазарет и плац Иначе тест rules_ClasspathStagesThreeAndFour_SplitMandatoryAndOptional перестанет ловить эту поломку. При смене rules_ClasspathStagesThreeAndFour_SplitMandatoryAndOptional проверь это утверждение.");
        assertEquals(
                Set.of("enchant_room", "alchemy", "library"),
                rules.stages().get(3).optional().keySet(),
                "Чаровальня, алхимия и библиотека — желательные: переход не держат Иначе тест rules_ClasspathStagesThreeAndFour_SplitMandatoryAndOptional перестанет ловить эту поломку. При смене rules_ClasspathStagesThreeAndFour_SplitMandatoryAndOptional проверь это утверждение.");
        for (int number = 3; number <= 4; number++) {
            int stage = number;
            rules.stages()
                    .get(stage - 1)
                    .requires()
                    .forEach(
                            (id, level) -> assertEquals(
                                    1,
                                    level,
                                    "Этап " + stage + ": '" + id
                                            + "' обязателен первого уровня Иначе тест rules_ClasspathStagesThreeAndFour_SplitMandatoryAndOptional перестанет ловить эту поломку. При смене rules_ClasspathStagesThreeAndFour_SplitMandatoryAndOptional проверь это утверждение."));
            rules.stages()
                    .get(stage - 1)
                    .optional()
                    .forEach(
                            (id, level) -> assertEquals(
                                    1,
                                    level,
                                    "Этап " + stage + ": желательная '" + id
                                            + "' первого уровня Иначе тест rules_ClasspathStagesThreeAndFour_SplitMandatoryAndOptional перестанет ловить эту поломку. При смене rules_ClasspathStagesThreeAndFour_SplitMandatoryAndOptional проверь это утверждение."));
        }
    }

    @Test
    void parse_StageOptional_ParsedSeparately() {
        String json = "{\"stages\":[{\"stage\":1,\"buildings\":{},\"requires\":{\"barn\":1},"
                + "\"optional\":{\"apiary\":1}}]}";
        StageRules rules = StageRules.parse(json, "тест");
        assertEquals(
                Map.of("barn", 1),
                rules.stages().get(0).requires(),
                "Обязательная постройка остаётся в requires Иначе тест parse_StageOptional_ParsedSeparately перестанет ловить эту поломку. При смене parse_StageOptional_ParsedSeparately проверь это утверждение.");
        assertEquals(
                Map.of("apiary", 1),
                rules.stages().get(0).optional(),
                "Желательная постройка читается отдельно и не попадает в обязательные Иначе тест parse_StageOptional_ParsedSeparately перестанет ловить эту поломку. При смене parse_StageOptional_ParsedSeparately проверь это утверждение.");
    }

    @Test
    void parse_OptionalAlsoMandatory_Rejected() {
        String json = "{\"stages\":[{\"stage\":1,\"buildings\":{},\"requires\":{\"barn\":1},"
                + "\"optional\":{\"barn\":1}}]}";
        assertThrows(
                IllegalStateException.class,
                () -> StageRules.parse(json, "тест"),
                "Одна постройка не бывает одновременно обязательной и желательной: переход решает только обязательное Иначе тест parse_OptionalAlsoMandatory_Rejected перестанет ловить эту поломку. При смене parse_OptionalAlsoMandatory_Rejected проверь это утверждение.");
    }

    @Test
    void parse_SchemeOfUnknownCase_Rejected() {
        String json = "{\"stages\": [{\"stage\": 1, \"buildings\": {}}], \"schemes\": {\"NO_SUCH\": [\"hut_1\"]}}";
        assertThrows(
                IllegalStateException.class,
                () -> StageRules.parse(json, "тест"),
                "Схема неизвестного кейса — ошибка данных, а не молча пропущенная строка Иначе тест parse_SchemeOfUnknownCase_Rejected перестанет ловить эту поломку. При смене parse_SchemeOfUnknownCase_Rejected проверь это утверждение.");
    }
}
