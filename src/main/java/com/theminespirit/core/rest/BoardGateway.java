package com.theminespirit.core.rest;

import com.theminespirit.core.board.BoardLogEntry;
import java.util.List;

public interface BoardGateway {

    List<BoardTask> tasks();

    BoardTask submit(BoardTaskRequest request);

    BoardTask cancel(String ticketId);

    List<BoardLogEntry> log(int limit);
}
