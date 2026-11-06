package com.theminespirit.core.rest;

import java.util.Objects;

public record BotBoardTask(String id, String caseName, String status) {
    public BotBoardTask {
        Objects.requireNonNull(id, "id задания доски не может быть null: без него наблюдатель не отличит задания");
        Objects.requireNonNull(caseName, "Кейс задания доски не может быть null: по нему видно, чем занят дух");
        Objects.requireNonNull(status, "Статус задания доски не может быть null: по нему видно, выдано оно или ждёт");
    }
}
