package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildBox;
import com.theminespirit.core.building.BuildReservations;
import com.theminespirit.core.building.BuildResult;
import com.theminespirit.core.building.BuildState;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.mining.ApproachFailureTracker;
import com.theminespirit.core.mining.BreakSafety;
import com.theminespirit.core.mining.MiningPlanner;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.pickup.OverflowMode;
import com.theminespirit.core.pickup.PickupTuning;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class ClearAreaSession {
    public enum Outcome {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    protected enum State {
        PICK,
        APPROACH,
        BREAK,
        COLLECT
    }

    private static final double APPROACH_ARRIVAL = 0.6;
    private static final int COLLECT_RADIUS = 4;

    protected final SpiritEntity spirit;
    protected final Level level;
    protected final BuildBox box;
    protected final List<BlockCoord> remaining;
    protected final LevelSafetyView safety;
    protected final BlockBreaker breaker;
    protected final Approach approach;
    protected final BuildReservations reservations;

    protected State state = State.PICK;
    protected Outcome outcome = Outcome.RUNNING;
    protected String failureReason;
    protected BlockCoord currentTarget;
    protected BlockCoord currentStand;
    protected final ApproachFailureTracker approachFailures = new ApproachFailureTracker();
    protected DropCollector collector;

    protected int removed;
    protected int unsafe;
    protected int noStand;
    protected int unreachable;
    protected int protectedSkipped;
    protected int foreignSkipped;
    protected int unbreakable;
    protected int restoredSkipped;

    private final BuildResult tally = new BuildResult();
    private final boolean demolish;

    public ClearAreaSession(SpiritEntity spirit, BuildBox box) {
        this(spirit, box, false);
    }

    public ClearAreaSession(SpiritEntity spirit, BuildBox box, boolean demolish) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.box = box;
        this.remaining = new ArrayList<>(box.cellsBottomUp());
        this.safety = new LevelSafetyView(level);
        this.breaker = new BlockBreaker(spirit);
        this.approach = new Approach(spirit);
        this.reservations = BuildReservationHub.forLevel(level);
        this.demolish = demolish;
    }

    public Outcome tick() {
        if (outcome != Outcome.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время расчистки");
        }
        switch (state) {
            case PICK -> pick();
            case APPROACH -> approachTick();
            case BREAK -> breakTick();
            case COLLECT -> collectTick();
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
        int total = Math.max(boxVolume(), 1);
        return new ScriptInstance.Progress(Math.min(removed + skippedTotal(), total), total);
    }

    public int removed() {
        return removed;
    }

    public int skippedTotal() {
        return unsafe + noStand + unreachable + protectedSkipped + foreignSkipped + unbreakable + restoredSkipped;
    }

    public Map<String, Object> resultData() {
        return tally.toData();
    }

    public Map<String, Object> saveState() {
        return new BuildState(0, removed, skippedTotal(), 0, outcome == Outcome.RUNNING).toMap();
    }

    public void restoreState(Map<String, Object> saved) {
        BuildState state = BuildState.fromMap(saved);
        removed = Math.max(removed, state.removed());
        restoredSkipped = Math.max(restoredSkipped, state.skipped());
        for (int i = 0; i < state.removed(); i++) {
            tally.removed();
        }
        for (int i = 0; i < state.skipped(); i++) {
            tally.skipped();
        }
        for (int i = 0; i < state.failed(); i++) {
            tally.failed();
        }
    }

    protected int boxVolume() {
        return (int) Math.min(box.volume(), Integer.MAX_VALUE);
    }

    private void pick() {
        while (!remaining.isEmpty()
                && level.getBlockState(toPos(remaining.get(0))).isAir()) {
            remaining.remove(0);
        }
        if (remaining.isEmpty()) {
            finish();
            return;
        }
        BlockPos feet = spirit.blockPosition();
        BlockCoord from = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        MiningPlanner.Outcome result = MiningPlanner.next(from, remaining, safety, approachFailures.excludedStands());
        if (result instanceof MiningPlanner.Exhausted exhausted) {
            unsafe += exhausted.unsafe();
            noStand += exhausted.noStand();
            unreachable += exhausted.unreachable();
            noteSkipped(exhausted.unsafe() + exhausted.noStand() + exhausted.unreachable());
            remaining.clear();
            finish();
            return;
        }
        MiningPlanner.Next next = (MiningPlanner.Next) result;
        remaining.remove(next.target());
        if (reservations.foreign(next.target(), spirit.getUUID())) {
            foreignSkipped++;
            tally.skipped();
            return;
        }
        reservations.reserve(next.target(), spirit.getUUID());
        currentTarget = next.target();
        currentStand = next.stand();
        approach.begin(
                new Vec3(next.stand().x() + 0.5, next.stand().y(), next.stand().z() + 0.5), APPROACH_ARRIVAL);
        state = State.APPROACH;
    }

    private void approachTick() {
        switch (approach.tick()) {
            case MOVING -> {}
            case ARRIVED -> prepareBreak();
            case STUCK, TIMEOUT -> {
                if (approachFailures.recordFailure(currentStand, currentTarget)) {
                    unreachable++;
                    tally.skipped();
                    releaseCurrent();
                } else {
                    BlockCoord retry = currentTarget;
                    releaseCurrent();
                    remaining.add(0, retry);
                }
                state = State.PICK;
            }
        }
    }

    private void prepareBreak() {
        if (!(MiningSafety.canBreak(currentTarget, safety) instanceof BreakSafety.Safe)) {
            unsafe++;
            tally.skipped();
            releaseCurrent();
            state = State.PICK;
            return;
        }
        BlockBreaker.Begin begin = breaker.begin(toPos(currentTarget), false, demolish);
        if (begin instanceof BlockBreaker.Protected) {
            protectedSkipped++;
            tally.skipped();
            releaseCurrent();
            state = State.PICK;
            return;
        }
        if (begin instanceof BlockBreaker.Refused refused && refused.reason() == BlockBreaker.RefuseReason.NEED_TOOL) {
            fail(refused.message());
            return;
        }
        if (begin instanceof BlockBreaker.Refused) {
            unbreakable++;
            tally.skipped();
            releaseCurrent();
            state = State.PICK;
            return;
        }
        state = State.BREAK;
    }

    private void breakTick() {
        switch (breaker.tick()) {
            case BREAKING -> {}
            case BROKEN -> {
                removed++;
                tally.removed();
                collector = new DropCollector(
                        spirit,
                        IdFilter.matchAll(),
                        currentTarget,
                        COLLECT_RADIUS,
                        PickupTuning.DEFAULT,
                        OverflowMode.BY_VALUE);
                state = State.COLLECT;
            }
            case GONE -> {
                releaseCurrent();
                state = State.PICK;
            }
        }
    }

    private void collectTick() {
        DropCollector.State collected = collector.tick();
        if (collected == DropCollector.State.RUNNING) {
            return;
        }
        collector = null;
        if (collected == DropCollector.State.FAILED) {
            fail("не удалось подобрать дроп у " + currentTarget + ": расчистка остановлена");
            return;
        }
        releaseCurrent();
        state = State.PICK;
    }

    private void finish() {
        reservations.releaseAll(spirit.getUUID());
        if (outcome != Outcome.RUNNING) {
            return;
        }
        if (removed == 0) {
            fail(summary() + "; не убран ни один блок");
            return;
        }
        outcome = Outcome.SUCCEEDED;
    }

    protected Outcome fail(String reason) {
        failureReason = reason;
        outcome = Outcome.FAILED;
        tally.failed();
        cancel();
        return outcome;
    }

    private String summary() {
        String counts = "убрано " + removed + " из " + boxVolume() + "; пропущено: " + skippedTotal()
                + " (небезопасных: " + unsafe
                + ", без стоянки: " + noStand + ", защищено: " + protectedSkipped + ", неразрушимых: " + unbreakable
                + ", занято другим духом: " + foreignSkipped + ")";
        if (unreachable > 0) {
            counts += "; недостижимо: " + unreachable;
        }
        return counts;
    }

    private void releaseCurrent() {
        if (currentTarget != null) {
            reservations.release(currentTarget, spirit.getUUID());
            currentTarget = null;
        }
        currentStand = null;
    }

    private void noteSkipped(int count) {
        for (int i = 0; i < count; i++) {
            tally.skipped();
        }
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}
