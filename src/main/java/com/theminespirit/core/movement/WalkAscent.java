package com.theminespirit.core.movement;

public final class WalkAscent {
    public enum Lift {
        HOLD,
        JUMP,
        CLIMB
    }

    public record Body(boolean onGround, boolean inWater, boolean onClimbable) {}

    private WalkAscent() {}

    public static Lift lift(boolean rising, Body body) {
        if (!rising) {
            return Lift.HOLD;
        }
        if (body.inWater() || body.onClimbable()) {
            return Lift.CLIMB;
        }
        return body.onGround() ? Lift.JUMP : Lift.HOLD;
    }
}
