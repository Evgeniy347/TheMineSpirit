package com.theminespirit.core.town;

import com.theminespirit.core.building.BuildRotation;
import java.util.Objects;

public record StreetTile(String scheme, BuildRotation rotation, int x, int z) {
    public StreetTile {
        Objects.requireNonNull(scheme, "Схема плитки улицы не может быть null");
        Objects.requireNonNull(rotation, "Поворот плитки улицы не может быть null");
    }
}
