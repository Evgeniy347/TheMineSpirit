package com.theminespirit.neoforge.movement;

import com.theminespirit.core.movement.WalkAscent;
import com.theminespirit.core.movement.WaterCrossingPlanner;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.RemovalLog;
import com.theminespirit.core.route.Route;
import com.theminespirit.core.route.RouteStep;
import com.theminespirit.neoforge.BlockBreaker;
import com.theminespirit.neoforge.BlockPlacer;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
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
    private final BlockBreaker breaker;
    private final BlockPlacer placer;
    private int next;
    private int stepTicks;
    private boolean breaking;
    private boolean pillarPlaced;
    private @Nullable String failure;
    private final RemovalLog removals = new RemovalLog();
    private String breakingId = "-";
    private int recorded = -1;

    public RouteWalk(SpiritEntity spirit, Route route, BlockCoord target) {
        this.spirit = spirit;
        this.steps = route.steps();
        this.target = target;
        this.cost = route.cost();
        this.breaker = new BlockBreaker(spirit);
        this.placer = new BlockPlacer(spirit);
    }

    public State tick() {
        if (failure != null) {
            return State.FAILED;
        }
        if (next >= steps.size()) {
            return State.DONE;
        }
        RouteStep step = steps.get(next);
        if (recorded != next) {
            RouteHistory.record(spirit, step);
            recorded = next;
        }
        if (!breaking && ++stepTicks > STEP_TICKS) {
            String details = step.action() == RouteStep.Action.WALK || step.action() == RouteStep.Action.SWIM
                    ? WalkReport.describe(spirit, step.cell())
                    : "";
            return fail("шаг " + step.action() + " в " + step.cell() + " не выполнен за " + STEP_TICKS + " тиков"
                    + details);
        }
        Optional<String> refusal = switch (step.action()) {
            case BREAK -> breakCell(pos(step.cell()));
            case PLACE -> placeCell(pos(step.cell()));
            case PILLAR -> pillar(step.cell());
            case WALK, SWIM -> walk(step.cell());
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
        if (breaking) {
            breaker.cancel();
            breaking = false;
        }
        spirit.getNavigation().stop();
    }

    private Optional<String> breakCell(BlockPos cell) {
        Level level = spirit.level();
        if (breaking) {
            BlockBreaker.Progress progress = breaker.tick();
            if (progress == BlockBreaker.Progress.BREAKING) {
                return Optional.empty();
            }
            breaking = false;
            if (progress == BlockBreaker.Progress.BROKEN) {
                removals.removed(breakingId, new BlockCoord(cell.getX(), cell.getY(), cell.getZ()));
            }
        }
        if (level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()) {
            advance();
            return Optional.empty();
        }
        return switch (breaker.begin(cell)) {
            case BlockBreaker.Started ignored -> {
                breaking = true;
                breakingId = BlockStates.idOf(level.getBlockState(cell));
                yield Optional.empty();
            }
            case BlockBreaker.Refused refused -> Optional.of(refused.message());
            case BlockBreaker.Protected refused -> Optional.of(refused.reason());
        };
    }

    private Optional<String> placeCell(BlockPos cell) {
        Level level = spirit.level();
        if (!level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()) {
            advance();
            return Optional.empty();
        }
        Optional<String> placed = place(cell);
        if (placed.isEmpty()) {
            advance();
        }
        return placed;
    }

    private Optional<String> pillar(BlockCoord cell) {
        BlockPos below = pos(cell);
        if (pillarPlaced) {
            if (spirit.onGround() && spirit.blockPosition().getY() == cell.y() + 1) {
                pillarPlaced = false;
                advance();
            }
            return Optional.empty();
        }
        spirit.getNavigation().stop();
        if (spirit.onGround()) {
            spirit.getJumpControl().jump();
            return Optional.empty();
        }
        if (spirit.getY() > cell.y() + 1.0) {
            Optional<String> placed = place(below);
            pillarPlaced = placed.isEmpty();
            return placed;
        }
        return Optional.empty();
    }

    private Optional<String> walk(BlockCoord cell) {
        WalkAscent.Command command = WalkAscent.next(cell, pose(), body(cell));
        if (command.step() == WalkAscent.Step.ARRIVED) {
            advance();
            return Optional.empty();
        }
        if (command.step() == WalkAscent.Step.SWIM || command.step() == WalkAscent.Step.CLIMB) {
            Optional<String> support = ensureSupport(cell);
            if (support.isPresent()) {
                return support;
            }
        }
        spirit.getNavigation().stop();
        boolean fall = command.step() == WalkAscent.Step.MOVE || command.step() == WalkAscent.Step.DIVE;
        if (fall && command.y() < spirit.getY() - 0.5 && !spirit.isInWater()) {
            pushOff(command);
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
            spirit.setDeltaMovement(motion.x, Math.min(motion.y, 0.0), motion.z);
        }
        return Optional.empty();
    }

    private void pushOff(WalkAscent.Command command) {
        spirit.getMoveControl().setWantedPosition(command.x(), spirit.getY(), command.z(), SPEED);
        Vec3 toward = new Vec3(command.x() - spirit.getX(), 0.0, command.z() - spirit.getZ());
        if (toward.lengthSqr() > 1e-6) {
            Vec3 push = toward.normalize().scale(EDGE_PUSH);
            Vec3 motion = spirit.getDeltaMovement();
            spirit.setDeltaMovement(motion.x + push.x, motion.y, motion.z + push.z);
        }
    }

    private Optional<String> ensureSupport(BlockCoord cell) {
        if (!spirit.isInWater()) {
            return Optional.empty();
        }
        BlockPos target = pos(cell);
        BlockPos feet = spirit.blockPosition();
        if (target.getY() <= feet.getY()) {
            return Optional.empty();
        }
        BlockPos below = target.below();
        Level level = spirit.level();
        if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
            return Optional.empty();
        }
        if (!below.equals(feet)) {
            return place(below);
        }
        if (spirit.getY() < below.getY() + 0.6) {
            spirit.getNavigation().stop();
            Vec3 motion = spirit.getDeltaMovement();
            spirit.setDeltaMovement(motion.x, Math.max(motion.y, LIFT), motion.z);
            spirit.getJumpControl().jump();
            return Optional.empty();
        }
        return place(below);
    }

    private WalkAscent.Pose pose() {
        BlockPos feet = spirit.blockPosition();
        return new WalkAscent.Pose(
                new BlockCoord(feet.getX(), feet.getY(), feet.getZ()), spirit.getX(), spirit.getY(), spirit.getZ());
    }

    private WalkAscent.Body body(BlockCoord cell) {
        BlockPos target = pos(cell);
        boolean water = spirit.level().getFluidState(target).is(FluidTags.WATER);
        return new WalkAscent.Body(spirit.onGround(), spirit.isInWater(), spirit.onClimbable(), water);
    }

    private Optional<String> place(BlockPos cell) {
        Optional<String> blockId = WaterCrossingPlanner.cheapestAvailable(CheapBlocks.counts(spirit));
        if (blockId.isEmpty()) {
            return Optional.of("в инвентаре нет дешёвых блоков для постановки");
        }
        String id = blockId.get();
        return switch (placer.beginWithoutSupport(cell, BlockStates.stateFor(id), id)) {
            case BlockPlacer.Started ignored -> Optional.empty();
            case BlockPlacer.Refused refused -> Optional.of(refused.message());
            case BlockPlacer.Protected refused -> Optional.of(refused.reason());
        };
    }

    private void advance() {
        next++;
        stepTicks = 0;
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
