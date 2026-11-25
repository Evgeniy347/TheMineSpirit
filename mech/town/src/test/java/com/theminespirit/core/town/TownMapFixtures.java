package com.theminespirit.core.town;

import java.util.List;

final class TownMapFixtures {
    static final ColumnMask REGION = ColumnMask.rect(0, 0, 199, 199);
    static final ColumnMask NO_WATER = ColumnMask.rect(-10, -10, -10, -10);

    private TownMapFixtures() {}

    static TownMap small() {
        return new TownMap(
                new TownMap.MapPoint(100, 100),
                List.of(
                        new TownMap.MapPlot("family_house_a", "family_house_a", 10, 10, 41, 41, "east"),
                        new TownMap.MapPlot("hut", "hut", 10, 60, 25, 75, "north")),
                List.of(new TownMap.MapStreet("main_ns", TownMapRules.MAIN, 95, 0, 101, 199)),
                List.of(
                        new TownMap.WallSegment(0, 0, 199, 0),
                        new TownMap.WallSegment(199, 0, 199, 199),
                        new TownMap.WallSegment(199, 199, 0, 199),
                        new TownMap.WallSegment(0, 199, 0, 0)));
    }
}
