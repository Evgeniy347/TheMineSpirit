package com.theminespirit.core.building;

import java.util.Locale;
import java.util.Optional;

/**
 * Тип строения поселения (жильё, склад, ферма, оборона, инфраструктура…). Идентификатор в json — строчными буквами
 * ({@code housing}, {@code storage}, ...): по типу библиотека пресетов ищет готовые постройки для поселения.
 */
public enum BuildingType {
    HOUSING("жильё"),
    STORAGE("склад"),
    FOOD("еда"),
    FARM("ферма"),
    DEFENSE("оборона"),
    CRAFT("ремесло"),
    INFRASTRUCTURE("инфраструктура"),
    SOCIAL("социальное"),
    PET("питомцы");

    private final String label;

    BuildingType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Optional<BuildingType> byId(String id) {
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