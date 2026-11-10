package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.BuildBox;
import com.theminespirit.core.building.BuildReservations;
import com.theminespirit.core.building.BuildResult;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.core.mining.ApproachFailureTracker;
import com.theminespirit.core.mining.BreakSkipTally;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

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

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int LEFTOVER_SHOWN = 4;

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
    protected int restoredSkipped;
    protected int excludedSkipped;
    protected final BreakSkipTally breakSkips = new BreakSkipTally();
    protected List<ClearSummary.Leftover> leftover = List.of();

    final BuildResult tally = new BuildResult();
    final boolean demolish;
    final boolean keepGround;
    final ClearBlocked blocked;
    final TreeFelling felling;
    private final Map<State, Integer> phaseTicks = new EnumMap<>(State.class);
    private ClearAreaRun run;

    public ClearAreaSession(SpiritEntity spirit, BuildBox box) {
        this(spirit, box, false);
    }

    public ClearAreaSession(SpiritEntity spirit, BuildBox box, boolean demolish) {
        this(spirit, box, demolish, false);
    }

    public ClearAreaSession(SpiritEntity spirit, BuildBox box, boolean demolish, boolean keepGround) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.box = box;
        this.remaining = new ArrayList<>(box.cellsBottomUp());
        this.safety = new LevelSafetyView(level);
        this.breaker = new BlockBreaker(spirit);
        this.approach = new Approach(spirit);
        this.reservations = BuildReservationHub.forLevel(level);
        this.demolish = demolish;
        this.keepGround = keepGround;
        this.blocked = new ClearBlocked(level, box);
        this.felling = new TreeFelling(new LevelTreeView(level, TreeBlocks.logs(TreeSpecies.ALL)));
        this.felling.extend(remaining);
    }

    private ClearAreaRun runner() {
        if (run == null) {
            run = new ClearAreaRun(this);
        }
        return run;
    }

    public Outcome tick() {
        if (outcome != Outcome.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) return fail("дух исчез во время расчистки");
        phaseTicks.merge(state, 1, Integer::sum);
        switch (state) {
            case PICK -> runner().pick();
            case APPROACH -> runner().approachTick();
            case BREAK -> runner().breakTick();
            case COLLECT -> collectTick();
        }
        return outcome;
    }

    public void cancel() {
        breaker.cancel();
        approach.cancel();
        if (collector != null) collector.cancel();
        reservations.releaseAll(spirit.getUUID());
    }

    public void suspend() {
        approach.suspend();
        if (collector != null) collector.suspend();
    }

    public boolean picking() {
        return state == State.PICK && outcome == Outcome.RUNNING;
    }

    public Outcome outcome() {
        return outcome;
    }

    public String failureReason() {
        return failureReason;
    }

    public ScriptInstance.Progress progress() {
        int total = Math.max(ClearSummary.volume(box), 1);
        return new ScriptInstance.Progress(Math.min(removed + skippedTotal(), total), total);
    }

    public int removed() {
        return removed;
    }

    public int skippedTotal() {
        return unsafe
                + noStand
                + unreachable
                + protectedSkipped
                + foreignSkipped
                + restoredSkipped
                + excludedSkipped
                + breakSkips.total();
    }

    public Map<String, Object> resultData() {
        return tally.toData();
    }

    public Map<String, Object> saveState() {
        return ClearState.save(removed, skippedTotal(), outcome == Outcome.RUNNING);
    }

    public void restoreState(Map<String, Object> saved) {
        ClearState.Restored state = ClearState.restore(saved);
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

    void collectTick() {
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

    void apply(ClearBlocked.Sweep sweep) {
        unsafe += sweep.unsafe();
        noStand += sweep.noStand();
        unreachable += sweep.unreachable();
        excludedSkipped += sweep.excluded();
        protectedSkipped += sweep.planned();
        noteSkipped(sweep.skipped());
        if (!sweep.leftover().isEmpty()) {
            leftover = Stream.concat(leftover.stream(), sweep.leftover().stream())
                    .limit(LEFTOVER_SHOWN)
                    .toList();
        }
    }

    boolean clear(BlockCoord cell) {
        BlockState state = level.getBlockState(toPos(cell));
        return state.isAir() || (keepGround && PlotGround.natural(state));
    }

    void finish() {
        reservations.releaseAll(spirit.getUUID());
        if (outcome != Outcome.RUNNING) {
            return;
        }
        if (removed == 0 && skippedTotal() - protectedSkipped - excludedSkipped > 0) {
            fail(ClearSummary.of(this) + "; не убран ни один блок");
            return;
        }
        if (removed == 0 && skippedTotal() == 0) {
            ClearReport.clean(LOGGER, spirit, box);
        }
        blocked.warnExcluded(LOGGER, spirit, excludedSkipped, leftover);
        if (removed > 0) {
            ClearReport.success(
                    LOGGER,
                    spirit,
                    box,
                    new ClearReport.Metrics(
                            removed,
                            runner().brokenKinds(),
                            phaseTicks.getOrDefault(State.APPROACH, 0),
                            phaseTicks.getOrDefault(State.BREAK, 0),
                            phaseTicks.getOrDefault(State.COLLECT, 0),
                            runner().approachFailed(),
                            runner().approaches(),
                            runner().fromPlace(),
                            runner().vanished()),
                    ClearSummary.of(this));
        }
        outcome = Outcome.SUCCEEDED;
    }

    protected Outcome fail(String reason) {
        LOGGER.info(
                "[clear] {}: ящик {}..{} не расчищен: {}",
                spirit.getName().getString(),
                new BlockCoord(box.minX(), box.minY(), box.minZ()),
                new BlockCoord(box.maxX(), box.maxY(), box.maxZ()),
                reason);
        blocked.escalate(LOGGER, spirit, reason);
        failureReason = reason;
        outcome = Outcome.FAILED;
        tally.failed();
        cancel();
        return outcome;
    }

    void releaseCurrent() {
        if (currentTarget != null) {
            reservations.release(currentTarget, spirit.getUUID());
            currentTarget = null;
        }
        currentStand = null;
    }

    boolean retryOtherStand() {
        if (currentTarget == null) {
            return false;
        }
        BlockCoord target = currentTarget;
        if (currentStand != null) {
            approachFailures.excludeStand(currentStand, target);
        }
        releaseCurrent();
        remaining.add(0, target);
        state = State.PICK;
        return true;
    }

    void noteSkipped(int count) {
        for (int i = 0; i < count; i++) {
            tally.skipped();
        }
    }

    static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}
