package com.theminespirit.core.perception;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record HazardFacts(List<HazardRef> hazards) {
    public static final int MAX_REPORTED = 64;
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

    public HazardFacts closest(int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("Предел " + limit + " отрицателен: сколько ближайших опасностей "
                    + "оставить, задаётся числом от нуля");
        }
        return new HazardFacts(
                hazards.stream().sorted(BY_PROXIMITY).limit(limit).toList());
    }

    private boolean has(HazardType type) {
        return hazards.stream().anyMatch(hazard -> hazard.type() == type);
    }
}
