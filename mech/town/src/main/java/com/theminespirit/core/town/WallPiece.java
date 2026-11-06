package com.theminespirit.core.town;

import com.theminespirit.core.building.BuildRotation;
import java.util.Objects;

public record WallPiece(String scheme, BuildRotation rotation, int x, int z, int from, int length) {
    public WallPiece {
        Objects.requireNonNull(scheme, "Схема фрагмента не может быть null");
        Objects.requireNonNull(rotation, "Поворот фрагмента не может быть null");
        if (length < 1) {
            throw new IllegalArgumentException(
                    "Фрагмент '" + scheme + "' закрывает " + length + " клеток: нужна хотя бы одна");
        }
    }
}
