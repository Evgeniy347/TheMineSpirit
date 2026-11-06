package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.LiveBoardFixture;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.ui.BoardTileGroup;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BoardListingsTests {
    private static List<BoardTask> liveTasks() throws IOException {
        Board board = LiveBoardFixture.load("/live/observe11-late-board.json");
        List<BoardTask> tasks = new ArrayList<>();
        for (BoardTicket ticket : board.all()) {
            tasks.add(BoardTasks.of(board, ticket, new StockView(Map.of())));
        }
        return List.copyOf(tasks);
    }

    @Test
    void select_LiveBoardDefaultPortion_KeepsBoardBiggerThanPortion() throws IOException {
        List<BoardTask> tasks = liveTasks();

        BoardListing listing = BoardListings.select(tasks, BoardListQuery.parse(Map.of()));

        assertEquals(
                BoardListQuery.DEFAULT_LIMIT,
                listing.tasks().size(),
                "Живая доска больше порции: ответ обязан отдавать limit заданий, а не все " + tasks.size());
        assertEquals(tasks.size(), listing.total(), "total обязан равняться всей живой доске без фильтров");
        assertTrue(listing.nextCursor() != null, "У живой доски остаётся хвост: клиент обязан получить курсор");
        int byGroups =
                listing.groups().values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(byGroups, listing.total(), "Сумма счётчиков колонок обязана совпадать с total");
    }

    @Test
    void select_LiveBoardFailedStatus_ReturnsOrchardTask() throws IOException {
        List<BoardTask> tasks = liveTasks();

        BoardListing listing = BoardListings.select(tasks, BoardListQuery.parse(Map.of("status", "FAILED")));

        assertTrue(
                listing.tasks().stream().anyMatch(task -> "first:build_orchard".equals(task.id())),
                "На живой доске FAILED — это сад: фильтр статуса обязан его вернуть, иначе REST-клиент его не найдёт");
        assertEquals(
                BoardStatus.FAILED.name(),
                listing.tasks().get(0).status(),
                "Все задания в ответе обязаны иметь запрошенный статус");
    }

    @Test
    void select_LiveBoardGroupFilter_MatchesGroupTotals() throws IOException {
        List<BoardTask> tasks = liveTasks();
        Set<String> unfinished = new HashSet<>();
        for (BoardTask task : tasks) {
            if (!task.done()) {
                unfinished.add(task.id());
            }
        }

        BoardListing listing =
                BoardListings.select(tasks, BoardListQuery.parse(Map.of("group", "WAITING", "limit", "10")));

        assertTrue(
                listing.tasks().size() <= 10,
                "Порция колонки обязана уважать limit: " + listing.tasks().size());
        assertTrue(
                listing.tasks().stream()
                        .allMatch(task -> BoardListings.groupOf(task, unfinished) == BoardTileGroup.WAITING),
                "Фильтр group обязан отдавать только задания колонки WAITING");
        assertEquals(
                listing.groups().get(BoardTileGroup.WAITING),
                listing.total(),
                "total при фильтре колонки обязан совпадать со счётчиком этой колонки");
    }
}
