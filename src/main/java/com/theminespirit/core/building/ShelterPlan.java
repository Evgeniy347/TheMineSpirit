package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class ShelterPlan {
    public static final int SHELTER_SIDE = 4;
    public static final int WALL_HEIGHT = 2;
    public static final int BLOCK_BUDGET = 64;

    private ShelterPlan() {
    }

    public static List<BlockCoord> shelter(BlockCoord origin, int wallHeight, int budget) {
        return shelter(origin, SHELTER_SIDE, wallHeight, budget);
    }

    public static List<BlockCoord> shelter(BlockCoord origin, int side, int wallHeight, int budget) {
        Objects.requireNonNull(origin, "Укрытие без угла не строится: без привязки неизвестно, где ставить стены");
        if (side < 3 || side > 4) {
            throw new IllegalArgumentException("Сторона укрытия " + side + ": укрытие бывает 3×3 или 4×4, "
                    + "меньше — дух не встанет внутри, больше — съедает бюджет и время стройки");
        }
        if (wallHeight < 1) {
            throw new IllegalArgumentException("Высота стен укрытия = " + wallHeight
                    + ": стена ниже блока не прикроет духа");
        }
        if (budget < 1) {
            throw new IllegalArgumentException("Бюджет укрытия = " + budget
                    + ": без блоков строителю нечего ставить");
        }
        List<BlockCoord> cells = new ArrayList<>();
        for (int level = 0; level < wallHeight; level++) {
            for (int dx = 0; dx < side; dx++) {
                for (int dz = 0; dz < side; dz++) {
                    if (dx == 0 || dx == side - 1 || dz == 0 || dz == side - 1) {
                        cells.add(new BlockCoord(origin.x() + dx, origin.y() + level, origin.z() + dz));
                    }
                }
            }
        }
        cells.sort(Comparator.comparingInt(BlockCoord::y).thenComparingInt(BlockCoord::x).thenComparingInt(BlockCoord::z));
        for (int dx = 0; dx < side; dx++) {
            for (int dz = 0; dz < side; dz++) {
                cells.add(new BlockCoord(origin.x() + dx, origin.y() + wallHeight, origin.z() + dz));
            }
        }
        return List.copyOf(cells.subList(0, Math.min(cells.size(), budget)));
    }
}
