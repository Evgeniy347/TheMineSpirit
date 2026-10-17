package com.theminespirit.core.observation;

import java.util.Objects;

public record BoardTaskRef(String caseName, String status) {
    public BoardTaskRef {
        Objects.requireNonNull(caseName, "caseName не может быть null: задание доски называет свой кейс");
        Objects.requireNonNull(status, "status не может быть null: задание доски несёт статус");
    }
}
