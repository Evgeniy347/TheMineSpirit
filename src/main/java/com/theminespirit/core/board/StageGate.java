package com.theminespirit.core.board;

import java.util.Map;
import java.util.Objects;

public record StageGate(int stage, Map<String, Integer> missing) {
    public StageGate {
        if (stage < 1) {
            throw new IllegalArgumentException("Этап " + stage + ": этапы считаются с первого");
        }
        missing = Map.copyOf(
                Objects.requireNonNull(missing, "Остаток этапа не может быть null: пустой — карта, а не null"));
    }
}
