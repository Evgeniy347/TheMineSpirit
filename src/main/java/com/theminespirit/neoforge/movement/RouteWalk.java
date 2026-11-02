package com.theminespirit.neoforge.movement;

import com.theminespirit.core.movement.WalkAscent;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.RemovalLog;
import com.theminespirit.core.route.Route;
import com.theminespirit.core.route.RouteStep;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class RouteWalk {
    public enum State {
        RUNNING,
        DONE,
        FAILED
    }

    private static final int STEP_TICKS = 60;
    private static final double LIFT = 0.2;
    private static final double EDGE_PUSH = 0.25;
    private static final double SPEED = 1.0;
    private final SpiritEntity spirit;
    private final List<RouteStep> steps;
    private final BlockCoord target;
    private final double cost;
    private final SwimStep swimmer;
    private final PassageWalk passages;
    private int next;
    private int stepTicks;
    private @Nullable String failure;
    private final RemovalLog removals = new RemovalLog();
    private int recorded = -1;

    public RouteWalk(SpiritEntity spirit, Route route, BlockCoord target) {
        this.spirit = spirit;
        this.steps = route.steps();
        this.target = target;
        this.cost = route.cost();
        this.swimmer = new SwimStep(spirit);
        this.passages = new PassageWalk(spirit);
    }

    public State tick() {
        if (failure != null) {
            return State.FAILED;
        }
        if (next >= steps.size()) {
            return State.DONE;
        }
        RouteStep step = steps.get(next);
        if (passages.openToward(step.cell())) {
            return State.RUNNING;
        }
        if ((step.action() == RouteStep.Action.WALK || step.action() == RouteStep.Action.SWIM)
                && SwimStep.far(spirit, step.cell())) {
            return fail("дух сошёл с маршрута: шаг " + step.cell() + " дальше соседней клетки");
        }
        if (recorded != next) {
            RouteHistory.record(spirit, step);
        }
        recorded = next;
        if (++stepTicks > STEP_TICKS) {
            String details = step.action() == RouteStep.Action.WALK || step.action() == RouteStep.Action.SWIM
                    ? WalkReport.describe(spirit, step.cell())
                    : "";
            return fail("шаг " + step.action() + " в " + step.cell() + " не выполнен за " + STEP_TICKS + " тиков"
                    + details);
        }
        Optional<String> refusal = switch (step.action()) {
            case SWIM -> swimmer.tick(step.cell()) ? advance() : Optional.<String>empty();
            case WALK -> walk(step.cell());
            case BREAK, PLACE, PILLAR -> Optional.of("навигация не меняет мир: шаг " + step.action());
        };
        if (refusal.isPresent()) {
            return fail(step.action() + " в " + step.cell() + ": " + refusal.get());
        }
        return next >= steps.size() ? State.DONE : State.RUNNING;
    }

    public int ticksBudget() {
        return (int) Math.ceil(cost) * 2 + STEP_TICKS * steps.size();
    }

    public RemovalLog removals() {
        return removals;
    }

    public String failure() {
        return failure == null ? "-" : failure;
    }

    public BlockCoord target() {
        return target;
    }

    public void cancel() {
        passages.settle();
        spirit.getNavigation().stop();
    }

    private Optional<String> walk(BlockCoord cell) {
        if (spirit.isInWater()) {
            return swimmer.exit(cell) ? advance() : Optional.<String>empty();
        }
        WalkAscent.Command command = WalkAscent.next(cell, pose(), body(cell));
        if (command.step() == WalkAscent.Step.ARRIVED) {
            advance();
            return Optional.empty();
        }
        spirit.getNavigation().stop();
        boolean fall = command.step() == WalkAscent.Step.MOVE || command.step() == WalkAscent.Step.DIVE;
        if (fall && command.y() < spirit.getY() - 0.5 && !spirit.isInWater()) {
            pushOff(command);
            if (command.step() == WalkAscent.Step.DIVE) {
                Vec3 motion = spirit.getDeltaMovement();
                spirit.setDeltaMovement(motion.x, Math.min(motion.y, -0.1), motion.z);
                spirit.getJumpControl().jump();
            }
            return Optional.empty();
        }
        spirit.getMoveControl().setWantedPosition(command.x(), command.y(), command.z(), SPEED);
        if (command.step() == WalkAscent.Step.JUMP) {
            spirit.getJumpControl().jump();
        } else if (command.step() == WalkAscent.Step.CLIMB) {
            Vec3 motion = spirit.getDeltaMovement();
            spirit.setDeltaMovement(motion.x, LIFT, motion.z);
        } else if (command.step() == WalkAscent.Step.SWIM) {
            Vec3 motion = spirit.getDeltaMovement();
            spirit.setDeltaMovement(motion.x, Math.max(motion.y, LIFT), motion.z);
            spirit.getJumpControl().jump();
        } else if (command.step() == WalkAscent.Step.DIVE) {
            Vec3 motion = spirit.getDeltaMovement();
            Vec3 toward = new Vec3(command.x() - spirit.getX(), 0.0, command.z() - spirit.getZ());
            if (toward.lengthSqr() > 0.04) {
                Vec3 push = toward.normalize().scale(EDGE_PUSH);
                motion = new Vec3(motion.x + push.x, Math.min(motion.y, -0.1), motion.z + push.z);
            } else {
                motion = new Vec3(motion.x, Math.min(motion.y, -0.1), motion.z);
            }
            spirit.setDeltaMovement(motion);
        }
        return Optional.empty();
    }

    private void pushOff(WalkAscent.Command command) {
        spirit.getMoveControl().setWantedPosition(command.x(), command.y(), command.z(), SPEED);
        Vec3 toward = new Vec3(command.x() - spirit.getX(), 0.0, command.z() - spirit.getZ());
        if (toward.lengthSqr() > 1e-6) {
            Vec3 push = toward.normalize().scale(EDGE_PUSH);
            Vec3 motion = spirit.getDeltaMovement();
            spirit.setDeltaMovement(motion.x + push.x, motion.y, motion.z + push.z);
        }
    }

    private WalkAscent.Pose pose() {
        BlockPos feet = spirit.blockPosition();
        return new WalkAscent.Pose(
                new BlockCoord(feet.getX(), feet.getY(), feet.getZ()), spirit.getX(), spirit.getY(), spirit.getZ());
    }

    private WalkAscent.Body body(BlockCoord cell) {
        BlockPos target = pos(cell);
        boolean water = spirit.level().getFluidState(target).is(FluidTags.WATER)
                || spirit.level().getFluidState(target.below()).is(FluidTags.WATER);
        return new WalkAscent.Body(spirit.onGround(), spirit.isInWater(), spirit.onClimbable(), water);
    }

    private Optional<String> advance() {
        next++;
        stepTicks = 0;
        passages.settle();
        return Optional.<String>empty();
    }

    private State fail(String reason) {
        failure = reason;
        cancel();
        return State.FAILED;
    }

    private static BlockPos pos(BlockCoord cell) {
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }
}
