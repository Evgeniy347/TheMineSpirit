package com.theminespirit.core.board;

import com.theminespirit.core.building.ScaffoldPlan;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;

final class HighClearFixture {
    static final ScaffoldPlan.Scaffold SCAFFOLD = new ScaffoldPlan.Scaffold(
            "scaffold:11,60,-5",
            new BlockCoord(11, 60, -5),
            19,
            List.of("high:10,-5"),
            List.of(new BlockCoord(10, 79, -5)));

    private HighClearFixture() {}
}
