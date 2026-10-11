package com.theminespirit.core.observation;

import java.util.Objects;

public record ContainerRef(String type, BlockCoord pos, String id) {
    public ContainerRef {
        Objects.requireNonNull(type, "type не может быть null: ссылка на контейнер называет его тип");
        Objects.requireNonNull(pos, "pos не может быть null: ссылка на контейнер называет её позицию");
    }

    public ContainerRef(String type, BlockCoord pos) {
        this(type, pos, null);
    }
}
