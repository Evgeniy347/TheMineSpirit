package com.theminespirit.core.observation;

import java.util.List;
import java.util.Objects;

public record ContainerContents(BlockCoord pos, List<ItemCount> items) {
    public ContainerContents {
        Objects.requireNonNull(pos, "pos не может быть null: содержимое привязано к контейнеру в мире");
        items = ItemCounts.aggregate(items, "items");
    }
}
