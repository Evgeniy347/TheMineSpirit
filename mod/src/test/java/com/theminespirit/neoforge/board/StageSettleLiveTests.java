package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.LiveBoardFixture;
import com.theminespirit.core.board.StageAudit;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StageSettleLiveTests {
    private static final String LIVE_BOARD = "/live/minereopen15/board.json";

    @Test
    void settle_LiveDoneMineWithInfeasibleGraph_IsReadyForAudit() throws IOException {
        Board board = LiveBoardFixture.load(LIVE_BOARD);
        BoardTicket mine = board.find("first:build_mine")
                .orElseThrow(() -> new AssertionError("В живой доске observe15 нет first:build_mine"));
        assertEquals(
                BoardStatus.DONE,
                board.statusOf("first:build_mine").orElseThrow(),
                "Живая шахта observe15 закрыта доской: " + mine);
        assertEquals(
                59, mine.requiredItems().get("cobblestone"), "Живой тикет шахты несёт 59 недостающих полов: " + mine);
        assertEquals(
                StageAudit.Decision.AUDIT,
                StageAudit.decide(BoardStatus.DONE, true, 1524 - 59, 1524),
                "Шахта DONE при 96% обязана идти в аудит/ремонт независимо от того, поставил ли граф задачу: "
                        + "в observe15 graph:building:mine был infeasible и аудит не запускался");
    }

    @Test
    void levelRepair_LiveBoard_ExistsWithEmptyPlan() throws IOException {
        Board board = LiveBoardFixture.load(LIVE_BOARD);
        BoardTicket repair = board.find("repair:level:23338210")
                .orElseThrow(() -> new AssertionError("В живой доске observe15 нет repair:level:23338210"));

        assertEquals(
                Map.of(), repair.requiredItems(), "Ремонт выравнивания в живой доске идёт без материалов: " + repair);
        assertEquals(
                BoardStatus.ASSIGNED,
                board.statusOf(repair.id()).orElseThrow(),
                "Живой repair:level уже выдан духу: " + repair);
    }
}
