package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildReservations;
import com.theminespirit.core.mining.BreakSafety;
import com.theminespirit.core.mining.MineDigOrder;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.mining.SafeMinePlan;
import com.theminespirit.core.mining.SafeMineStep;
import com.theminespirit.core.observation.BlockCoord;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

final class SafeMineStepRun {
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

    private static final String TORCH_BLOCK_ID = "minecraft:torch";

    private final SpiritEntity spirit;
    private final Level level;
    private final LevelSafetyView safety;
    private final BlockBreaker breaker;
    private final BlockPlacer placer;
    private final BuildReservations reservations;
    private final SafeMineFill fill;
    private final SafeMineTravel travel;

    private SafeMinePlan plan;
    private int stepIndex;
    private int blocksDug;
    private int torchesPlaced;
    private boolean finished;
    private String stopReason;
    private State state = State.PLAN_CHECK;

    SafeMineStepRun(
            SpiritEntity spirit,
            Level level,
            LevelSafetyView safety,
            BlockBreaker breaker,
            BlockPlacer placer,
            Approach approach,
            BuildReservations reservations,
            SafeMinePlan plan) {
        this.spirit = spirit;
        this.level = level;
        this.safety = safety;
        this.breaker = breaker;
        this.placer = placer;
        this.reservations = reservations;
        this.plan = plan;
        this.fill = new SafeMineFill(spirit, level, placer);
        this.travel = new SafeMineTravel(spirit, level, safety, approach);
    }

    SafeMinePlan plan() {
        return plan;
    }

    int stepIndex() {
        return stepIndex;
    }

    int blocksDug() {
        return blocksDug;
    }

    int blocksFilled() {
        return fill.filled();
    }

    int torchesPlaced() {
        return torchesPlaced;
    }

    String stopReason() {
        return stopReason;
    }

    void restore(SafeMinePlan plan, int step, int dug, int filled, int torches) {
        this.plan = plan;
        blocksDug = dug;
        fill.filled(filled);
        torchesPlaced = torches;
        stepIndex = Math.min(step, plan.steps().size());
        fill.resetCursor();
        travel.resetStep();
        state = State.PLAN_CHECK;
    }

    boolean tick() {
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
        return finished;
    }

    void cancel() {
        travel.cancel();
    }

    void suspend() {
        travel.suspend();
    }

    private void planCheck() {
        if (stepIndex >= plan.steps().size()) {
            finish();
            return;
        }
        SafeMineStep step = plan.steps().get(stepIndex);
        for (BlockCoord cell : step.passage()) {
            if (reservations.foreign(cell, spirit.getUUID())) {
                stopReason = "клетка " + cell + " занята другим духом: проходка остановлена";
                finish();
                return;
            }
        }
        for (BlockCoord cell : step.passage()) {
            reservations.reserve(cell, spirit.getUUID());
        }
        travel.resetStep();
        fill.prepare(plan, stepIndex);
        state = fill.isEmpty() ? State.DIG_BEGIN : State.FILL;
    }

    private void fillTick() {
        SafeMineFill.Result result = fill.tick();
        if (result == SafeMineFill.Result.FAILED) {
            stopReason = fill.failure();
            finish();
            return;
        }
        if (result == SafeMineFill.Result.DONE) {
            state = State.DIG_BEGIN;
        }
    }

    private void digBegin() {
        BlockCoord feet = new BlockCoord(spirit.getBlockX(), spirit.getBlockY(), spirit.getBlockZ());
        for (BlockCoord cell : MineDigOrder.from(feet, currentStep().passage(), safety)) {
            BlockPos pos = toPos(cell);
            if (level.getBlockState(pos).isAir() || level.getBlockState(pos).is(Blocks.TORCH)) {
                continue;
            }
            BreakSafety safetyResult = MiningSafety.canBreak(cell, safety);
            if (safetyResult instanceof BreakSafety.Unsafe unsafe) {
                stopReason = unsafe.reason();
                finish();
                return;
            }
            BlockBreaker.Begin begin = breaker.beginExcavate(pos);
            if (begin instanceof BlockBreaker.Started) {
                state = State.BREAK;
                return;
            }
            if (begin instanceof BlockBreaker.Protected protectedBreak) {
                stopReason = "ломать запрещено: " + protectedBreak.reason();
                finish();
                return;
            }
            BlockBreaker.Refused refused = (BlockBreaker.Refused) begin;
            stopReason = refused.message();
            finish();
            return;
        }
        if (currentStep().fallingAbove()) {
            travel.beginFall();
            state = State.FALL_WAIT;
        } else {
            travel.beginApproach();
            state = State.APPROACH;
        }
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

    private void fallWaitTick() {
        switch (travel.fallTick(currentStep())) {
            case DIG_BEGIN -> state = State.DIG_BEGIN;
            case APPROACH -> state = State.APPROACH;
            case FAILED -> {
                stopReason = travel.failure();
                finish();
            }
            default -> {}
        }
    }

    private void collectTick() {
        switch (travel.collectTick(currentStep())) {
            case TORCH -> state = State.TORCH;
            case STEP_FINISHED -> stepFinished();
            default -> {}
        }
    }

    private void approachTick() {
        switch (travel.approachTick(currentStep())) {
            case COLLECT -> state = State.COLLECT;
            case FAILED -> {
                stopReason = travel.failure();
                finish();
            }
            default -> {}
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
            finish();
            return;
        }
        BlockPlacer.Refused refused = (BlockPlacer.Refused) begin;
        stopReason = switch (refused.reason()) {
            case NO_MATERIAL -> "нет факелов в инвентаре: ступень " + stand + " осталась без света";
            default -> refused.message();
        };
        finish();
    }

    private void stepFinished() {
        for (BlockCoord cell : currentStep().passage()) {
            reservations.release(cell, spirit.getUUID());
        }
        stepIndex++;
        attemptNextStep();
    }

    private void attemptNextStep() {
        fill.resetCursor();
        travel.resetStep();
        state = State.PLAN_CHECK;
    }

    private void finish() {
        finished = true;
    }

    private SafeMineStep currentStep() {
        return plan.steps().get(stepIndex);
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}
