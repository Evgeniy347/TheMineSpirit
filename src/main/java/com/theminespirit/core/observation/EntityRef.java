package com.theminespirit.core.observation;

import java.util.Objects;

public record EntityRef(String type, BlockCoord pos, String id, double distance) {
    public EntityRef {
        Objects.requireNonNull(type, "type не может быть null: ссылка на сущность называет её тип");
        Objects.requireNonNull(pos, "pos не может быть null: ссылка на сущность называет её позицию");
    }

    public EntityRef(String type, BlockCoord pos, String id) {
        this(type, pos, id, 0.0);
    }

    public EntityRef(String type, BlockCoord pos, double distance) {
        this(type, pos, null, distance);
    }

    public EntityRef(String type, BlockCoord pos) {
        this(type, pos, null, 0.0);
    }

    @Override
    public double distance() {
        return distance;
    }
}
