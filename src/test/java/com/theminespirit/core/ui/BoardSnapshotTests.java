package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
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
        return timed(id, priority, status, missing, deps, at, assignee, done, 0L, 0L);
    }

    private static BoardTask timed(
            String id,
            int priority,
            String status,
            Map<String, Integer> missing,
            List<String> deps,
            long at,
            String assignee,
            boolean done,
            long startedAt,
            long finishedAt) {
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
                done,
                startedAt,
                finishedAt,
                finishedAt > startedAt ? finishedAt - startedAt : 0L);
    }

    @Test
    void build_MixedBoard_KeepsWaitingApartFromDone() {
        List<BoardTask> tasks = List.of(
                task("lack", 90, "WAITING_RESOURCES", Map.of("oak_log", 3), List.of(), 1),
                task("free-low", 10, "PENDING", Map.of(), List.of(), 2),
                timed("done-late", 50, "DONE", Map.of(), List.of(), 3, SPIRIT_UUID, true, 500L, 900L),
                task("work", 20, "ASSIGNED", Map.of(), List.of(), 4, SPIRIT_UUID, false),
                task("free-high", 70, "PENDING", Map.of(), List.of(), 5),
                timed("done-early", 50, "DONE", Map.of(), List.of(), 6, SPIRIT_UUID, true, 0L, 100L));

        List<BoardTile> tiles = BoardSnapshot.build(tasks, uuid -> "Тихон");

        assertEquals(
                List.of("done-early", "done-late", "work", "lack", "free-high", "free-low"),
                tiles.stream().map(BoardTile::id).toList(),
                "Порядок плиток: успешно завершённые по времени, активные, ждущие с причиной, доступные");
        assertEquals(100L, tiles.get(0).doneAt(), "Завершённая плитка обязана нести тик завершения из журнала");
        assertEquals(BoardTileGroup.ACTIVE, tiles.get(2).group(), "Выданное задание обязано стоять в активных");
        assertEquals(
                BoardTileGroup.WAITING,
                tiles.get(3).group(),
                "Задание без ресурсов обязано стоять в колонке ожидания, а не среди завершённых");
        assertFalse(tiles.get(3).unfinished(), "Ждущее задание не считается не удавшимся в колонке завершённых");
        assertEquals(0L, tiles.get(3).doneAt(), "Ждущее задание не имеет тика завершения");
        assertEquals("Тихон", tiles.get(2).spirit(), "Плитка обязана называть духа-исполнителя по имени");
        assertNull(tiles.get(4).spirit(), "Невыданная плитка не называет исполнителя");
    }

    @Test
    void build_TicketDetails_ReachTheTile() {
        BoardTask task = new BoardTask(
                "activity:1",
                "ACTIVITY_CHOP",
                15,
                SPIRIT_UUID,
                "ASSIGNED",
                Map.of(),
                Map.of(),
                List.of(),
                1,
                1,
                "always",
                15,
                List.of("script=chop_tree", "target=-203,68,-560"),
                false);

        BoardTile tile = BoardSnapshot.build(List.of(task), uuid -> "Тихон").get(0);

        assertEquals(
                List.of("script=chop_tree", "target=-203,68,-560"),
                tile.details(),
                "Скрипт и цель обязаны дойти до плитки: иначе на экране доски карточка не говорит, "
                        + "чем дух занят и куда идёт");
    }

    @Test
    void build_NonTransferTicket_ShowsTicketTargetAndSpecifics() {
        TicketTarget target = TicketTarget.at(new BlockCoord(10, 64, -5)).withSupply("minecraft:oak_log", 5);
        BoardTask task = task("supply", 50, "ASSIGNED", Map.of(), List.of(), 1, SPIRIT_UUID, false);

        BoardTile tile = BoardSnapshot.build(List.of(task), uuid -> "Тихон", Map.of("supply", target.details()))
                .get(0);

        assertEquals(
                List.of("target=10,64,-5", "supply=minecraft:oak_log×5"),
                tile.details(),
                "Цель задания обязана дойти до плитки любого кейса, а не только передачи: "
                        + "иначе по доске не видно, куда идёт дух");
    }

    @Test
    void build_TicketDetails_KeepExistingTargetWithoutRepeating() {
        BoardTask task = new BoardTask(
                "activity:1",
                "ACTIVITY_CHOP",
                15,
                SPIRIT_UUID,
                "ASSIGNED",
                Map.of(),
                Map.of(),
                List.of(),
                1,
                1,
                "always",
                15,
                List.of("script=chop_tree", "target=-203,68,-560"),
                false);
        TicketTarget target = TicketTarget.at(new BlockCoord(1, 2, 3)).withScheme("fence");

        BoardTile tile = BoardSnapshot.build(List.of(task), uuid -> "Тихон", Map.of("activity:1", target.details()))
                .get(0);

        assertEquals(
                List.of("script=chop_tree", "target=-203,68,-560", "scheme=fence"),
                tile.details(),
                "Цель задания не повторяет уже показанную цель, но добавляет недостающую подробность");
    }

    @Test
    void build_UnknownSpirit_ShowsShortUuid() {
        List<BoardTile> tiles = BoardSnapshot.build(
                List.of(task("work", 20, "ASSIGNED", Map.of(), List.of(), 1, SPIRIT_UUID, false)), uuid -> null);

        assertEquals("0f1e2d3c", tiles.get(0).spirit(), "Дух вне загруженных чанков показывается началом UUID");
    }

    @Test
    void groupOf_PendingWithUnfinishedDependency_GoesToWaiting() {
        BoardTask dependant = task("after", 50, "PENDING", Map.of(), List.of("before"), 1);

        assertEquals(
                BoardTileGroup.WAITING,
                BoardSnapshot.groupOf(dependant, Set.of("before", "after")),
                "Задание, ждущее незавершённую зависимость, стоит в колонке ожидания с причиной");
        assertEquals(
                BoardTileGroup.AVAILABLE,
                BoardSnapshot.groupOf(dependant, Set.of("after")),
                "После завершения зависимости задание без нехватки доступно");
    }

    @Test
    void groupOf_BlockedAndFailed_GoToWaiting() {
        assertEquals(
                BoardTileGroup.WAITING,
                BoardSnapshot.groupOf(task("x", 1, "BLOCKED", Map.of(), List.of(), 1), Set.of()),
                "Заблокированное задание стоит в колонке ожидания, а не среди завершённых");
        assertEquals(
                BoardTileGroup.WAITING,
                BoardSnapshot.groupOf(task("y", 1, "FAILED", Map.of(), List.of(), 1), Set.of()),
                "Проваленное задание ждёт повтора в колонке ожидания, а не среди завершённых");
    }

    @Test
    void blockedTask_InWaitingColumn_IsNotCompleted() {
        List<BoardTile> tiles =
                BoardSnapshot.build(List.of(task("blocked", 1, "BLOCKED", Map.of(), List.of(), 1)), uuid -> null);

        assertEquals(
                BoardTileGroup.WAITING,
                tiles.get(0).group(),
                "Заблокированное задание обязано стоять в колонке ожидания, а не в завершённых");
        assertFalse(tiles.get(0).completed(), "Заблокированное задание нельзя считать успешно завершённым");
    }

    @Test
    void build_DependentTask_KeepsDependencyIds() {
        List<BoardTile> tiles =
                BoardSnapshot.build(List.of(task("after", 5, "PENDING", Map.of(), List.of("before"), 1)), uuid -> null);

        assertEquals(
                List.of("before"),
                tiles.get(0).dependsOn(),
                "Плитка обязана нести id зависимостей: иначе экран не назовёт задание, которого она ждёт");
    }

    @Test
    void build_DoneWithoutJournal_UsesCreationTime() {
        List<BoardTile> tiles = BoardSnapshot.build(
                List.of(task("done", 5, "DONE", Map.of(), List.of(), 42, null, true)), uuid -> null);

        assertEquals(42L, tiles.get(0).doneAt(), "Без записи журнала время завершения — время постановки");
    }

    @Test
    void build_TimedTask_KeepsStartFinishAndSpentTicks() {
        List<BoardTile> tiles = BoardSnapshot.build(
                List.of(timed("work", 5, "DONE", Map.of(), List.of(), 1, SPIRIT_UUID, true, 500L, 900L)), uuid -> null);

        assertEquals(500L, tiles.get(0).startedAt(), "Плитка обязана нести тик выдачи из журнала");
        assertEquals(900L, tiles.get(0).finishedAt(), "Плитка обязана нести тик закрытия из журнала");
        assertEquals(400L, tiles.get(0).durationTicks(), "Плитка обязана нести затраченные тики работы");
    }
}
