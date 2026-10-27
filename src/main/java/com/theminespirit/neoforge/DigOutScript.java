package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.GotoParams;
import com.theminespirit.neoforge.movement.RouteDetour;
import com.theminespirit.neoforge.movement.RouteWalk;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

public final class DigOutScript implements ScriptInstance {
    private static final int MAX_STEPS = 96;
    private static final int STEP_TICKS = 60;
    private static final int SEALED_SCAN = 256;
    private static final String FLOOR_BLOCK = "minecraft:cobblestone";
    private static final int HOME_RADIUS = 2;

    private final SpiritEntity spirit;
    private final Level level;
    private final GotoParams target;
    private final boolean fromPit;
    private final BlockBreaker breaker;
    private final BlockPlacer placer;
    private Direction direction;
    private BlockPos next;
    private boolean breaking;
    private int stepTicks;
    private int steps;
    private int turns;
    private ScriptStatus outcome = ScriptStatus.RUNNING;
    private String failureReason;
    private boolean routePlanned;
    private @Nullable RouteWalk route;

    public DigOutScript(SpiritEntity spirit, GotoParams target) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.target = target;
        this.fromPit = open(level, spirit.blockPosition());
        this.breaker = BlockBreaker.escaping(spirit);
        this.placer = new BlockPlacer(spirit);
    }

    public static boolean sealed(Level level, BlockPos feet) {
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(feet);
        seen.add(feet);
        while (!queue.isEmpty()) {
            BlockPos cell = queue.poll();
            if (open(level, cell)) {
                return false;
            }
            if (seen.size() > SEALED_SCAN) {
                return false;
            }
            for (Direction side : Direction.values()) {
                BlockPos near = cell.relative(side);
                if (level.isLoaded(near) && walkable(level, near) && seen.add(near)) {
                    queue.add(near);
                }
            }
        }
        return true;
    }

    public static boolean open(Level level, BlockPos cell) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cell.getX(), cell.getZ()) <= cell.getY();
    }

    private static boolean walkable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof FenceGateBlock
                || state.getBlock() instanceof TrapDoorBlock) {
            return true;
        }
        return passable(level, pos);
    }

    private static boolean passable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty()
                && state.getFluidState().isEmpty();
    }

    @Override
    public ScriptStatus tick() {
        if (outcome != ScriptStatus.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез, выбираясь из-под земли");
        }
        if (followRoute()) {
            return ScriptStatus.RUNNING;
        }
        if (breaking) {
            if (breaker.tick() == BlockBreaker.Progress.BREAKING) {
                return ScriptStatus.RUNNING;
            }
            breaking = false;
        }
        BlockPos feet = spirit.blockPosition();
        if ((open(level, feet) && !fromPit) || feet.getY() >= target.y()) {
            outcome = ScriptStatus.SUCCEEDED;
            return outcome;
        }
        if (next != null) {
            return walkTick(feet);
        }
        if (steps >= MAX_STEPS) {
            return fail("за " + MAX_STEPS + " ступеней не вышел на поверхность, стоит в " + feet.toShortString());
        }
        if (direction == null) {
            direction = towardTarget(feet);
        }
        BlockPos ahead = feet.relative(direction);
        for (BlockPos cell : List.of(feet.above(2), ahead.above(), ahead.above(2))) {
            if (!passable(level, cell)) {
                return dig(cell);
            }
        }
        if (passable(level, ahead)) {
            Optional<String> refusal = placeFloor(ahead);
            if (refusal.isPresent()) {
                return turnOrFail("пол ступени " + ahead.toShortString() + " не подложить: " + refusal.get());
            }
        }
        next = ahead.above();
        stepTicks = 0;
        spirit.getNavigation().moveTo(next.getX() + 0.5, next.getY(), next.getZ() + 0.5, 1.0);
        return ScriptStatus.RUNNING;
    }

    private boolean followRoute() {
        if (!routePlanned) {
            routePlanned = true;
            route = RouteDetour.plan(spirit, new BlockCoord(target.x(), target.y(), target.z()), HOME_RADIUS, "dig_out")
                    .orElse(null);
        }
        RouteWalk current = route;
        if (current == null) {
            return false;
        }
        RouteWalk.State state = current.tick();
        if (state == RouteWalk.State.RUNNING) {
            return true;
        }
        if (state == RouteWalk.State.FAILED) {
            RouteDetour.failed(spirit, current);
        }
        RouteDetour.removed(spirit, current);
        route = null;
        return false;
    }

    private ScriptStatus walkTick(BlockPos feet) {
        if (feet.equals(next)) {
            next = null;
            steps++;
            turns = 0;
            return ScriptStatus.RUNNING;
        }
        if (++stepTicks > STEP_TICKS) {
            BlockPos missed = next;
            next = null;
            return turnOrFail("не шагнул на ступень " + missed.toShortString() + " за " + STEP_TICKS + " тиков");
        }
        if (spirit.getNavigation().isDone()) {
            spirit.getNavigation().moveTo(next.getX() + 0.5, next.getY(), next.getZ() + 0.5, 1.0);
        }
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus dig(BlockPos cell) {
        if (!level.getBlockState(cell).getFluidState().isEmpty()) {
            return turnOrFail("в проходе жидкость в " + cell.toShortString());
        }
        return switch (breaker.begin(cell)) {
            case BlockBreaker.Started ignored -> {
                breaking = true;
                yield ScriptStatus.RUNNING;
            }
            case BlockBreaker.Refused refused ->
                turnOrFail("не сломать " + cell.toShortString() + ": " + refused.message());
            case BlockBreaker.Protected refused ->
                turnOrFail("нельзя ломать " + cell.toShortString() + ": " + refused.reason());
        };
    }

    private Optional<String> placeFloor(BlockPos cell) {
        return switch (placer.begin(cell, Blocks.COBBLESTONE.defaultBlockState(), FLOOR_BLOCK)) {
            case BlockPlacer.Started ignored -> Optional.empty();
            case BlockPlacer.Refused refused -> Optional.of(refused.message());
            case BlockPlacer.Protected refused -> Optional.of(refused.reason());
        };
    }

    private ScriptStatus turnOrFail(String reason) {
        turns++;
        if (turns >= Direction.Plane.HORIZONTAL.length()) {
            return fail(reason);
        }
        direction = direction == null ? Direction.NORTH : direction.getClockWise();
        return ScriptStatus.RUNNING;
    }

    private Direction towardTarget(BlockPos feet) {
        int dx = target.x() - feet.getX();
        int dz = target.z() - feet.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private ScriptStatus fail(String reason) {
        failureReason = reason;
        outcome = ScriptStatus.FAILED;
        return outcome;
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public void cancel() {
        if (route != null) {
            route.cancel();
            route = null;
        }
        if (breaking) {
            breaker.cancel();
            breaking = false;
        }
        spirit.getNavigation().stop();
    }

    @Override
    public Map<String, Object> saveState() {
        return Map.of();
    }

    @Override
    public void restoreState(Map<String, Object> state) {}
}
