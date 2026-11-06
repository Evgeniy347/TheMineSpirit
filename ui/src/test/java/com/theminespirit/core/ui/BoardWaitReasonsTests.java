package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BoardWaitReasonsTests {
    private static BoardTile tile(
            String id,
            String status,
            String spirit,
            BoardTileGroup group,
            Map<String, Integer> missing,
            List<String> dependsOn) {
        return new BoardTile(id, "BUILD_HOUSE", 1, 10, spirit, status, group, missing, dependsOn, 0, 0, 0, 0);
    }

    @Test
    void of_PendingWithoutSpirit_ReportsFreeSpirit() {
        BoardTile pending = tile("free", "PENDING", null, BoardTileGroup.AVAILABLE, Map.of(), List.of());

        List<BoardWaitReasons.Wait> reasons = BoardWaitReasons.of(pending, List.of(pending));

        assertEquals(
                List.of(BoardWaitReasons.Kind.FREE_SPIRIT),
                reasons.stream().map(BoardWaitReasons.Wait::kind).toList(),
                "Задание без исполнителя ждёт свободного духа, а не абстрактной «выдачи»");
    }

    @Test
    void of_PendingWithSpirit_ReportsThatSpirit() {
        BoardTile pending = tile("taken", "PENDING", "Тихон", BoardTileGroup.AVAILABLE, Map.of(), List.of());

        List<BoardWaitReasons.Wait> reasons = BoardWaitReasons.of(pending, List.of(pending));

        assertEquals(
                List.of(BoardWaitReasons.Kind.SPIRIT),
                reasons.stream().map(BoardWaitReasons.Wait::kind).toList(),
                "Задание с назначенным духом ждёт именно его");
        assertEquals("Тихон", reasons.get(0).subject(), "Причина обязана называть духа по имени");
    }

    @Test
    void of_WaitingResource_ReportsItemAndCount() {
        BoardTile waiting =
                tile("lack", "WAITING_RESOURCES", null, BoardTileGroup.DONE, Map.of("oak_log", 3), List.of());

        List<BoardWaitReasons.Wait> reasons = BoardWaitReasons.of(waiting, List.of(waiting));

        assertEquals(
                List.of(BoardWaitReasons.Kind.RESOURCE),
                reasons.stream().map(BoardWaitReasons.Wait::kind).toList(),
                "Нехватка обычного предмета — причина «ждёт ресурс»");
        assertEquals("oak_log", reasons.get(0).subject(), "Причина обязана называть предмет");
        assertEquals(3, reasons.get(0).count(), "Причина обязана называть недостающее количество");
    }

    @Test
    void of_MissingTool_ReportsToolKind() {
        BoardTile waiting =
                tile("tool", "WAITING_RESOURCES", null, BoardTileGroup.DONE, Map.of("stone_pickaxe", 1), List.of());

        List<BoardWaitReasons.Wait> reasons = BoardWaitReasons.of(waiting, List.of(waiting));

        assertEquals(
                List.of(BoardWaitReasons.Kind.TOOL),
                reasons.stream().map(BoardWaitReasons.Wait::kind).toList(),
                "Нехватка кирки — это ожидание инструмента, а не обычного ресурса");
    }

    @Test
    void of_UnfinishedDependency_ReportsDependencyId() {
        BoardTile before = tile("before", "ASSIGNED", "Тихон", BoardTileGroup.ACTIVE, Map.of(), List.of());
        BoardTile after = tile("after", "PENDING", null, BoardTileGroup.DONE, Map.of(), List.of("before"));

        List<BoardWaitReasons.Wait> reasons = BoardWaitReasons.of(after, List.of(before, after));

        assertEquals(
                List.of(BoardWaitReasons.Kind.DEPENDENCY),
                reasons.stream().map(BoardWaitReasons.Wait::kind).toList(),
                "Незавершённая зависимость — причина ожидания, а не свободный дух");
        assertEquals("before", reasons.get(0).subject(), "Причина обязана называть задание-зависимость");
    }

    @Test
    void of_FailedDependency_ReportsDependencyId() {
        BoardTile before = tile("before", "FAILED", null, BoardTileGroup.DONE, Map.of(), List.of());
        BoardTile after = tile("after", "PENDING", null, BoardTileGroup.DONE, Map.of(), List.of("before"));

        List<BoardWaitReasons.Wait> reasons = BoardWaitReasons.of(after, List.of(before, after));

        assertEquals(
                List.of(BoardWaitReasons.Kind.DEPENDENCY),
                reasons.stream().map(BoardWaitReasons.Wait::kind).toList(),
                "Проваленная зависимость не считается выполненной: задание обязано её ждать");
    }

    @Test
    void of_FinishedDependency_DoesNotReportDependency() {
        BoardTile before = tile("before", "DONE", null, BoardTileGroup.DONE, Map.of(), List.of());
        BoardTile after = tile("after", "PENDING", null, BoardTileGroup.AVAILABLE, Map.of(), List.of("before"));

        List<BoardWaitReasons.Wait> reasons = BoardWaitReasons.of(after, List.of(before, after));

        assertEquals(
                List.of(BoardWaitReasons.Kind.FREE_SPIRIT),
                reasons.stream().map(BoardWaitReasons.Wait::kind).toList(),
                "Завершённая зависимость не мешает заданию ждать свободного духа");
    }

    @Test
    void of_Blocked_ReportsBlocked() {
        BoardTile blocked = tile("blocked", "BLOCKED", null, BoardTileGroup.DONE, Map.of(), List.of());

        List<BoardWaitReasons.Wait> reasons = BoardWaitReasons.of(blocked, List.of(blocked));

        assertEquals(
                List.of(BoardWaitReasons.Kind.BLOCKED),
                reasons.stream().map(BoardWaitReasons.Wait::kind).toList(),
                "Заблокированное задание обязано называть блокировку, а не ждать духа");
    }

    @Test
    void of_AssignedOrDone_ReportsNothing() {
        BoardTile active = tile("active", "ASSIGNED", "Тихон", BoardTileGroup.ACTIVE, Map.of(), List.of());
        BoardTile done = tile("done", "DONE", "Тихон", BoardTileGroup.DONE, Map.of(), List.of());

        assertEquals(
                List.of(),
                BoardWaitReasons.of(active, List.of(active)),
                "Выполняющееся задание не ждёт: причин ожидания нет");
        assertEquals(
                List.of(),
                BoardWaitReasons.of(done, List.of(done)),
                "Завершённое задание не ждёт: причин ожидания нет");
    }
}
