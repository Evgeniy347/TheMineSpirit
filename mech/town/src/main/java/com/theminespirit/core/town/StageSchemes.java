package com.theminespirit.core.town;

import java.util.ArrayList;
import java.util.List;

public final class StageSchemes {
    public static final List<String> FENCE = List.of(
            "fence_long",
            "fence_straight",
            "fence_corner",
            "fence_diagonal",
            "fence_corner45_left",
            "fence_corner45_right",
            "fence_wicket",
            "fence_post");

    public static final List<String> WALL =
            List.of("wall_3", "wall_3_straight", "wall_3_post", "wall_3_corner", "gate_3");

    private StageSchemes() {}

    public static List<String> fence(int stage) {
        return stage >= 2 ? WALL : FENCE;
    }

    public static List<String> fenceAtomic(int stage) {
        if (stage >= 2) {
            return WALL;
        }
        List<String> atomic = new ArrayList<>(FENCE);
        atomic.add("fence_gate");
        return List.copyOf(atomic);
    }
}
