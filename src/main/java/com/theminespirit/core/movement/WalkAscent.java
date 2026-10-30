package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;

public final class WalkAscent {
    public enum Lift {
        HOLD,
        JUMP,
        CLIMB
    }

    public enum Step {
        ALIGN,
        MOVE,
        JUMP,
        CLIMB,
        ARRIVED
    }

    public record Body(boolean onGround, boolean inWater, boolean onClimbable) {}

    public record Pose(BlockCoord feet, double x, double y, double z) {}

    public record Command(Step step, double x, double y, double z) {}

    public static final double ARRIVAL = 0.3;
    private static final double CENTER = 0.5;

    private WalkAscent() {}

    public static Lift lift(boolean rising, Body body) {
        if (!rising) {
            return Lift.HOLD;
        }
        if (body.inWater() || body.onClimbable()) {
            return Lift.CLIMB;
        }
        return Lift.JUMP;
    }

    public static Command next(BlockCoord cell, Pose pose, Body body) {
        double targetX = cell.x() + CENTER;
        double targetZ = cell.z() + CENTER;
        if (arrived(cell, pose, body)) {
            return new Command(Step.ARRIVED, targetX, cell.y(), targetZ);
        }
        boolean rising = cell.y() > pose.feet().y();
        double centerX = pose.feet().x() + CENTER;
        double centerZ = pose.feet().z() + CENTER;
        if (rising && body.onGround() && horizontal(pose, centerX, centerZ) > CellCentering.TOLERANCE) {
            return new Command(Step.ALIGN, centerX, pose.y(), centerZ);
        }
        Step step = switch (lift(rising, body)) {
            case HOLD -> Step.MOVE;
            case JUMP -> Step.JUMP;
            case CLIMB -> Step.CLIMB;
        };
        return new Command(step, targetX, cell.y(), targetZ);
    }

    public static double horizontal(Pose pose, double x, double z) {
        double dx = pose.x() - x;
        double dz = pose.z() - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static boolean arrived(BlockCoord cell, Pose pose, Body body) {
        return pose.feet().equals(cell)
                && horizontal(pose, cell.x() + CENTER, cell.z() + CENTER) < ARRIVAL
                && (body.onGround() || body.inWater() || body.onClimbable());
    }
}
