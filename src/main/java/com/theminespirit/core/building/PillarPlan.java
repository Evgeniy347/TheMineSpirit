package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PillarPlan {
    private PillarPlan() {
    }

    public static List<BlockCoord> plan(int height) {
        if (height < 1) {
            throw new IllegalArgumentException("Высота столба " + height + " меньше 1: строить нечего");
        }
        List<BlockCoord> cells = new ArrayList<>(height);
        for (int step = 0; step < height; step++) {
            cells.add(new BlockCoord(0, step, 0));
        }
        return List.copyOf(cells);
    }

    public static List<BlockCoord> dismantle(int height) {
        List<BlockCoord> topDown = new ArrayList<>(plan(height));
        Collections.reverse(topDown);
        return List.copyOf(topDown);
    }
}
