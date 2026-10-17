package com.theminespirit.core.policy;

import com.theminespirit.core.observation.BlockBox;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;

public final class ProtectedZones {
    public static final int MAX_ZONES = 64;

    private final List<ProtectedZone> zones;

    private ProtectedZones(List<ProtectedZone> zones) {
        this.zones = zones;
    }

    public record ProtectedZone(String dimension, BlockBox box) {
        public ProtectedZone {
            Objects.requireNonNull(dimension, "Измерение защищённой зоны не может быть null: зона принадлежит одному миру");
            Objects.requireNonNull(box, "Коробка защищённой зоны не может быть null: без неё зона ничего не защищает");
            if (dimension.isBlank()) {
                throw new IllegalArgumentException("Измерение защищённой зоны пусто: укажите, например, minecraft:overworld");
            }
        }
    }

    public static ProtectedZones of(List<ProtectedZone> zones) {
        Objects.requireNonNull(zones, "Список защищённых зон не может быть null: пустой список задаётся List.of()");
        if (zones.size() > MAX_ZONES) {
            throw new IllegalArgumentException("Защищённых зон " + zones.size() + ", допустимо не больше " + MAX_ZONES
                    + ": проверка идёт по списку на каждую ломку, объедините соседние коробки");
        }
        return new ProtectedZones(List.copyOf(zones));
    }

    public boolean contains(String dimension, BlockCoord pos) {
        Objects.requireNonNull(dimension, "Измерение не может быть null: зоны привязаны к миру");
        Objects.requireNonNull(pos, "Позиция не может быть null: проверяется, лежит ли она в защищённой зоне");
        for (ProtectedZone zone : zones) {
            if (zone.dimension().equals(dimension) && zone.box().contains(pos)) {
                return true;
            }
        }
        return false;
    }

    public boolean intersects(String dimension, BlockBox box) {
        Objects.requireNonNull(dimension, "Измерение не может быть null: зоны привязаны к миру");
        Objects.requireNonNull(box, "Куб не может быть null: проверяется пересечение с зонами");
        for (ProtectedZone zone : zones) {
            if (zone.dimension().equals(dimension) && zone.box().intersects(box)) {
                return true;
            }
        }
        return false;
    }

    public int size() {
        return zones.size();
    }
}
