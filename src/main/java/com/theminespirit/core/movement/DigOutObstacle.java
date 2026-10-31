package com.theminespirit.core.movement;

public final class DigOutObstacle {
    private DigOutObstacle() {}

    public static boolean obstacle(boolean passable, boolean water) {
        return !passable && !water;
    }
}
