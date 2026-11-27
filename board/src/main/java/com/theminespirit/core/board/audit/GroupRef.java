package com.theminespirit.core.board.audit;

import java.util.Objects;

public record GroupRef(String id, int revision) {
    public GroupRef {
        Objects.requireNonNull(id, "Id группы не может быть null: билет обязан ссылаться на конкретную группу");
        if (id.isBlank() || revision < 0) {
            throw new IllegalArgumentException("Ссылка на группу '" + id + "@" + revision
                    + "' неполна: пустой id или отрицательная ревизия не укажут снимок работы");
        }
    }

    public String text() {
        return id + "@" + revision;
    }
}
