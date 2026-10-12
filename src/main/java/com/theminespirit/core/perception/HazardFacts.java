package com.theminespirit.core.perception;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record HazardFacts(List<HazardRef> hazards) {
    private static final Comparator<HazardRef> BY_PROXIMITY = Comparator.comparingDouble(HazardRef::distance)
            .thenComparing(HazardRef::pos)
            .thenComparing(HazardRef::type);

    public HazardFacts {
        Objects.requireNonNull(hazards, "hazards не может быть null: отсутствие опасностей — пустой список");
        hazards = List.copyOf(hazards);
    }

    public boolean hasLava() {
        return has(HazardType.LAVA);
    }

    public boolean hasWater() {
        return has(HazardType.WATER);
    }

    public boolean hasDrop() {
        return has(HazardType.DROP);
    }

    public HazardRef nearest() {
        return hazards.stream().min(BY_PROXIMITY).orElse(null);
    }

    public int count() {
        return hazards.size();
    }

    private boolean has(HazardType type) {
        return hazards.stream().anyMatch(hazard -> hazard.type() == type);
    }
}
