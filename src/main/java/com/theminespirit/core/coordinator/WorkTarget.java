package com.theminespirit.core.coordinator;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public record WorkTarget(WorkKind kind, BlockCoord anchor) {
    public WorkTarget {
        Objects.requireNonNull(kind, "kind не может быть null: вид цели входит в ключ брони");
        Objects.requireNonNull(anchor,
                "anchor не может быть null: цель без якоря не опознает клетку, ствол или жилу");
    }
}
