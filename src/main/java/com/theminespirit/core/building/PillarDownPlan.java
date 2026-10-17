package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;

public final class PillarDownPlan {
    private PillarDownPlan() {
    }

    public static List<BlockCoord> of(int height) { return PillarPlan.of(height); }
    public static List<BlockCoord> plan(int height) { return PillarPlan.plan(height); }
    public static List<BlockCoord> steps(int height) { return PillarPlan.steps(height); }
    public static List<BlockCoord> cells(int height) { return PillarPlan.cells(height); }
    public static List<BlockCoord> blocks(int height) { return PillarPlan.blocks(height); }
}
