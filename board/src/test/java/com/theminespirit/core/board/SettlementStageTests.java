package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.api.BoardCase;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        assertEquals(
                1,
                SettlementStage.of(new BoardLedger()),
                "До построек поселение на первом этапе Иначе тест of_NothingBuilt_FirstStage перестанет ловить эту поломку. При смене of_NothingBuilt_FirstStage проверь это утверждение.");
    }

    @Test
    void of_HouseLevelTwoAlone_StaysFirstStage() {
        BoardLedger ledger = new BoardLedger();
        ledger.note(BoardCase.BUILD_HOUSE, 1);
        ledger.note(BoardCase.BUILD_HOUSE, 2);
        assertEquals(
                1,
                SettlementStage.of(ledger),
                "Этап — по всем постройкам этапа, а не по уровню дома Иначе тест of_HouseLevelTwoAlone_StaysFirstStage перестанет ловить эту поломку. При смене of_HouseLevelTwoAlone_StaysFirstStage проверь это утверждение.");
    }

    @Test
    void of_AllFirstStageBuilt_SecondStage() {
        BoardLedger ledger = firstStageBuilt();
        assertEquals(
                2,
                SettlementStage.of(ledger),
                "Расчистка, шахта, забор, сад, дом, склад и улицы 1 — переход на этап 2 Иначе тест of_AllFirstStageBuilt_SecondStage перестанет ловить эту поломку. При смене of_AllFirstStageBuilt_SecondStage проверь это утверждение.");
        assertEquals(
                Map.ofEntries(
                        Map.entry("BUILD_MINE", 2),
                        Map.entry("BUILD_STREETS", 2),
                        Map.entry("BUILD_WAREHOUSE", 2),
                        Map.entry("barn", 1),
                        Map.entry("family_house_a", 1),
                        Map.entry("family_house_b", 1),
                        Map.entry("family_house_c", 1),
                        Map.entry("field_a", 1),
                        Map.entry("field_b", 1),
                        Map.entry("lumber_yard", 1),
                        Map.entry("pen", 1),
                        Map.entry("plaza", 1),
                        Map.entry("storage", 1),
                        Map.entry("well", 1),
                        Map.entry("workshop", 1)),
                SettlementStage.missing(ledger),
                "Остаток этапа 2 — постройки уровня 2 и весь план этапа из stages.json Иначе тест of_AllFirstStageBuilt_SecondStage перестанет ловить эту поломку. При смене of_AllFirstStageBuilt_SecondStage проверь это утверждение.");
    }

    @Test
    void missing_SecondStageWithoutBarn_StageTwo() {
        BoardLedger ledger = firstStageBuilt();
        ledger.note(BoardCase.BUILD_MINE, 2);
        ledger.note(BoardCase.BUILD_WAREHOUSE, 1);
        ledger.note(BoardCase.BUILD_WAREHOUSE, 2);
        ledger.note(BoardCase.BUILD_STREETS, 2);
        ledger.note(BoardCase.BUILD_PEN, 1);
        ledger.restore(Set.of(
                "building:plaza:1",
                "building:family_house_a:1",
                "building:family_house_b:1",
                "building:family_house_c:1",
                "building:field_a:1",
                "building:field_b:1",
                "building:well:1",
                "building:lumber_yard:1",
                "building:workshop:1"));
        Map<?, ?> missing = SettlementStage.missing(ledger);
        assertEquals(
                2,
                SettlementStage.of(ledger),
                "Без амбара этап 2 не закрыт: состав этапа — план поселения Иначе тест missing_SecondStageWithoutBarn_StageTwo перестанет ловить эту поломку. При смене missing_SecondStageWithoutBarn_StageTwo проверь это утверждение.");
        assertEquals(
                Set.of("barn"),
                missing.keySet(),
                "Остаток этапа 2 называет амбар, а не три кейса доски Иначе тест missing_SecondStageWithoutBarn_StageTwo перестанет ловить эту поломку. При смене missing_SecondStageWithoutBarn_StageTwo проверь это утверждение.");
        assertEquals(
                1,
                missing.get("barn"),
                "Амбар этапа 2 — уровень 1 Иначе тест missing_SecondStageWithoutBarn_StageTwo перестанет ловить эту поломку. При смене missing_SecondStageWithoutBarn_StageTwo проверь это утверждение.");
    }

    private static BoardLedger firstStageBuilt() {
        BoardLedger ledger = new BoardLedger();
        FIRST_STAGE.forEach(boardCase -> ledger.note(boardCase, 1));
        return ledger;
    }
}
