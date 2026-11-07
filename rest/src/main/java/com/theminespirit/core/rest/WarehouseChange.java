package com.theminespirit.core.rest;

import com.theminespirit.core.board.WarehouseOrder;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record WarehouseChange(@Nullable WarehouseOrder previous, WarehouseOrder order, boolean changed) {
    public WarehouseChange {
        Objects.requireNonNull(order, "Новое решение о складе не может быть null: ответ обязан назвать, что решено");
    }
}
