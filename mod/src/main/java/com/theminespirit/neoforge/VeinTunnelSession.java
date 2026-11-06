package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.mining.BreakSafety;
import com.theminespirit.core.mining.MineTunnel;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

final class VeinTunnelSession {
    enum Outcome {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    private enum State {
        NEXT,
        DIG,
        BREAK,
        APPROACH
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double APPROACH_ARRIVAL = 0.6;
    private static final int STEP_LIMIT_TICKS = 400;

    private final SpiritEntity spirit;
    private final Level level;
    private final LevelSafetyView safety;
    private final BlockBreaker breaker;
    private final Approach approach;
    private final BlockCoord target;
    private final List<MineTunnel.Step> steps;

    private State state = State.NEXT;
    private Outcome outcome = Outcome.RUNNING;
    private String failureReason;
    private int index;
    private int stepTicks;
    private List<BlockCoord> pending = List.of();
    private int pendingIndex;

    private VeinTunnelSession(
            SpiritEntity spirit, LevelSafetyView safety, BlockCoord target, List<MineTunnel.Step> steps) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.safety = safety;
        this.breaker = new BlockBreaker(spirit);
        this.approach = new Approach(spirit);
        this.target = target;
        this.steps = steps;
    }

    static VeinTunnelSession start(
            SpiritEntity spirit, BlockCoord target, MineTunnel.Plan plan, LevelSafetyView safety) {
        if (!plan.found()) {
            LOGGER.info(
                    "[mine] {}: проход к руде {} не найден — {}", spirit.displayName(), coord(target), plan.refusal());
            return null;
        }
        LOGGER.info(
                "[mine] {}: выбранная руда {} закрыта — проход {} шагов: {}",
                spirit.displayName(),
                coord(target),
                plan.steps().size(),
                pathText(plan.steps()));
        return new VeinTunnelSession(spirit, safety, target, plan.steps());
    }

    Outcome tick() {
        if (outcome != Outcome.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время прохода к руде " + coord(target));
        }
        if (index >= steps.size()) {
            outcome = Outcome.SUCCEEDED;
            LOGGER.info(
                    "[mine] {}: проход к руде {} пройден за {} шагов",
                    spirit.displayName(),
                    coord(target),
                    steps.size());
            return outcome;
        }
        stepTicks++;
        if (stepTicks > STEP_LIMIT_TICKS) {
            return fail(
                    "шаг прохода " + coord(steps.get(index).stand()) + " не пройден за " + STEP_LIMIT_TICKS + " тиков");
        }
        switch (state) {
            case NEXT -> beginStep();
            case DIG -> digTick();
            case BREAK -> breakTick();
            case APPROACH -> approachTick();
        }
        return outcome;
    }

    void cancel() {
        breaker.cancel();
        approach.cancel();
    }

    void suspend() {
        approach.suspend();
    }

    String failureReason() {
        return failureReason;
    }

    private void beginStep() {
        List<BlockCoord> dig = new ArrayList<>(steps.get(index).dig());
        dig.sort(Comparator.comparingInt(BlockCoord::y).reversed());
        pending = List.copyOf(dig);
        pendingIndex = 0;
        stepTicks = 0;
        state = State.DIG;
    }

    private void digTick() {
        MineTunnel.Step step = steps.get(index);
        while (pendingIndex < pending.size()) {
            BlockCoord cell = pending.get(pendingIndex);
            BlockPos pos = toPos(cell);
            BlockState current = level.getBlockState(pos);
            if (current.isAir() || current.canBeReplaced()) {
                pendingIndex++;
                continue;
            }
            if (!(MiningSafety.canBreak(cell, safety) instanceof BreakSafety.Safe)) {
                fail("ломать " + coord(cell) + " на пути к руде небезопасно: рядом жидкость или опасный блок");
                return;
            }
            BlockBreaker.Begin begin = breaker.begin(pos);
            if (begin instanceof BlockBreaker.Started) {
                state = State.BREAK;
                return;
            }
            if (begin instanceof BlockBreaker.Protected protectedBreak) {
                fail("ломать " + coord(cell) + " на пути к руде запрещено: " + protectedBreak.reason());
                return;
            }
            BlockBreaker.Refused refused = (BlockBreaker.Refused) begin;
            fail("не удалось пробить " + coord(cell) + " на пути к руде: " + refused.message());
            return;
        }
        approach.begin(
                new Vec3(step.stand().x() + 0.5, step.stand().y(), step.stand().z() + 0.5), APPROACH_ARRIVAL);
        state = State.APPROACH;
    }

    private void breakTick() {
        switch (breaker.tick()) {
            case BREAKING -> {}
            case BROKEN, GONE -> state = State.DIG;
        }
    }

    private void approachTick() {
        MineTunnel.Step step = steps.get(index);
        switch (approach.tick()) {
            case MOVING -> {}
            case ARRIVED -> {
                index++;
                state = State.NEXT;
            }
            case STUCK, TIMEOUT -> {
                String detail = approach.refusal() == null ? "путь не найден" : approach.refusal();
                fail("к стоянке " + coord(step.stand()) + " на пути к руде не подойти: " + detail);
            }
        }
    }

    private Outcome fail(String reason) {
        outcome = Outcome.FAILED;
        failureReason = reason;
        LOGGER.info("[mine] {}: проход к руде {} прерван — {}", spirit.displayName(), coord(target), reason);
        cancel();
        return outcome;
    }

    private static String pathText(List<MineTunnel.Step> steps) {
        StringBuilder text = new StringBuilder();
        for (MineTunnel.Step step : steps) {
            if (!text.isEmpty()) {
                text.append(" → ");
            }
            text.append(coord(step.stand()));
        }
        return text.toString();
    }

    private static String coord(BlockCoord coord) {
        return "(" + coord.x() + ", " + coord.y() + ", " + coord.z() + ")";
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}
