package com.theminespirit.core.movement.api;

public final class GazeAhead {
    private GazeAhead() {}

    public static boolean steer(boolean pathActive, boolean working) {
        return pathActive && !working;
    }
}
