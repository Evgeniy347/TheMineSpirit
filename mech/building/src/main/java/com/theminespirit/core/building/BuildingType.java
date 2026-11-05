package com.theminespirit.core.building;

import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public enum BuildingType {
    HOUSING("жильё"),
    STORAGE("склад"),
    FOOD("еда"),
    FARM("ферма"),
    DEFENSE("оборона"),
    FORTIFIED("укреплённое"),
    CRAFT("ремесло"),
    INFRASTRUCTURE("инфраструктура"),
    SOCIAL("социальное"),
    PET("питомцы"),
    UNDERGROUND("подземное");

    private final String label;

    BuildingType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Optional<BuildingType> byId(@Nullable String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (BuildingType type : values()) {
            if (type.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
