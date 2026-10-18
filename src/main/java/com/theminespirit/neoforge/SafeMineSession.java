package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildReservations;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.mining.BreakSafety;
import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.DescentDirection;
import com.theminespirit.core.mining.DescentStop;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.mining.SafeMineMode;
import com.theminespirit.core.mining.SafeMinePlan;
import com.theminespirit.core.mining.SafeMineStep;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.pickup.OverflowMode;
import com.theminespirit.core.pickup.PickupTuning;
import com.theminespirit.core.script.SafeMineParams;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Исполнение {@link SafeMinePlan} в мире: копает проход 1×2, закладывает опасные клетки блоком из
 * инвентаря, ждёт осыпания сыпучего блока над головой и копает его, ставит факелы через интервал.
 *
 * <p>Дух копает шаг со предыдущей ступени и заходит в проход только после того, как тот выкопан и
 * сыпучее осыпано: падающий гравий не засыпает духа.
 */
public final class SafeMineSession {
    public enum Outcome {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    private enum State {
        PLAN_CHECK,
        FILL,
        DIG_BEGIN,
        BREAK,
        COLLECT,
        FALL_WAIT,
        APPROACH,
        TORCH
    }

    private static final double APPROACH_ARRIVAL = 0.6;
    private static final int COLLECT_RADIUS = 4;
    private static final int FALL_SETTLE_TICKS = 8;
    private static final int FALL_WAIT_TICKS = 60;
    private static final String TORCH_BLOCK_ID = "minecraft:torch";
    private static final List<String> FILL_BLOCKS = List.of(
            "minecraft:cobblestone", "minecraft:stone", "minecraft:dirt",
            "minecraft:gravel", "minecraft:sand", "minecraft:oak_planks");

    private final SpiritEntity spirit;
    private final Level level;
    private final LevelSafetyView safety;
    private final BlockBreaker breaker;
    private final BlockPlacer placer;
    private final Approach approach;
    private final BuildReservations reservations;

    private SafeMinePlan plan;
    private int stepIndex;
    private int blocksDug;
    private int blocksFilled;
    private int torchesPlaced;
    private int cellCursor;
    private int fillCursor;
    private int fallWait;
    private boolean approachBegun;
    private String fillBlock;
    private List<BlockCoord> fillQueue = List.of();

    private State state = State.PLAN_CHECK;
    private Outcome outcome = Outcome.RUNNING;
    private String failureReason;
    private String stopReason;
    private DropCollector collector;

    public SafeMineSession(SpiritEntity spirit, SafeMineParams params) {
        this.spirit = Objects.requireNonNull(spirit, "Дух не может быть null: без него неизвестно, кто копает");
        Objects.requireNonNull(params, "Параметры не могут быть null: без них неизвестна форма шахты");
        this.level = spirit.level();
        this.safety = new LevelSafetyView(level);
        this.breaker = new BlockBreaker(spirit);
        this.placer = new BlockPlacer(spirit);
        this.approach = new Approach(spirit);
        this.reservations = BuildReservationHub.forLevel(level);
        BlockPos feet = spirit.blockPosition();
        BlockCoord entry = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        this.plan = switch (params.mode()) {
            case STAIRCASE -> SafeMinePlan.staircase(safety, entry, params.direction(), params.y(),
                    params.torchInterval());
            case TUNNEL -> SafeMinePlan.tunnel(safety, entry, params.direction(), params.length(),
                    params.torchInterval());
        };
    }

    public Outcome tick() {
        if (outcome != Outcome.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время проходки");
        }
        switch (state) {
            case PLAN_CHECK -> planCheck();
            case FILL -> fillTick();
            case DIG_BEGIN -> digBegin();
            case BREAK -> breakTick();
            case COLLECT -> collectTick();
            case FALL_WAIT -> fallWaitTick();
            case APPROACH -> approachTick();
            case TORCH -> torchTick();
        }
        return outcome;
    }

    public void cancel() {
        breaker.cancel();
        approach.cancel();
        if (collector != null) {
            collector.cancel();
        }
        reservations.releaseAll(spirit.getUUID());
    }

