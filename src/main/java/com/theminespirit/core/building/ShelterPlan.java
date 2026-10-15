package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class ShelterPlan {
    public static final int SHELTER_SIDE = 4;

    private ShelterPlan() {
    }

    public static List<BlockCoord> shelter(BlockCoord origin, int wallHeight, int budget) {
        Objects.requireNonNull(origin, "Укрытие без угла не строится: без привязки неизвестно, где ставить стены");
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
            for (int dx = 0; dx < SHELTER_SIDE; dx++) {
                for (int dz = 0; dz < SHELTER_SIDE; dz++) {
                    if (dx == 0 || dx == SHELTER_SIDE - 1 || dz == 0 || dz == SHELTER_SIDE - 1) {
                        cells.add(new BlockCoord(origin.x() + dx, origin.y() + level, origin.z() + dz));
                    }
                }
            }
        }
        cells.sort(Comparator.comparingInt(BlockCoord::y).thenComparingInt(BlockCoord::x).thenComparingInt(BlockCoord::z));
        for (int dx = 0; dx < SHELTER_SIDE; dx++) {
            for (int dz = 0; dz < SHELTER_SIDE; dz++) {
                cells.add(new BlockCoord(origin.x() + dx, origin.y() + wallHeight, origin.z() + dz));
            }
        }
        return List.copyOf(cells.subList(0, Math.min(cells.size(), budget)));
    }
}
