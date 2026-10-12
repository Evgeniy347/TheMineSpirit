package com.theminespirit.core.perception;

import com.theminespirit.core.observation.ScanRadius;
import com.theminespirit.core.observation.WorldObservation;
import java.util.Objects;
import java.util.Optional;

public enum ScanFocus {
    ALL,
    BLOCKS,
    ENTITIES,
    CONTAINERS;

    public static ScanFocus of(PerceptionType type) {
        Objects.requireNonNull(type, "type не может быть null: по типу запроса выбирается, что скан собирает");
        return switch (type) {
            case BLOCKS -> BLOCKS;
            case ENTITIES -> ENTITIES;
            case HAZARDS -> throw new IllegalArgumentException("Запрос опасностей не сканируется срезами блоков: "
                    + "опасности ищет отдельный скрипт sense_hazard, а ScanFocus отвечает за scan_blocks и scan_entities");
        };
    }

    public boolean collectsBlocks() {
        return this == ALL || this == BLOCKS;
    }

    public boolean collectsEntities() {
        return this == ALL || this == ENTITIES;
    }

    public boolean collectsContainers() {
        return this == ALL || this == CONTAINERS;
    }

    public boolean needsSlices() {
        return collectsBlocks() || collectsContainers();
    }

    public boolean filtersEntities() {
        return this == ENTITIES;
    }

    public Optional<PerceptionResult> resultOf(WorldObservation observation, ScanRadius radius) {
        Objects.requireNonNull(observation, "observation не может быть null: результат строится из наблюдения");
        Objects.requireNonNull(radius, "radius не может быть null: результат помнит радиус запроса");
        return switch (this) {
            case BLOCKS -> Optional.of(PerceptionResult.blocks(radius, observation.blocks()));
            case ENTITIES -> Optional.of(PerceptionResult.entities(radius, observation.entities()));
            case ALL, CONTAINERS -> Optional.empty();
        };
    }
}