    public void suspend() {
        approach.suspend();
        if (collector != null) {
            collector.suspend();
        }
    }

    public Outcome outcome() {
        return outcome;
    }

    public String failureReason() {
        return failureReason;
    }

    public ScriptInstance.Progress progress() {
        return new ScriptInstance.Progress(stepIndex, Math.max(plan.steps().size(), 1));
    }

    public Map<String, Object> resultData() {
        int entryFloor = plan.entry().y() - 1;
        int y = stepIndex == 0 ? entryFloor : plan.steps().get(stepIndex - 1).floor();
        return Map.of(
                "reached", stopReason == null && plan.stop() == DescentStop.TARGET_Y,
                "y", y,
                "depth", entryFloor - y,
                "blocks_dug", blocksDug,
                "blocks_filled", blocksFilled,
                "torches", torchesPlaced,
                "reason", stopReason == null ? plan.dangerReason() : stopReason);
    }

    public Map<String, Object> saveState() {
        Map<String, Object> map = new HashMap<>();
        map.put("entry_x", plan.entry().x());
        map.put("entry_y", plan.entry().y());
        map.put("entry_z", plan.entry().z());
        map.put("mode", plan.mode().name().toLowerCase(Locale.ROOT));
        map.put("direction", plan.direction().name().toLowerCase(Locale.ROOT));
        map.put("torch_interval", plan.torchInterval());
        map.put("step", stepIndex);
        map.put("blocks_dug", blocksDug);
        map.put("blocks_filled", blocksFilled);
        map.put("torches", torchesPlaced);
        map.put("planned_steps", plan.steps().size());
        return Map.copyOf(map);
    }

    public void restoreState(Map<String, Object> saved) {
        Objects.requireNonNull(saved, "Снимок состояния не может быть null: пустое состояние задают пустой картой");
        BlockCoord entry = new BlockCoord(number(saved, "entry_x"), number(saved, "entry_y"), number(saved, "entry_z"));
        SafeMineMode mode = SafeMineMode.parse(text(saved, "mode"));
        DescentDirection direction = DescentDirection.parse(text(saved, "direction"));
        int torchInterval = number(saved, "torch_interval");
        int plannedSteps = number(saved, "planned_steps");
        plan = rebuild(entry, mode, direction, torchInterval, plannedSteps);
        blocksDug = number(saved, "blocks_dug");
        blocksFilled = number(saved, "blocks_filled");
        torchesPlaced = number(saved, "torches");
        stepIndex = Math.min(number(saved, "step"), plan.steps().size());
        cellCursor = 0;
        fillCursor = 0;
        fallWait = 0;
        approachBegun = false;
        fillBlock = null;
        state = State.PLAN_CHECK;
    }

    private SafeMinePlan rebuild(BlockCoord entry, SafeMineMode mode, DescentDirection direction,
            int torchInterval, int plannedSteps) {
        return switch (mode) {
            case STAIRCASE -> SafeMinePlan.staircase(safety, entry, direction, entry.y() - plannedSteps,
                    torchInterval);
            case TUNNEL -> SafeMinePlan.tunnel(safety, entry, direction, plannedSteps, torchInterval);
        };
    }

    private void planCheck() {
        if (stepIndex >= plan.steps().size()) {
            complete();
            return;
        }
        SafeMineStep step = plan.steps().get(stepIndex);
        for (BlockCoord cell : step.passage()) {
            if (reservations.foreign(cell, spirit.getUUID())) {
                stopReason = "клетка " + cell + " занята другим духом: проходка остановлена";
                complete();
                return;
            }
        }
        for (BlockCoord cell : step.passage()) {
            reservations.reserve(cell, spirit.getUUID());
        }
        cellCursor = 0;
        fillCursor = 0;
        fallWait = 0;
        approachBegun = false;
        fillBlock = null;
        fillQueue = fillQueue();
        state = fillQueue.isEmpty() ? State.DIG_BEGIN : State.FILL;
    }

