package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.BuildReservations;
import com.theminespirit.core.building.BuildResult;
import com.theminespirit.core.building.api.BuildBox;
import com.theminespirit.core.engine.FailureFact;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.forestry.api.TreeSpecies;
import com.theminespirit.core.mining.ApproachFailureTracker;
import com.theminespirit.core.mining.BreakSkipTally;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
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
        COLLECT,
        PILLAR,
        DESCEND,
        UNLOAD
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int LEFTOVER_SHOWN = 4;

    protected final SpiritEntity spirit;
    protected final Level level;
    protected final BuildBox box;
    protected final List<BlockCoord> remaining;
    final int planned;
    protected final LevelSafetyView safety;
    protected final BlockBreaker breaker;
    protected final Approach approach;
    protected final BuildReservations reservations;
    protected final ClearTargets targets;
    String neededTool;
    boolean neededMaterial;
    FailureFact failureFact;

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
    final DemolitionPermit permit;
    final ClearBlocked blocked;
    final TreeFelling felling;
    private final Map<State, Integer> phaseTicks = new EnumMap<>(State.class);
    private ClearSweepRun run;

    public ClearAreaSession(SpiritEntity spirit, BuildBox box, boolean demolish, boolean keepGround) {
        this(spirit, List.of(box), demolish, keepGround);
    }

    public ClearAreaSession(SpiritEntity spirit, List<BuildBox> boxes, boolean demolish, boolean keepGround) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.box = bounds(boxes);
        LinkedHashSet<BlockCoord> cells = new LinkedHashSet<>();
        boxes.forEach(box -> cells.addAll(box.cellsBottomUp()));
        this.planned = cells.size();
        this.remaining = new ArrayList<>(cells);
        this.safety = new LevelSafetyView(level);
        this.breaker = new BlockBreaker(spirit);
        this.approach = new Approach(spirit);
        this.reservations = BuildReservationHub.forLevel(level);
        this.demolish = demolish;
        this.permit = DemolitionPermit.open(spirit, demolish);
        this.permit.claim(level, remaining);
        this.targets = new ClearTargets(level, permit.leaseId(), keepGround);
        this.blocked = new ClearBlocked(level, box);
        this.felling = new TreeFelling(new LevelTreeView(level, TreeBlocks.logs(TreeSpecies.ALL)));
        this.felling.extend(remaining);
    }

    private static BuildBox bounds(List<BuildBox> boxes) {
        if (boxes.isEmpty()) {
            throw new IllegalArgumentException("Список ящиков расчистки пуст: чистить нечего");
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BuildBox box : boxes) {
            minX = Math.min(minX, box.minX());
            minY = Math.min(minY, box.minY());
            minZ = Math.min(minZ, box.minZ());
            maxX = Math.max(maxX, box.maxX());
            maxY = Math.max(maxY, box.maxY());
            maxZ = Math.max(maxZ, box.maxZ());
        }
        return BuildBox.of(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private ClearSweepRun runner() {
        if (run == null) {
            run = new ClearSweepRun(this);
        }
        return run;
    }

    public Outcome tick() {
        if (outcome != Outcome.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) return fail("дух исчез во время расчистки");
        runner().filterTick();
        phaseTicks.merge(state, 1, Integer::sum);
        switch (state) {
            case PICK -> runner().pick();
            case APPROACH -> runner().approachTick();
            case BREAK -> runner().breakTick();
            case COLLECT -> collectTick();
            case PILLAR -> runner().pillarTick();
            case DESCEND -> runner().descentTick();
            case UNLOAD -> runner().unloadTick();
        }
        return outcome;
    }

    public void cancel() {
        breaker.cancel();
        approach.cancel();
        if (collector != null) collector.cancel();
        reservations.releaseAll(spirit.getUUID());
        if (outcome == Outcome.RUNNING) permit.orphan();
    }

    public void suspend() {
        approach.suspend();
        if (collector != null) collector.suspend();
    }

    public Outcome outcome() {
        return outcome;
    }

    public String failureReason() {
        return failureReason;
    }

    public ScriptInstance.Progress progress() {
        return ClearProgress.progress(this);
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

    void collectTick() {
        DropCollector.State collected = collector.tick();
        if (collected == DropCollector.State.RUNNING) {
            return;
        }
        DropCollector done = collector;
        collector = null;
        if (collected == DropCollector.State.FAILED && !ClearDrops.continues(done.full())) {
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

    void finish() {
        reservations.releaseAll(spirit.getUUID());
        if (outcome != Outcome.RUNNING) {
            return;
        }
        if (removed == 0 && skippedTotal() - protectedSkipped - excludedSkipped > 0) {
            fail(ClearSummary.of(this)
                    + (demolish
                            ? "; разбор не убрал ни одного блока: остатки нечем снять или они защищены"
                            : "; не убран ни один блок"));
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
        permit.commit();
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
        permit.orphan();
        return outcome;
    }

    protected Outcome fail(String reason, FailureFact fact) {
        failureFact = fact;
        return fail(reason);
    }

    void needTool(String item) {
        neededTool = item;
    }

    void releaseCurrent() {
        if (currentTarget != null) {
            reservations.release(currentTarget, spirit.getUUID());
            currentTarget = null;
        }
        currentStand = null;
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
