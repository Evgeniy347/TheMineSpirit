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
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class RouteWalk {
    public enum State {
        RUNNING,
        DONE,
        FAILED
    }

    private static final int STEP_TICKS = 60;
    private static final double CENTER = 0.5;
    private static final double ARRIVAL = 0.3;
    private static final double LIFT = 0.2;
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
        if (!breaking && ++stepTicks > STEP_TICKS) {
            String details = step.action() == RouteStep.Action.WALK ? walkState(step.cell()) : "";
            return fail("шаг " + step.action() + " в " + step.cell() + " не выполнен за " + STEP_TICKS + " тиков"
                    + details);
        }
        Optional<String> refusal = switch (step.action()) {
            case BREAK -> breakCell(pos(step.cell()));
            case PLACE -> placeCell(pos(step.cell()));
            case PILLAR -> pillar(step.cell());
            case WALK -> walk(step.cell());
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
        Vec3 target = new Vec3(cell.x() + CENTER, cell.y(), cell.z() + CENTER);
        boolean arrived = spirit.blockPosition().equals(pos(cell))
                && horizontal(target) < ARRIVAL
                && (spirit.onGround() || spirit.isInWater() || spirit.onClimbable());
        if (arrived) {
            advance();
            return Optional.empty();
        }
        spirit.getNavigation().stop();
        spirit.getMoveControl().setWantedPosition(target.x, target.y, target.z, SPEED);
        WalkAscent.Lift lift = WalkAscent.lift(
                cell.y() > spirit.blockPosition().getY(),
                new WalkAscent.Body(spirit.onGround(), spirit.isInWater(), spirit.onClimbable()));
        if (lift == WalkAscent.Lift.JUMP) {
            spirit.getJumpControl().jump();
        } else if (lift == WalkAscent.Lift.CLIMB) {
            Vec3 motion = spirit.getDeltaMovement();
            spirit.setDeltaMovement(motion.x, LIFT, motion.z);
        }
        return Optional.empty();
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

    private String walkState(BlockCoord cell) {
        Level level = spirit.level();
        BlockPos pos = pos(cell);
        BlockState state = level.getBlockState(pos);
        boolean collision = !state.getCollisionShape(level, pos).isEmpty();
        boolean climbable = state.is(BlockTags.CLIMBABLE);
        boolean rising = cell.y() > spirit.blockPosition().getY();
        String code = rising ? (climbable ? "CLIMB_STALLED" : "JUMP_STALLED") : "NO_PROGRESS";
        if (collision) {
            code = "CELL_BLOCKED";
        }
        double distance = horizontal(new Vec3(cell.x() + CENTER, cell.y(), cell.z() + CENTER));
        return ": блок " + BlockStates.idOf(state)
                + ", collision=" + collision
                + ", climbable=" + climbable
                + ", onGround=" + spirit.onGround()
                + ", onClimbable=" + spirit.onClimbable()
                + ", дистанция=" + String.format(Locale.ROOT, "%.2f", distance)
                + ", код=" + code;
    }

    private double horizontal(Vec3 target) {
        double dx = spirit.getX() - target.x;
        double dz = spirit.getZ() - target.z;
        return Math.sqrt(dx * dx + dz * dz);
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