    /**
     * Заделки текущего шага и шага впереди: жидкость у следующей ступени (особенно лава по диагонали)
     * делает стоянку текущего шага непроходимой для поиска пути, поэтому закладываем её заранее.
     */
    private List<BlockCoord> fillQueue() {
        Set<BlockCoord> cells = new LinkedHashSet<>(plan.steps().get(stepIndex).fill());
        if (stepIndex + 1 < plan.steps().size()) {
            cells.addAll(plan.steps().get(stepIndex + 1).fill());
        }
        return List.copyOf(cells);
    }

    private void fillTick() {
        List<BlockCoord> fill = fillQueue;
        while (fillCursor < fill.size()) {
            BlockCoord cell = fill.get(fillCursor);
            BlockPos pos = toPos(cell);
            if (level.getBlockState(pos).getFluidState().isEmpty()) {
                fillCursor++;
                continue;
            }
            if (fillBlock == null || BlockItems.countIn(spirit.getInventory(), fillBlock) < 1) {
                fillBlock = pickFillBlock();
                if (fillBlock == null) {
                    stopReason = "нет блока для заделки опасной клетки " + cell + ": нужен булыжник, земля или доски";
                    complete();
                    return;
                }
            }
            BlockPlacer.Begin begin = placer.begin(pos, BlockStates.stateFor(fillBlock), fillBlock);
            if (begin instanceof BlockPlacer.Started) {
                blocksFilled++;
                fillCursor++;
                return;
            }
            if (begin instanceof BlockPlacer.Protected protectedPlace) {
                stopReason = "ставить заделку запрещено: " + protectedPlace.reason();
                complete();
                return;
            }
            BlockPlacer.Refused refused = (BlockPlacer.Refused) begin;
            stopReason = "не удалось заложить клетку " + cell + ": " + refused.message();
            complete();
            return;
        }
        state = State.DIG_BEGIN;
    }

    private void digBegin() {
        List<BlockCoord> passage = currentStep().passage();
        while (cellCursor < passage.size()) {
            BlockCoord cell = passage.get(cellCursor);
            BlockPos pos = toPos(cell);
            if (level.getBlockState(pos).isAir() || level.getBlockState(pos).is(Blocks.TORCH)) {
                cellCursor++;
                continue;
            }
            BreakSafety safetyResult = MiningSafety.canBreak(cell, safety);
            if (safetyResult instanceof BreakSafety.Unsafe unsafe) {
                stopReason = unsafe.reason();
                complete();
                return;
            }
            BlockBreaker.Begin begin = breaker.begin(pos);
            if (begin instanceof BlockBreaker.Started) {
                state = State.BREAK;
                return;
            }
            if (begin instanceof BlockBreaker.Protected protectedBreak) {
                stopReason = "ломать запрещено: " + protectedBreak.reason();
                complete();
                return;
            }
            BlockBreaker.Refused refused = (BlockBreaker.Refused) begin;
            stopReason = refused.message();
            complete();
            return;
        }
        if (currentStep().fallingAbove()) {
            fallWait = 0;
            state = State.FALL_WAIT;
        } else {
            approachBegun = false;
            state = State.APPROACH;
        }
    }

    private void breakTick() {
        switch (breaker.tick()) {
            case BREAKING -> {
            }
            case BROKEN -> {
                blocksDug++;
                cellCursor++;
                state = State.DIG_BEGIN;
            }
            case GONE -> {
                cellCursor++;
                state = State.DIG_BEGIN;
            }
        }
    }

    private void fallWaitTick() {
        SafeMineStep step = currentStep();
        fallWait++;
        if (fallWait < FALL_SETTLE_TICKS) {
            return;
        }
        for (BlockCoord cell : step.passage()) {
            if (!level.getBlockState(toPos(cell)).isAir()) {
                cellCursor = 0;
                state = State.DIG_BEGIN;
                return;
            }
        }
        BlockCoord ceiling = new BlockCoord(step.stand().x(), step.floor() + step.passage().size() + 1,
                step.stand().z());
        if (safety.cellAt(ceiling) == Cell.FALLING || fallingEntityAbove(step)) {
            if (fallWait >= FALL_WAIT_TICKS) {
                stopReason = "сыпучий блок над проходом " + ceiling + " не осыпался за " + FALL_WAIT_TICKS
                        + " тиков: дух не лезет под него";
                complete();
            }
            return;
        }
        approachBegun = false;
        state = State.APPROACH;
    }

