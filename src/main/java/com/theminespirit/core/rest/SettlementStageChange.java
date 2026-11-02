package com.theminespirit.core.rest;

import java.util.List;
import java.util.Objects;

public record SettlementStageChange(
        int previous, int stage, boolean changed, List<String> forgotten, List<String> cancelled) {
    public SettlementStageChange {
        if (previous < 1 || stage < 1) {
            throw new IllegalArgumentException(
                    "Откат " + previous + "→" + stage + ": этапы поселения начинаются с первого");
        }
        forgotten = List.copyOf(Objects.requireNonNull(
                forgotten, "Снятые отметки не могут быть null: пустой откат задают пустым списком"));
        cancelled = List.copyOf(Objects.requireNonNull(
                cancelled, "Снятые задания не могут быть null: пустой откат задают пустым списком"));
    }
}
