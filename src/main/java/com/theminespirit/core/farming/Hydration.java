package com.theminespirit.core.farming;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public final class Hydration {
    public static final int RADIUS = 4;
    public static final int MAX_LOOKUPS = 9 * 9 * 2;

    private final Set<BlockCoord> waterCells;

    private Hydration(Set<BlockCoord> waterCells) {
        this.waterCells = waterCells;
    }

    public static Hydration of(Set<BlockCoord> waterCells) {
        Objects.requireNonNull(waterCells, "Клетки воды не могут быть null: без них неизвестно, что увлажняет грядку");
        return new Hydration(Collections.unmodifiableSet(waterCells));
    }

    public boolean watered(BlockCoord ground) {
        Objects.requireNonNull(ground, "Клетка земли не может быть null: без неё неизвестно, где искать воду");
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    if (waterCells.contains(
                            new BlockCoord(ground.x() + dx, ground.y() + dy, ground.z() + dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