    private boolean fallingEntityAbove(SafeMineStep step) {
        AABB column = new AABB(step.stand().x(), step.stand().y() - 1, step.stand().z(),
                step.stand().x() + 1, step.stand().y() + 4, step.stand().z() + 1);
        return !level.getEntitiesOfClass(FallingBlockEntity.class, column).isEmpty();
    }

    private void collectTick() {
        DropCollector.State collected = collector.tick();
        if (collected == DropCollector.State.RUNNING) {
            return;
        }
        collector = null;
        if (currentStep().torch()) {
            state = State.TORCH;
        } else {
            stepFinished();
        }
    }

    private void approachTick() {
        SafeMineStep step = currentStep();
        if (!approachBegun) {
            BlockCoord stand = step.stand();
            approach.begin(new Vec3(stand.x() + 0.5, stand.y(), stand.z() + 0.5), APPROACH_ARRIVAL);
            approachBegun = true;
        }
        switch (approach.tick()) {
            case MOVING -> {
            }
            case ARRIVED -> {
                collector = new DropCollector(spirit, IdFilter.matchAll(), step.stand(), COLLECT_RADIUS,
                        PickupTuning.DEFAULT, OverflowMode.BY_VALUE);
                state = State.COLLECT;
            }
            case STUCK, TIMEOUT -> {
                String detail = approach.refusal() == null ? "стоянка недостижима" : approach.refusal();
                stopReason = "к ступени " + step.stand() + " не подойти: " + detail;
                complete();
            }
        }
    }

    private void torchTick() {
        BlockCoord stand = currentStep().stand();
        BlockPos standPos = toPos(stand);
        if (level.getBlockState(standPos).is(Blocks.TORCH)) {
            stepFinished();
            return;
        }
        BlockPlacer.Begin begin = placer.begin(standPos, Blocks.TORCH.defaultBlockState(), TORCH_BLOCK_ID);
        if (begin instanceof BlockPlacer.Started) {
            torchesPlaced++;
            stepFinished();
            return;
        }
        if (begin instanceof BlockPlacer.Protected protectedPlace) {
            stopReason = "ставить факел запрещено: " + protectedPlace.reason();
            complete();
            return;
        }
        BlockPlacer.Refused refused = (BlockPlacer.Refused) begin;
        stopReason = switch (refused.reason()) {
            case NO_MATERIAL -> "нет факелов в инвентаре: ступень " + stand + " осталась без света";
            default -> refused.message();
        };
        complete();
    }

    private void stepFinished() {
        for (BlockCoord cell : currentStep().passage()) {
            reservations.release(cell, spirit.getUUID());
        }
        stepIndex++;
        attemptNextStep();
    }

    private void attemptNextStep() {
        cellCursor = 0;
        fillCursor = 0;
        fallWait = 0;
        approachBegun = false;
        fillBlock = null;
        state = State.PLAN_CHECK;
    }

    private void complete() {
        reservations.releaseAll(spirit.getUUID());
        if (stopReason == null && plan.stop() == DescentStop.DANGER) {
            stopReason = plan.dangerReason();
        }
        outcome = Outcome.SUCCEEDED;
    }

    private Outcome fail(String reason) {
        failureReason = reason;
        outcome = Outcome.FAILED;
        cancel();
        return outcome;
    }

    private String pickFillBlock() {
        for (String id : FILL_BLOCKS) {
            if (BlockItems.countIn(spirit.getInventory(), id) >= 1) {
                return id;
            }
        }
        return null;
    }

    private SafeMineStep currentStep() {
        return plan.steps().get(stepIndex);
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }

    private static int number(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Поле снимка '" + key + "' отсутствует: без него проходку не восстановить");
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Поле снимка '" + key + "' обязано быть числом: иначе проходку не восстановить");
        }
        return number.intValue();
    }

    private static String text(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("Поле снимка '" + key + "' обязано быть строкой: иначе проходку не восстановить");
        }
        return text;
    }
}
