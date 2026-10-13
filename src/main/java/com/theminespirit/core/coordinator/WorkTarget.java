package com.theminespirit.core.coordinator;

import com.theminespirit.core.observation.BlockCoord;

public record WorkTarget(WorkKind kind, BlockCoord anchor) {
    public WorkTarget {
        if (kind == null) {
            throw new IllegalArgumentException("kind не может быть null: вид цели входит в ключ брони");
        }
        if (anchor == null) {
            throw new IllegalArgumentException(
                    "anchor не может быть null: цель без якоря не опознает клетку, ствол или жилу");
        }
    }
}
