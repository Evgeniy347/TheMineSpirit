package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.movement.DigOutObstacle;
import com.theminespirit.core.movement.SealedSpace;
import com.theminespirit.core.movement.WaterCrossingPlanner;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.GotoParams;
import com.theminespirit.neoforge.movement.CheapBlocks;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class DigOutScript implements ScriptInstance {
    private static final int MAX_STEPS = 160;
    private static final int STEP_TICKS = 60;
    private static final int HOME_RADIUS = 2;

    private final SpiritEntity spirit;
    private final Level level;
    private final GotoParams target;
    private final boolean fromPit;
    private final boolean descending;
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
    private @Nullable DigOutRoute routeFollow;

    public DigOutScript(SpiritEntity spirit, GotoParams target) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.target = target;
        this.fromPit = open(level, spirit.blockPosition());
        this.descending = target.y() < spirit.blockPosition().getY();
        this.breaker = BlockBreaker.escaping(spirit);
        this.placer = new BlockPlacer(spirit);
    }

    public static boolean sealed(Level level, BlockPos feet) {
        return SealedSpace.sealed(new BlockCoord(feet.getX(), feet.getY(), feet.getZ()), new SealedView(level));
    }

    public static boolean embedded(SpiritEntity spirit) {
        return EmbeddedSpace.inside(spirit);
    }

    public static boolean open(Level level, BlockPos cell) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cell.getX(), cell.getZ()) <= cell.getY();
    }

    private record SealedView(Level level) implements SealedSpace.View {
        @Override
        public boolean passable(BlockCoord cell) {
            BlockPos pos = pos(cell);
            if (!level.isLoaded(pos)) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock
                    || state.getBlock() instanceof FenceGateBlock
                    || state.getBlock() instanceof TrapDoorBlock) {
                return true;
            }
            return DigOutScript.passable(level, pos);
        }

        @Override
        public boolean swimmable(BlockCoord cell) {
            BlockPos pos = pos(cell);
            return level.isLoaded(pos) && level.getFluidState(pos).is(FluidTags.WATER);
        }

        @Override
        public boolean sky(BlockCoord cell) {
            return open(level, pos(cell));
        }

        private static BlockPos pos(BlockCoord cell) {
            return new BlockPos(cell.x(), cell.y(), cell.z());
        }
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
        if (embedded(spirit) && !breaking) {
            BlockPos feet = spirit.blockPosition();
            BlockBreaker.Begin begin = breaker.begin(feet);
            if (begin instanceof BlockBreaker.Started) {
                breaking = true;
                return ScriptStatus.RUNNING;
            }
            String why = begin instanceof BlockBreaker.Refused refused
                    ? refused.message()
                    : ((BlockBreaker.Protected) begin).reason();
            return fail("не выйти из блока " + feet.toShortString() + ": " + why);
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
        boolean goalReached = descending
                ? feet.getY() <= target.y() && (open(level, feet) || feet.getY() == target.y())
                : feet.getY() >= target.y();
        if ((open(level, feet) && !fromPit) || goalReached) {
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
            if (DigOutObstacle.obstacle(passable(level, cell), water(level, cell))) {
                return dig(cell);
            }
        }
        if (passable(level, ahead) || water(level, ahead)) {
            Optional<String> refusal = placeFloor(ahead);
            if (refusal.isPresent()) {
                return turnOrFail("пол ступени " + ahead.toShortString() + " не подложить: " + refusal.get());
            }
        }
        next = ahead.above();
        stepTicks = 0;
        moveTo(next);
        return ScriptStatus.RUNNING;
    }

    private boolean followRoute() {
        if (routeFollow == null) {
            routeFollow = new DigOutRoute(
                    spirit, new BlockCoord(target.x(), target.y(), target.z()), descending, HOME_RADIUS);
        }
        DigOutRoute.State state = routeFollow.tick();
        if (state == DigOutRoute.State.DESCENT_FAILED) {
            routeFollow = null;
            fail("пути вниз к " + target + " нет: ступени и прыжок не находятся");
            return false;
        }
        return state == DigOutRoute.State.RUNNING;
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
        moveTo(next);
        return ScriptStatus.RUNNING;
    }

    private void moveTo(BlockPos cell) {
        if (spirit.isInWater()) {
            spirit.getNavigation().stop();
            spirit.getMoveControl().setWantedPosition(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, 1.0);
            Vec3 motion = spirit.getDeltaMovement();
            spirit.setDeltaMovement(motion.x, Math.max(motion.y, 0.2), motion.z);
            spirit.getJumpControl().jump();
            return;
        }
        if (spirit.getNavigation().isDone()) {
            spirit.getNavigation().moveTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, 1.0);
        }
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
        Optional<String> blockId = WaterCrossingPlanner.cheapestAvailable(CheapBlocks.counts(spirit));
        if (blockId.isEmpty()) {
            return Optional.of("в инвентаре нет дешёвых блоков для ступени");
        }
        String id = blockId.get();
        return switch (placer.begin(cell, BlockStates.stateFor(id), id)) {
            case BlockPlacer.Started ignored -> Optional.empty();
            case BlockPlacer.Refused refused -> Optional.of(refused.message());
            case BlockPlacer.Protected refused -> Optional.of(refused.reason());
        };
    }

    private static boolean water(Level level, BlockPos cell) {
        return level.getFluidState(cell).is(FluidTags.WATER);
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
        if (routeFollow != null) {
            routeFollow.cancel();
            routeFollow = null;
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
