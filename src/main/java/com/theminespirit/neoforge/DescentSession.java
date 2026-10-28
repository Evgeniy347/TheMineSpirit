package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildReservations;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.mining.BreakSafety;
import com.theminespirit.core.mining.DescentData;
import com.theminespirit.core.mining.DescentDirection;
import com.theminespirit.core.mining.DescentPlan;
import com.theminespirit.core.mining.DescentStep;
import com.theminespirit.core.mining.DescentStop;
import com.theminespirit.core.mining.MineDigOrder;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.pickup.OverflowMode;
import com.theminespirit.core.pickup.PickupTuning;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class DescentSession {
    public enum Outcome {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    private enum State {
        PLAN_CHECK,
        DIG_BEGIN,
        BREAK,
        COLLECT,
        APPROACH,
        TORCH
    }

    private static final double APPROACH_ARRIVAL = 0.6;
    private static final int COLLECT_RADIUS = 4;
    private static final String TORCH_BLOCK_ID = "minecraft:torch";

    private final SpiritEntity spirit;
    private final Level level;
    private final LevelSafetyView safety;
    private final BlockBreaker breaker;
    private final BlockPlacer placer;
    private final Approach approach;
    private final BuildReservations reservations;

    private BlockCoord entry;
    private DescentDirection direction;
    private int targetY;
    private int torchInterval;
    private DescentPlan plan;
    private int stepIndex;
    private int blocksDug;
    private boolean approachBegun;

    private State state = State.PLAN_CHECK;
    private Outcome outcome = Outcome.RUNNING;
    private String failureReason;
    private String stopReason;
    private DropCollector collector;

    public DescentSession(SpiritEntity spirit, DescentDirection direction, int targetY) {
        this.spirit = Objects.requireNonNull(spirit, "Дух не может быть null: без него неизвестно, кто спускается");
        this.level = spirit.level();
        this.safety = new LevelSafetyView(level);
        this.breaker = new BlockBreaker(spirit);
        this.placer = new BlockPlacer(spirit);
        this.approach = new Approach(spirit);
        this.reservations = BuildReservationHub.forLevel(level);
        BlockPos feet = spirit.blockPosition();
        this.entry = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        this.direction = Objects.requireNonNull(
                direction, "Направление спуска не может быть null: без него неизвестно, куда копать");
        this.targetY = targetY;
        this.torchInterval = DescentData.loadDefault().torchInterval();
        this.plan = DescentPlan.from(safety, entry, direction, targetY, torchInterval);
    }

    public Outcome tick() {
        if (outcome != Outcome.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время спуска");
        }
        switch (state) {
            case PLAN_CHECK -> planCheck();
            case DIG_BEGIN -> digBegin();
            case BREAK -> breakTick();
            case COLLECT -> collectTick();
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
        int floorUnderFeet = entry.y() - 1;
        int y = stepIndex == 0
                ? floorUnderFeet
                : plan.steps().get(stepIndex - 1).floor();
        int depth = floorUnderFeet - y;
        return Map.of(
                "reached", stopReason == null,
                "y", y,
                "depth", depth,
                "blocks_dug", blocksDug,
                "reason", stopReason == null ? "" : stopReason);
    }

    public Map<String, Object> saveState() {
        Map<String, Object> map = new HashMap<>();
        map.put("entry_x", entry.x());
        map.put("entry_y", entry.y());
        map.put("entry_z", entry.z());
        map.put("direction", plan.direction().name().toLowerCase(Locale.ROOT));
        map.put("target_y", plan.targetY());
        map.put("step", stepIndex);
        map.put("blocks_dug", blocksDug);
        return Map.copyOf(map);
    }

    public void restoreState(Map<String, Object> saved) {
        Objects.requireNonNull(saved, "Снимок состояния не может быть null: пустое состояние задают пустой картой");
        entry = new BlockCoord(number(saved, "entry_x"), number(saved, "entry_y"), number(saved, "entry_z"));
        direction = DescentDirection.parse(text(saved, "direction"));
        targetY = number(saved, "target_y");
        blocksDug = number(saved, "blocks_dug");
        torchInterval = DescentData.loadDefault().torchInterval();
        plan = DescentPlan.from(safety, entry, direction, targetY, torchInterval);
        stepIndex = Math.min(number(saved, "step"), plan.steps().size());
        approachBegun = false;
        state = State.PLAN_CHECK;
    }

    private void planCheck() {
        if (stepIndex >= plan.steps().size()) {
            complete();
            return;
        }
        DescentStep step = plan.steps().get(stepIndex);
        String danger = DescentPlan.dangerAt(safety, step, plan.direction());
        if (danger != null) {
            stopReason = danger;
            complete();
            return;
        }
        for (BlockCoord cell : step.passage()) {
            if (reservations.foreign(cell, spirit.getUUID())) {
                stopReason = "клетка " + cell + " занята другим духом: спуск остановлен";
                complete();
                return;
            }
        }
        for (BlockCoord cell : step.passage()) {
            reservations.reserve(cell, spirit.getUUID());
        }
        state = State.DIG_BEGIN;
    }

    private void digBegin() {
        BlockCoord feet = new BlockCoord(spirit.getBlockX(), spirit.getBlockY(), spirit.getBlockZ());
        for (BlockCoord cell : MineDigOrder.from(feet, currentStep().passage(), safety)) {
            BlockPos pos = toPos(cell);
            if (level.getBlockState(pos).isAir()) {
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
        approachBegun = false;
        state = State.APPROACH;
    }

    private void breakTick() {
        switch (breaker.tick()) {
            case BREAKING -> {}
            case BROKEN -> {
                blocksDug++;
                state = State.DIG_BEGIN;
            }
            case GONE -> {
                state = State.DIG_BEGIN;
            }
        }
    }

    private void collectTick() {
        DropCollector.State collected = collector.tick();
        if (collected == DropCollector.State.RUNNING) {
            return;
        }
        collector = null;
        if (stepIndex % plan.torchInterval() == 0) {
            state = State.TORCH;
        } else {
            stepFinished();
        }
    }

    private void approachTick() {
        DescentStep step = currentStep();
        if (!approachBegun) {
            BlockCoord stand = step.stand();
            approach.begin(new Vec3(stand.x() + 0.5, stand.y(), stand.z() + 0.5), APPROACH_ARRIVAL);
            approachBegun = true;
        }
        switch (approach.tick()) {
            case MOVING -> {}
            case ARRIVED -> {
                collector = new DropCollector(
                        spirit,
                        IdFilter.matchAll(),
                        step.stand(),
                        COLLECT_RADIUS,
                        PickupTuning.DEFAULT,
                        OverflowMode.BY_VALUE);
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
            case OCCUPIED -> "клетка ступени " + stand + " занята другим блоком: факел не поставлен";
            default -> refused.message();
        };
        complete();
    }

    private void stepFinished() {
        for (BlockCoord cell : currentStep().passage()) {
            reservations.release(cell, spirit.getUUID());
        }
        stepIndex++;
        approachBegun = false;
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

    private DescentStep currentStep() {
        return plan.steps().get(stepIndex);
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }

    private static int number(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Поле снимка '" + key + "' отсутствует: без него спуск не восстановить");
        }
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(
                    "Поле снимка '" + key + "' обязано быть числом: иначе спуск не восстановить");
        }
        return number.intValue();
    }

    private static String text(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(
                    "Поле снимка '" + key + "' обязано быть строкой: иначе спуск не восстановить");
        }
        return text;
    }
}
