package com.theminespirit.core.town;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public final class YardFloor {
    public record Audit(List<BlockCoord> stations, List<BlockCoord> holes, int supported) {
        public Audit {
            stations = List.copyOf(stations);
            holes = List.copyOf(holes);
        }

        public boolean damaged() {
            return supported < stations.size();
        }
    }

    private YardFloor() {}

    public static boolean due(long now, long last, int every) {
        if (every < 1) {
            throw new IllegalArgumentException("Период проверки " + every + " не положителен: двор не проверить");
        }
        return now < last || now - last >= every;
    }

    public static Audit of(List<BlockCoord> stations, int maxDepth, Predicate<BlockCoord> solid) {
        Objects.requireNonNull(stations, "Станки двора не могут быть null: пустой список — пустой двор");
        Objects.requireNonNull(solid, "Проверка опоры не может быть null: без неё не видно дыру");
        if (maxDepth < 1) {
            throw new IllegalArgumentException("Глубина опоры " + maxDepth + " не положительна: дыру не найти");
        }
        List<BlockCoord> holes = new ArrayList<>();
        int supported = 0;
        for (BlockCoord station : stations) {
            boolean found = false;
            int before = holes.size();
            for (int depth = 1; depth <= maxDepth; depth++) {
                BlockCoord below = new BlockCoord(station.x(), station.y() - depth, station.z());
                if (solid.test(below)) {
                    found = true;
                    break;
                }
                holes.add(below);
            }
            if (found && holes.size() == before) {
                supported++;
            }
        }
        holes.sort(Comparator.comparingInt(BlockCoord::y)
                .thenComparingInt(BlockCoord::x)
                .thenComparingInt(BlockCoord::z));
        return new Audit(stations, holes, supported);
    }
}
