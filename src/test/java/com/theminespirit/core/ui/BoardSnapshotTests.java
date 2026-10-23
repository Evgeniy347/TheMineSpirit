package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.theminespirit.core.rest.BoardTask;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BoardSnapshotTests {
    private static final String SPIRIT_UUID = "0f1e2d3c-0000-0000-0000-000000000001";

    private static BoardTask task(
            String id, int priority, String status, Map<String, Integer> missing, List<String> deps, long at) {
        return task(id, priority, status, missing, deps, at, null, false);
    }

    private static BoardTask task(
            String id,
            int priority,
            String status,
            Map<String, Integer> missing,
            List<String> deps,
            long at,
            String assignee,
            boolean done) {
        return new BoardTask(
                id,
                "BUILD_HOUSE",
                priority,
                assignee,
                status,
                Map.of(),
                missing,
                deps,
                at,
                1,
                "always",
                priority,
                List.of(),
                done);
    }

    @Test
    void build_MixedBoard_OrdersDoneActiveAvailableUnavailable() {
        List<BoardTask> tasks = List.of(
                task("lack", 90, "WAITING_RESOURCES", Map.of("oak_log", 3), List.of(), 1),
                task("free-low", 10, "PENDING", Map.of(), List.of(), 2),
                task("done-late", 50, "DONE", Map.of(), List.of(), 3, SPIRIT_UUID, true),
                task("work", 20, "ASSIGNED", Map.of(), List.of(), 4, SPIRIT_UUID, false),
                task("free-high", 70, "PENDING", Map.of(), List.of(), 5),
                task("done-early", 50, "DONE", Map.of(), List.of(), 6, SPIRIT_UUID, true));

        List<BoardTile> tiles =
                BoardSnapshot.build(tasks, Map.of("done-late", 900L, "done-early", 100L), uuid -> "Тихон");

        assertEquals(
                List.of("done-early", "done-late", "work", "free-high", "free-low", "lack"),
                tiles.stream().map(BoardTile::id).toList(),
                "Порядок плиток: завершённые по времени, активные, доступные и недоступные по приоритету");
        assertEquals(100L, tiles.get(0).doneAt(), "Завершённая плитка обязана нести тик завершения из журнала");
        assertEquals("Тихон", tiles.get(2).spirit(), "Плитка обязана называть духа-исполнителя по имени");
        assertNull(tiles.get(3).spirit(), "Невыданная плитка не называет исполнителя");
    }

    @Test
    void build_UnknownSpirit_ShowsShortUuid() {
        List<BoardTile> tiles = BoardSnapshot.build(
                List.of(task("work", 20, "ASSIGNED", Map.of(), List.of(), 1, SPIRIT_UUID, false)),
                Map.of(),
                uuid -> null);

        assertEquals("0f1e2d3c", tiles.get(0).spirit(), "Дух вне загруженных чанков показывается началом UUID");
    }

    @Test
    void groupOf_PendingWithUnfinishedDependency_IsUnavailable() {
        BoardTask dependant = task("after", 50, "PENDING", Map.of(), List.of("before"), 1);

        assertEquals(
                BoardTileGroup.UNAVAILABLE,
                BoardSnapshot.groupOf(dependant, Set.of("before", "after")),
                "Задание, ждущее незавершённую зависимость, не может быть доступным");
        assertEquals(
                BoardTileGroup.AVAILABLE,
                BoardSnapshot.groupOf(dependant, Set.of("after")),
                "После завершения зависимости задание без нехватки доступно");
    }

    @Test
    void groupOf_FailedOrBlocked_IsUnavailable() {
        assertEquals(
                BoardTileGroup.UNAVAILABLE,
                BoardSnapshot.groupOf(task("x", 1, "BLOCKED", Map.of(), List.of(), 1), Set.of()),
                "Заблокированное задание показывается среди недоступных");
        assertEquals(
                BoardTileGroup.UNAVAILABLE,
                BoardSnapshot.groupOf(task("y", 1, "FAILED", Map.of(), List.of(), 1), Set.of()),
                "Проваленное задание показывается среди недоступных");
    }

    @Test
    void build_DoneWithoutJournal_UsesCreationTime() {
        List<BoardTile> tiles = BoardSnapshot.build(
                List.of(task("done", 5, "DONE", Map.of(), List.of(), 42, null, true)), Map.of(), uuid -> null);

        assertEquals(42L, tiles.get(0).doneAt(), "Без записи журнала время завершения — время постановки");
    }
}
