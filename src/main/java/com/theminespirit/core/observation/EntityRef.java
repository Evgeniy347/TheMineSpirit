package com.theminespirit.core.observation;

import java.util.Objects;

public record EntityRef(String type, BlockPos pos, double distance) {
    public EntityRef {
        Objects.requireNonNull(type, "type не может быть null: ссылка на сущность называет её тип");
        Objects.requireNonNull(pos, "pos не может быть null: ссылка на сущность называет её позицию");
    }
}
