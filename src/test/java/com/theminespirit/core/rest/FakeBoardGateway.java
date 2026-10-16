package com.theminespirit.core.rest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Фальшивая доска для тестов REST-контроллера: хранит задания в памяти, считает вызовы. */
final class FakeBoardGateway implements BoardGateway {
    private static final String ID_PREFIX = "board-";

    private final Map<String, BoardTask> tasks = new LinkedHashMap<>();
    private final List<String> submits = new ArrayList<>();
    private final List<String> cancels = new ArrayList<>();

    List<String> submits() {
        return submits;
    }

    List<String> cancels() {
        return cancels;
    }

    int size() {
        return tasks.size();
    }

    FakeBoardGateway with(BoardTask task) {
        tasks.put(task.id(), task);
        return this;
    }

    @Override
    public List<BoardTask> tasks() {
        return List.copyOf(tasks.values());
    }

    @Override
    public BoardTask submit(BoardTaskRequest request) {
        String id = ID_PREFIX + request.requestId();
        submits.add(id);
        BoardTask task = new BoardTask(id, request.boardCase().name(), 50,
                request.assignee().map(UUID::toString).orElse(null), "PENDING", request.requiredItems(),
                Map.of(), request.dependsOn(), 0, request.level(), request.cause());
        tasks.put(id, task);
        return task;
    }

    @Override
    public BoardTask cancel(String ticketId) {
        BoardTask task = tasks.get(ticketId);
        if (task == null) {
            throw new ApiException(ErrorCode.NOT_FOUND,
                    "Задание доски '" + ticketId + "' не найдено: список заданий — GET /v1/board",
                    Map.of("id", ticketId));
        }
        cancels.add(ticketId);
        BoardTask cancelled = new BoardTask(task.id(), task.boardCase(), task.priority(), task.assignee(),
                "CANCELLED", task.requiredItems(), task.missing(), task.dependsOn(), task.createdAt(),
                task.level(), task.cause());
        tasks.put(ticketId, cancelled);
        return cancelled;
    }
}
