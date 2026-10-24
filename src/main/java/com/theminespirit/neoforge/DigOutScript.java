package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.GotoParams;
import java.util.ArrayDeque;
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

/**
 * Самовыход замурованного духа (требование владельца, INC-45): лестница вверх в сторону точки — ступень 1, проход 2 высоты.
 * Шаг: расчистить голову над собой и две клетки впереди-выше, подложить пол ступени, если там пусто, шагнуть.
 * Конец — дух под открытым небом или на высоте точки. Ломка и постановка — обычные (BlockBreaker/BlockPlacer):
 * охраняемые блоки и неломаемые (бедрок) — смена направления, затем отказ.
 */
public final class DigOutScript implements ScriptInstance {
    private static final int MAX_STEPS = 96;
    private static final int STEP_TICKS = 60;
    private static final int SEALED_SCAN = 256;
    private static final String FLOOR_BLOCK = "minecraft:cobblestone";

    private final SpiritEntity spirit;
    private final Level level;
    private final GotoParams target;
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

    public DigOutScript(SpiritEntity spirit, GotoParams target) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.target = target;
        this.breaker = new BlockBreaker(spirit);
        this.placer = new BlockPlacer(spirit);
    }

    // Замурован: из клетки духа по проходимым клеткам не дойти ни до одной под открытым небом (обход ограничен —
    // большая пещера замурованной не считается).
    public static boolean sealed(Level level, BlockPos feet) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
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

    // Под открытым небом — над клеткой нет твёрдых блоков (карта высот). Не canSeeSky: тот смотрит на небесный свет,
    // а он в засыпанной яме бывает устаревшим — живой карман INC-59 не считался замурованным.
    public static boolean open(Level level, BlockPos cell) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cell.getX(), cell.getZ()) <= cell.getY();
    }

    // Дверь, калитка и люк — проход: закрытая хижина с духами внутри считалась замурованной, и дух рыл стену
    // хижины (приёмка «джунгли» 24.10).
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
        if (breaking) {
            if (breaker.tick() == BlockBreaker.Progress.BREAKING) {
                return ScriptStatus.RUNNING;
            }
            breaking = false;
        }
        BlockPos feet = spirit.blockPosition();
        if (open(level, feet) || feet.getY() >= target.y()) {
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
            case BlockBreaker.Started started -> {
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
            case BlockPlacer.Started started -> Optional.empty();
            case BlockPlacer.Refused refused -> Optional.of(refused.message());
            case BlockPlacer.Protected refused -> Optional.of(refused.reason());
        };
    }

    // Направление — к точке по большей разнице; упёрлись — следующее по кругу, четыре отказа подряд — провал.
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
