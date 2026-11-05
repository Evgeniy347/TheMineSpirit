package com.theminespirit.core.perception;

import java.util.Objects;

public record MemoryOutcome(MemoryStore store, Finding finding) {
    public MemoryOutcome {
        Objects.requireNonNull(store, "store не может быть null: после действия память духа всегда существует");
        Objects.requireNonNull(finding, "finding не может быть null: действие с памятью всегда отвечает находкой");
    }
}
