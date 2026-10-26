package com.theminespirit.core.town;

import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;

public record SchemeBuild(String scheme, BlockCoord origin, BuildRotation rotation, List<String> only) {
    public SchemeBuild(String scheme, BlockCoord origin, BuildRotation rotation) {
        this(scheme, origin, rotation, List.of());
    }

    public SchemeBuild {
        Objects.requireNonNull(scheme, "Схема стройки не может быть null: постройка ставится только по схеме");
        Objects.requireNonNull(origin, "Начало стройки по схеме не может быть null");
        Objects.requireNonNull(rotation, "Поворот стройки по схеме не может быть null");
        only = List.copyOf(Objects.requireNonNull(only, "Отбор блоков схемы не может быть null: все — пустой список"));
    }
}
