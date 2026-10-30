package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.SettlementStage;
import com.theminespirit.core.board.StageRules;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StageTwoClosureTests {
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
    void plan_AllStageTwoBuildingsExceptBarn_StageTwoStaysOpen() {
        Board board = stageTwoReady();
        StageRules rules = StageRules.load();

        for (String id : rules.stages().get(1).plan()) {
            if ("barn".equals(id) || rules.planCases().containsKey(id)) {
                continue;
            }
            built(board, id);
        }

        assertEquals(
                2,
                SettlementStage.of(board.ledger()),
                "Без амбара этап 2 не закрыт: состав этапа — план stages.json, а не только три кейса");
        assertEquals(
                Set.of("barn"),
                SettlementStage.missing(board.ledger()).keySet(),
                "Остаток этапа 2 после всех построек плана — только амбар");
    }

    @Test
    void plan_AllStageTwoBuildingsBuilt_StageThreeOpened() {
        Board board = stageTwoReady();
        StageRules rules = StageRules.load();

        for (String id : rules.stages().get(1).plan()) {
            if (rules.planCases().containsKey(id)) {
                continue;
            }
            built(board, id);
        }

        assertEquals(3, SettlementStage.of(board.ledger()), "Все постройки плана этапа 2 построены — этап 2 закрыт");
    }

    private static Board stageTwoReady() {
        Board board = new Board(() -> 0L);
        FIRST_STAGE.forEach(boardCase -> board.ledger().note(boardCase, 1));
        board.ledger().note(BoardCase.BUILD_MINE, 2);
        board.ledger().note(BoardCase.BUILD_WAREHOUSE, 1);
        board.ledger().note(BoardCase.BUILD_WAREHOUSE, 2);
        board.ledger().note(BoardCase.BUILD_STREETS, 2);
        board.ledger().note(BoardCase.BUILD_PEN, 1);
        return board;
    }

    private static void built(Board board, String planId) {
        BoardTicket ticket = new BoardTicket(
                "graph:building:" + planId,
                BoardCase.BUILD_PLACED,
                Optional.empty(),
                50,
                0L,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
        board.submit(ticket);
        board.claim(ticket.id(), UUID.nameUUIDFromBytes(planId.getBytes(StandardCharsets.UTF_8)), 0L);
        TicketTarget target =
                TicketTarget.at(new BlockCoord(0, 64, 0)).withBuilding(planId).withScheme("field_1");
        PlanCompletion.complete(board, ticket, target);
    }
}
