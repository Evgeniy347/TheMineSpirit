package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildReservations;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.mining.DescentStop;
import com.theminespirit.core.mining.SafeMinePlan;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.SafeMineParams;
import com.theminespirit.core.town.PlotProtection;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class SafeMineSession {
    public enum Outcome {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    private final SpiritEntity spirit;
    private final Level level;
    private final LevelSafetyView safety;
    private final BlockBreaker breaker;
    private final BlockPlacer placer;
    private final Approach approach;
    private final BuildReservations reservations;
    private final SafeMineStepRun run;

    private Outcome outcome = Outcome.RUNNING;
    private String failureReason;
    private String stopReason;

    public SafeMineSession(SpiritEntity spirit, SafeMineParams params) {
        this.spirit = Objects.requireNonNull(spirit, "Дух не может быть null: без него неизвестно, кто копает");
        Objects.requireNonNull(params, "Параметры не могут быть null: без них неизвестна форма шахты");
        this.level = spirit.level();
        this.safety = new LevelSafetyView(level);
        this.breaker = new BlockBreaker(spirit);
        this.placer = new BlockPlacer(spirit, PlotProtection.Ground.PLAN_BUILD);
        this.approach = new Approach(spirit);
        this.reservations = BuildReservationHub.forLevel(level);
        BlockPos feet = spirit.blockPosition();
        BlockCoord entry = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        SafeMinePlan plan = switch (params.mode()) {
            case STAIRCASE ->
                SafeMinePlan.staircase(safety, entry, params.direction(), params.y(), params.torchInterval());
            case TUNNEL ->
                SafeMinePlan.tunnel(safety, entry, params.direction(), params.length(), params.torchInterval());
        };
        this.run = new SafeMineStepRun(
                new SafeMineStepRun.World(spirit, level, safety, breaker), placer, approach, reservations, plan);
    }

    public Outcome tick() {
        if (outcome != Outcome.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время проходки");
        }
        if (run.tick()) {
            stopReason = run.stopReason();
            complete();
        }
        return outcome;
    }

    public void cancel() {
        breaker.cancel();
        approach.cancel();
        run.cancel();
        reservations.releaseAll(spirit.getUUID());
    }

    public void suspend() {
        approach.suspend();
        run.suspend();
    }

    public Outcome outcome() {
        return outcome;
    }

    public String failureReason() {
        return failureReason;
    }

    public ScriptInstance.Progress progress() {
        return new ScriptInstance.Progress(
                run.stepIndex(), Math.max(run.plan().steps().size(), 1));
    }

    public Map<String, Object> resultData() {
        SafeMinePlan plan = run.plan();
        int entryFloor = plan.entry().y() - 1;
        int stepIndex = run.stepIndex();
        int y = stepIndex == 0 ? entryFloor : plan.steps().get(stepIndex - 1).floor();
        return Map.of(
                "reached",
                stopReason == null && plan.stop() == DescentStop.TARGET_Y,
                "y",
                y,
                "depth",
                entryFloor - y,
                "blocks_dug",
                run.blocksDug(),
                "blocks_filled",
                run.blocksFilled(),
                "torches",
                run.torchesPlaced(),
                "reason",
                stopReason == null ? plan.dangerReason() : stopReason);
    }

    public Map<String, Object> saveState() {
        return SafeMineState.save(
                run.plan(), run.stepIndex(), run.blocksDug(), run.blocksFilled(), run.torchesPlaced());
    }

    public void restoreState(Map<String, Object> saved) {
        SafeMineState.Restored restored = SafeMineState.restore(saved);
        run.restore(rebuild(restored), restored.step(), restored.dug(), restored.filled(), restored.torches());
    }

    private SafeMinePlan rebuild(SafeMineState.Restored restored) {
        return switch (restored.mode()) {
            case STAIRCASE ->
                SafeMinePlan.staircase(
                        safety,
                        restored.entry(),
                        restored.direction(),
                        restored.entry().y() - restored.plannedSteps(),
                        restored.torchInterval());
            case TUNNEL ->
                SafeMinePlan.tunnel(
                        safety,
                        restored.entry(),
                        restored.direction(),
                        restored.plannedSteps(),
                        restored.torchInterval());
        };
    }

    private void complete() {
        reservations.releaseAll(spirit.getUUID());
        SafeMinePlan plan = run.plan();
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
}
