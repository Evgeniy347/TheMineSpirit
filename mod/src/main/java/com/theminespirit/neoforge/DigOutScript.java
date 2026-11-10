package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptFactory;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.movement.DigOutObstacle;
import com.theminespirit.core.movement.SealedSpace;
import com.theminespirit.core.movement.WaterCrossingPlanner;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.GotoParams;
import com.theminespirit.core.town.PlotProtection;
import com.theminespirit.neoforge.movement.CheapBlocks;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
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
    private final boolean rescue;
    private final boolean fromPit;
    private final boolean descending;
    private final DigOutBackfill backfill;
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
    private boolean routeFallback;
    private final DigOutRestore restore;

    public DigOutScript(SpiritEntity spirit, GotoParams target) {
        this(spirit, target, false);
    }

    public DigOutScript(SpiritEntity spirit, GotoParams target, boolean rescue) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.target = target;
        this.rescue = rescue;
        this.fromPit = open(level, spirit.blockPosition());
        this.descending = target.y() < spirit.blockPosition().getY();
        this.breaker = BlockBreaker.escaping(spirit, rescue);
        this.placer = new BlockPlacer(spirit, PlotProtection.Ground.TEMPORARY_MOVEMENT);
        this.restore = new DigOutRestore(spirit, level);
        boolean sealedNow = sealed(level, spirit.blockPosition());
        this.backfill = new DigOutBackfill(
                spirit,
                level,
                placer,
                !descending && !sealedNow && target.y() > spirit.blockPosition().getY(),
                restore);
    }

    static ScriptFactory factory(SpiritEntity spirit) {
        return params -> new DigOutScript(spirit, GotoParams.from(params), GotoParams.rescue(params));
    }

    public static boolean sealed(Level level, BlockPos feet) {
        return SealedSpace.sealed(new BlockCoord(feet.getX(), feet.getY(), feet.getZ()), new DigOutSealedView(level));
    }

    public static boolean embedded(SpiritEntity spirit) {
        return EmbeddedSpace.inside(spirit);
    }

    public static boolean open(Level level, BlockPos cell) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cell.getX(), cell.getZ()) <= cell.getY();
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
            BlockState inside = level.getBlockState(feet);
            BlockBreaker.Begin begin = breaker.begin(feet);
            if (begin instanceof BlockBreaker.Started) {
                restore.remember(feet, inside);
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
        if ((DigOutExit.surfaced(level, feet) && !fromPit && !restore.pendingOver(feet)) || goalReached) {
            backfill.report();
            restore.returnSpoil();
            PlanBreachRestore.restore(spirit, level, placer, feet);
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
        boolean sideways = DigOutExit.fixedCeiling(level, feet);
        List<BlockPos> clear =
                sideways ? List.of(ahead.above(), ahead) : List.of(feet.above(2), ahead.above(), ahead.above(2));
        for (BlockPos cell : clear) {
            if (DigOutObstacle.obstacle(DigOutBackfill.passable(level, cell), DigOutBackfill.water(level, cell))) {
                return dig(cell);
            }
        }
        BlockPos step = sideways ? ahead : ahead.above();
        BlockPos floor = step.below();
        if (DigOutBackfill.passable(level, floor) || DigOutBackfill.water(level, floor)) {
            Optional<String> refusal = placeFloor(floor);
            if (refusal.isPresent()) {
                return turnOrFail("пол ступени " + floor.toShortString() + " не подложить: " + refusal.get());
            }
        }
        backfill.remember(feet);
        next = step;
        stepTicks = 0;
        moveTo(next);
        return ScriptStatus.RUNNING;
    }

    private boolean followRoute() {
        if (routeFallback) {
            return false;
        }
        if (routeFollow == null) {
            routeFollow = new DigOutRoute(
                    spirit, new BlockCoord(target.x(), target.y(), target.z()), descending, HOME_RADIUS, rescue);
        }
        DigOutRoute.State state = routeFollow.tick();
        if (state == DigOutRoute.State.DESCENT_FAILED || state == DigOutRoute.State.NO_ROUTE) {
            routeFollow = null;
            routeFallback = true;
            return false;
        }
        return state == DigOutRoute.State.RUNNING;
    }

    private ScriptStatus walkTick(BlockPos feet) {
        if (feet.equals(next)) {
            backfill.close();
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
        spirit.getNavigation().stop();
        spirit.getMoveControl().setWantedPosition(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, 1.0);
    }

    private ScriptStatus dig(BlockPos cell) {
        if (!level.getBlockState(cell).getFluidState().isEmpty()) {
            return turnOrFail("в проходе жидкость в " + cell.toShortString());
        }
        BlockState before = level.getBlockState(cell);
        return switch (breaker.begin(cell)) {
            case BlockBreaker.Started ignored -> {
                restore.remember(cell, before);
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
            case BlockPlacer.Started ignored -> {
                if (level instanceof ServerLevel server) {
                    PlacedBlocksData.get(server).markTemporary(cell);
                }
                yield Optional.empty();
            }
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
        restore.returnSpoil();
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
        restore.returnSpoil();
    }

    @Override
    public Map<String, Object> saveState() {
        return Map.of();
    }

    @Override
    public void restoreState(Map<String, Object> state) {}
}
