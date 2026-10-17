package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildReservations;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.mining.BreakSafety;
import com.theminespirit.core.mining.DescentData;
import com.theminespirit.core.mining.DescentDirection;
import com.theminespirit.core.mining.DescentLevels;
import com.theminespirit.core.mining.DescentPlan;
import com.theminespirit.core.mining.DescentStop;
import com.theminespirit.core.mining.DescentWidenPlan;
import com.theminespirit.core.mining.DescentWidenStep;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class DescentWidenSession {
    public enum Outcome {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    private enum State {
        APPROACH,
        DIG,
        BREAK
    }

    private static final double APPROACH_ARRIVAL = 0.6;

    private final SpiritEntity spirit;
    private final Level level;
    private final LevelSafetyView safety;
    private final BlockBreaker breaker;
    private final Approach approach;
    private final BuildReservations reservations;

    private BlockCoord entry;
    private DescentDirection direction;
    private int targetY;
    private DescentWidenPlan plan;
    private List<List<BlockCoord>> cells;
    private int entryFloor;

    private int stepIndex;
    private int cellCursor;
    private boolean approachBegun;
    private int blocksDug;
    private State state = State.APPROACH;
    private Outcome outcome = Outcome.RUNNING;
    private String failureReason;
    private String stopReason;

    public DescentWidenSession(SpiritEntity spirit, DescentDirection direction, int targetY, DescentLevels levels) {
        this.spirit = Objects.requireNonNull(spirit, "Дух не может быть null: без него неизвестно, кто расширяет");
        this.level = spirit.level();
        this.safety = new LevelSafetyView(level);
        this.breaker = new BlockBreaker(spirit);
        this.approach = new Approach(spirit);
        this.reservations = BuildReservationHub.forLevel(level);
        BlockPos feet = spirit.blockPosition();
        this.entry = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        this.direction = Objects.requireNonNull(direction,
                "Направление расширения не может быть null: без него неизвестно, куда копать");
        this.targetY = targetY;
        rebuild(levels);
    }

    public Outcome tick() {
        if (outcome != Outcome.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время расширения спуска");
        }
        switch (state) {
            case APPROACH -> approachTick();
            case DIG -> digTick();
            case BREAK -> breakTick();
        }
        return outcome;
    }

    public void cancel() {
        breaker.cancel();
        approach.cancel();
        reservations.releaseAll(spirit.getUUID());
    }

    public void suspend() {
        approach.suspend();
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
        int y = stepIndex == 0 ? entryFloor : plan.steps().get(stepIndex - 1).floor();
        return Map.of(
                "reached", stopReason == null && plan.stop() == DescentStop.TARGET_Y,
                "y", y,
                "depth", entryFloor - y,
                "blocks_dug", blocksDug,
                "reason", stopReason == null ? plan.dangerReason() : stopReason);
    }

    public Map<String, Object> saveState() {
        Map<String, Object> map = new HashMap<>();
        map.put("entry_x", entry.x());
        map.put("entry_y", entry.y());
        map.put("entry_z", entry.z());
        map.put("direction", direction.name().toLowerCase(Locale.ROOT));
        map.put("target_y", targetY);
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
        rebuild(DescentLevels.loadDefault());
        stepIndex = Math.min(number(saved, "step"), plan.steps().size());
        cellCursor = 0;
        approachBegun = false;
        state = State.APPROACH;
    }

    private void rebuild(DescentLevels levels) {
        DescentPlan level1 = DescentPlan.from(safety, entry, direction, targetY,
                DescentData.loadDefault().torchInterval());
        plan = DescentWidenPlan.from(safety, level1, levels.widenWidth(), levels.widenHeight());
        entryFloor = entry.y() - 1;
        List<List<BlockCoord>> perStep = new ArrayList<>();
        for (DescentWidenStep step : plan.steps()) {
            List<BlockCoord> solid = new ArrayList<>();
            for (BlockCoord cell : step.cells()) {
                BlockState stateAt = level.getBlockState(toPos(cell));
                if (!stateAt.isAir() && !stateAt.is(Blocks.TORCH)) {
                    solid.add(cell);
                }
            }
            perStep.add(List.copyOf(solid));
        }
        cells = List.copyOf(perStep);
    }

    private void approachTick() {
        if (stepIndex >= plan.steps().size()) {
            complete();
            return;
        }
        DescentWidenStep step = plan.steps().get(stepIndex);
        BlockCoord stand = step.stand();
        if (!approachBegun) {
            approach.begin(new Vec3(stand.x() + 0.5, stand.y(), stand.z() + 0.5), APPROACH_ARRIVAL);
            approachBegun = true;
        }
        switch (approach.tick()) {
            case MOVING -> {
            }
            case ARRIVED -> {
                approachBegun = false;
                cellCursor = 0;
                state = State.DIG;
            }
            case STUCK, TIMEOUT -> {
                String detail = approach.refusal() == null ? "стоянка недостижима" : approach.refusal();
                fail("к ступени " + stand + " не подойти: " + detail);
            }
        }
    }

    private void digTick() {
        List<BlockCoord> stepCells = cells.get(stepIndex);
        while (cellCursor < stepCells.size()) {
            BlockCoord cell = stepCells.get(cellCursor);
            BlockPos pos = toPos(cell);
            if (level.getBlockState(pos).isAir()) {
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
                fail("ломать запрещено: " + protectedBreak.reason());
                return;
            }
            BlockBreaker.Refused refused = (BlockBreaker.Refused) begin;
            fail(refused.message());
            return;
        }
        stepIndex++;
        cellCursor = 0;
        approachBegun = false;
        state = State.APPROACH;
    }

    private void breakTick() {
        switch (breaker.tick()) {
            case BREAKING -> {
            }
            case BROKEN -> {
                blocksDug++;
                cellCursor++;
                state = State.DIG;
            }
            case GONE -> {
                cellCursor++;
                state = State.DIG;
            }
        }
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

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }

    private static int number(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("Поле снимка '" + key + "' обязано быть числом: иначе расширение не восстановить");
        }
        return number.intValue();
    }

    private static String text(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("Поле снимка '" + key + "' обязано быть строкой: иначе расширение не восстановить");
        }
        return text;
    }
}
