package com.theminespirit.core.movement;

import com.theminespirit.core.movement.api.CellCentering;
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
        SWIM,
        DIVE,
        ARRIVED,
        IMPOSSIBLE,
        DESCENT_BLOCKED,
        ASCENT_BLOCKED
    }

    public record Body(
            boolean onGround,
            boolean inWater,
            boolean onClimbable,
            boolean waterTarget,
            boolean descentBlocked,
            boolean headBlocked) {
        public Body(boolean onGround, boolean inWater, boolean onClimbable, boolean waterTarget) {
            this(onGround, inWater, onClimbable, waterTarget, false, false);
        }

        public Body(
                boolean onGround, boolean inWater, boolean onClimbable, boolean waterTarget, boolean descentBlocked) {
            this(onGround, inWater, onClimbable, waterTarget, descentBlocked, false);
        }

        public Body(boolean onGround, boolean inWater, boolean onClimbable) {
            this(onGround, inWater, onClimbable, false, false, false);
        }
    }

    public record Pose(BlockCoord feet, double x, double y, double z) {}

    public record Command(Step step, double x, double y, double z) {}

    public static final double ARRIVAL = 0.3;
    private static final double CENTER = 0.5;
    private static final double MAX_JUMP_REACH = 1.6;

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
        if (body.waterTarget() || (body.inWater() && !body.onGround() && !body.onClimbable())) {
            Step swim = cell.y() < pose.feet().y() ? Step.DIVE : Step.SWIM;
            return new Command(swim, targetX, cell.y(), targetZ);
        }
        if (!rising && pose.feet().y() - cell.y() > 1) {
            Step descent = horizontal(pose, targetX, targetZ) > MAX_JUMP_REACH
                    ? Step.IMPOSSIBLE
                    : body.descentBlocked() ? Step.DESCENT_BLOCKED : Step.DIVE;
            return new Command(descent, targetX, cell.y(), targetZ);
        }
        Step step = switch (lift(rising, body)) {
            case HOLD -> horizontal(pose, targetX, targetZ) > MAX_JUMP_REACH ? Step.IMPOSSIBLE : Step.MOVE;
            case JUMP ->
                cell.y() - pose.feet().y() > 1 || horizontal(pose, targetX, targetZ) > MAX_JUMP_REACH
                        ? Step.IMPOSSIBLE
                        : body.headBlocked() ? Step.ASCENT_BLOCKED : Step.JUMP;
            case CLIMB -> Step.CLIMB;
        };
        return new Command(step, targetX, cell.y(), targetZ);
    }

    public static double horizontal(Pose pose, double x, double z) {
        double dx = pose.x() - x;
        double dz = pose.z() - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static boolean needsFloor(Step step, boolean water, boolean floorBelow) {
        return step == Step.MOVE && !water && !floorBelow;
    }

    public static boolean safeWithoutFloor(boolean sameCell, boolean onGround) {
        return sameCell && onGround;
    }

    private static boolean arrived(BlockCoord cell, Pose pose, Body body) {
        return pose.feet().equals(cell)
                && horizontal(pose, cell.x() + CENTER, cell.z() + CENTER) < ARRIVAL
                && (body.onGround() || body.inWater() || body.onClimbable());
    }
}
