package com.theminespirit.core.movement;

public final class StairJump {
    private StairJump() {}

    public static boolean needed(int rise, boolean onGround, boolean horizontalCollision) {
        return rise > 0 && onGround && horizontalCollision;
    }
}
