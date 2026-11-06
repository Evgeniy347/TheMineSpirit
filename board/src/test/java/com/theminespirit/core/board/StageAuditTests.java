package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StageAuditTests {
    private static final int REBUILD_PERCENT = BoardTuning.REBUILD_PERCENT;

    @Test
    void unbuilt_FenceMissing144Of146_IsConstructionNotRepair() {
        assertEquals(
                List.of(true, false),
                List.of(StageAudit.unbuilt(144, 146, REBUILD_PERCENT), StageAudit.unbuilt(5, 146, REBUILD_PERCENT)),
                "INC-87: ограды нет на 98% — это шаг стройки с материалами, а не ремонт; 5 из 146 — ремонт "
                        + "построенной и повреждённой");
    }

    @Test
    void shortage_RepairWithoutFences_TicketWaitsNamingMaterial() {
        Map<String, Integer> need = new HashMap<>(Map.of("jungle_planks", 120, "stick", 0));
        Board board = new Board(() -> 0);
        board.submit(new BoardTicket(
                "repair:build_fence",
                BoardCase.REPAIR_BUILDING,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.REPAIR_BUILDING),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                StageAudit.shortage(need)));

        StockView empty = new StockView(Map.of());
        assertEquals(
                List.of(List.of(), Map.of("jungle_planks", 120)),
                List.of(
                        board.claimable(empty, 0),
                        ResourceGate.missing(new StockNeed(StageAudit.shortage(need)), empty)),
                "Ремонт без материалов не выдаётся: ждёт с недостачей «доски×120» (по ней рубка и крафт), а не "
                        + "ведёт духа вдоль линии с пустыми руками");
    }

    @Test
    void allDone_PartsPending_NotRebuilt() {
        assertEquals(
                List.of(false, false, true),
                List.of(
                        StageAudit.allDone(List.of(BoardStatus.DONE, BoardStatus.PENDING)),
                        StageAudit.allDone(List.of()),
                        StageAudit.allDone(List.of(BoardStatus.DONE, BoardStatus.DONE))),
                "Частичную стройку не переоткрывают: шаг ограды закрыт, только когда закрыты все его части");
    }

    @Test
    void nothingToBuild_EmptyPlan_NamesReason() {
        assertEquals(
                List.of(false, true),
                List.of(
                        StageAudit.nothingToBuild(1).isPresent(),
                        StageAudit.nothingToBuild(0).isPresent()),
                "Пустой план стройки закрывается с причиной, а не «сделано»: иначе доска выдаёт задание по кругу");
        assertTrue(
                StageAudit.nothingToBuild(0).orElseThrow().contains("0 блоков"),
                "Причина пустого плана называет цену: в работе 0 блоков — духу нечего строить");
    }

    @Test
    void progressed_RepairClosedWithNothingPlaced_IsNotDone() {
        assertEquals(
                List.of(false, true, true),
                List.of(
                        StageAudit.progressed(2, 2, 146),
                        StageAudit.progressed(2, 30, 146),
                        StageAudit.progressed(146, 146, 146)),
                "INC-87: задание стройки, закрытое без единого нового блока, — провал с причиной, а не «сделано»: "
                        + "иначе аудит каждые ~110 с выдаёт его заново тому же духу");
    }
}
