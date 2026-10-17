package com.theminespirit.core.base;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.perception.MemoryPoint;
import com.theminespirit.core.perception.MemoryType;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record SpiritHome(Optional<BlockCoord> point) {
    public SpiritHome {
        Objects.requireNonNull(point, "Дом духа без контейнера точки не считается: нет дома — Optional.empty()");
    }

    public static SpiritHome none() {
        return new SpiritHome(Optional.empty());
    }

    public static SpiritHome at(BlockCoord point) {
        return new SpiritHome(Optional.of(Objects.requireNonNull(point,
                "Дом духа без координаты не ставится: без точки дом некуда вести")));
    }

    public boolean isSet() {
        return point.isPresent();
    }

    public static SpiritHome resolve(Optional<SpiritHome> stored, List<MemoryPoint> memories, String dimension,
            BlockCoord self) {
        Objects.requireNonNull(stored, "Сохранённый дом не может быть null: нет дома — Optional.empty()");
        Objects.requireNonNull(memories, "Память духа не может быть null: легаси-точки переносятся из неё");
        Objects.requireNonNull(dimension, "Измерение не может быть null: дом ищется только в текущем мире");
        Objects.requireNonNull(self, "Позиция духа не может быть null: ближайшая легаси-база меряется от неё");
        if (stored.isPresent()) {
            return stored.get();
        }
        return memories.stream()
                .filter(memory -> memory.type() == MemoryType.BASE)
                .filter(memory -> memory.dimension().equals(dimension))
                .min(Comparator.comparingDouble((MemoryPoint memory) -> squaredDistance(self, memory.pos()))
                        .thenComparing(MemoryPoint::name))
                .map(memory -> SpiritHome.at(memory.pos()))
                .orElseGet(SpiritHome::none);
    }

    private static double squaredDistance(BlockCoord first, BlockCoord second) {
        double dx = first.x() - second.x();
        double dy = first.y() - second.y();
        double dz = first.z() - second.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
