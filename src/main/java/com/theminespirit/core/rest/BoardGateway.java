package com.theminespirit.core.rest;

import com.theminespirit.core.board.BoardLogEntry;
import java.util.List;
import java.util.Optional;

public interface BoardGateway {

    List<BoardTask> tasks();

    default BoardCounts counts() {
        return new BoardCounts(0, 0);
    }

    default Optional<BoardTask> task(String ticketId) {
        return tasks().stream().filter(task -> task.id().equals(ticketId)).findFirst();
    }

    BoardTask submit(BoardTaskRequest request);

    BoardTask cancel(String ticketId);

    List<BoardLogEntry> log(int limit);
}
