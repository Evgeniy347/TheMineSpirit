package com.theminespirit.core.perception;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record Finding(String script, List<Sighting> sightings) {
    public Finding {
        Objects.requireNonNull(script, "script не может быть null: находка помнит, какой скрипт её дал");
        if (script.isBlank()) {
            throw new IllegalArgumentException("Поле script пусто: находка обязана назвать скрипт, который её дал");
        }
        Objects.requireNonNull(sightings, "sightings не может быть null: «не найдено» — пустой список");
        for (Sighting sighting : sightings) {
            Objects.requireNonNull(sighting, "Список sightings содержит null: находка обязана существовать");
        }
        sightings = List.copyOf(sightings);
    }

    public static Finding located(LocateType type, String label, BlockCoord pos, BlockCoord origin) {
        Objects.requireNonNull(type, "type не может быть null: находка называет вид поиска");
        Objects.requireNonNull(origin, "origin не может быть null: расстояние считается от точки духа");
        double dx = pos.x() - origin.x();
        double dy = pos.y() - origin.y();
        double dz = pos.z() - origin.z();
        String distance = String.format(Locale.ROOT, "расстояние %.1f", Math.sqrt(dx * dx + dy * dy + dz * dz));
        return new Finding(type.scriptId(), List.of(new Sighting(type.sightingKind(), label, pos, distance)));
    }

    public static Finding notFound(LocateType type) {
        Objects.requireNonNull(type, "type не может быть null: находка называет вид поиска");
        return new Finding(type.scriptId(), List.of());
    }

    public boolean isEmpty() {
        return sightings.isEmpty();
    }
}
