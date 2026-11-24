package com.theminespirit.core.town;

import java.util.List;

public final class StageSchemes {
    public static final List<String> WALL =
            List.of("wall_3", "wall_3_straight", "wall_3_post", "wall_3_corner", "gate_3");

    private StageSchemes() {}

    public static List<String> wall() {
        return WALL;
    }

    public static String wallStub(int stage) {
        return WALL_POST;
    }

    private static final String WALL_POST = "wall_3_post";
}
