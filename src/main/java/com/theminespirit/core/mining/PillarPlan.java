package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;

public final class PillarPlan {
    private PillarPlan() {
    }

    public static List<BlockCoord> of(int height) { return plan(height); }
    public static List<BlockCoord> plan(int height) {
        if (height < 1) throw new IllegalArgumentException("Высота столба " + height + " меньше 1: строить нечего");
        List<BlockCoord> cells = new ArrayList<>(height);
        for (int step = 0; step < height; step++) cells.add(new BlockCoord(0, step, 0));
        return List.copyOf(cells);
    }
    public static List<BlockCoord> steps(int height) { return plan(height); }
    public static List<BlockCoord> cells(int height) { return plan(height); }
    public static List<BlockCoord> blocks(int height) { return plan(height); }
}
