package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.BuildBox;
import com.theminespirit.core.building.BuildReservations;
import com.theminespirit.core.building.BuildResult;
import com.theminespirit.core.building.CaveStand;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.core.mining.ApproachFailureTracker;
import com.theminespirit.core.mining.BreakSafety;
import com.theminespirit.core.mining.BreakSkipTally;
import com.theminespirit.core.mining.ClearOrder;
import com.theminespirit.core.mining.MiningPlanner;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.pickup.OverflowMode;
import com.theminespirit.core.pickup.PickupTuning;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
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

    private static final double APPROACH_ARRIVAL = 0.6;
    private static final int PROGRESS_EVERY = 16;
    private static final int PROTECTED_LOGGED = 10;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int COLLECT_RADIUS = 4;
    private static final int LEFTOVER_SHOWN = 4;
    private static final int LONG_APPROACH = 60;

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

    private final BuildResult tally = new BuildResult();
    private int approachFailed;
    private int approachSpent;
    private int approaches;
    private int fromPlace;
    private int vanished;
    private String pickedKind = "";
    private String brokenKind = "";
    private final Map<String, Integer> brokenKinds = new TreeMap<>();
    private int longApproaches;
    private final Map<State, Integer> phaseTicks = new EnumMap<>(State.class);
    private final boolean demolish;
    private final boolean keepGround;
    private final ClearBlocked blocked;
    private final TreeFelling felling;

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

    public Outcome tick() {
        if (outcome != Outcome.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) return fail("дух исчез во время расчистки");
        phaseTicks.merge(state, 1, Integer::sum);
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

    private void pick() {
        noteSkipped(felling.dropPartial(remaining));
        while (!remaining.isEmpty() && clear(remaining.get(0))) {
            remaining.remove(0);
        }
        apply(blocked.dropExcluded(remaining));
        apply(blocked.dropPlanned(remaining));
        noteSkipped(felling.dropPartial(remaining));
        if (remaining.isEmpty()) {
            finish();
            return;
        }
        BlockPos feet = spirit.blockPosition();
        BlockCoord from = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        List<BlockCoord> top = ClearOrder.topmost(remaining, cell -> !clear(cell));
        BlockCoord near = ClearReach.near(spirit, level, top, feet, safety, reservations);
        if (near != null && !approachFailures.excludedStands().contains(from)) {
            remaining.remove(near);
            reservations.reserve(near, spirit.getUUID());
            currentTarget = near;
            currentStand = from;
            approachSpent = 0;
            fromPlace++;
            prepareBreak();
            return;
        }
        MiningPlanner.Outcome result = MiningPlanner.next(from, top, safety, approachFailures.excludedStands());
        if (result instanceof MiningPlanner.Exhausted) {
            List<BlockCoord> solid =
                    remaining.stream().filter(cell -> !clear(cell)).toList();
            result = MiningPlanner.next(from, solid, safety, approachFailures.excludedStands());
        }
        if (result instanceof MiningPlanner.Exhausted) {
            apply(blocked.exhausted(
                    remaining.stream().filter(cell -> !clear(cell)).toList(), safety, from));
            remaining.clear();
            finish();
            return;
        }
        MiningPlanner.Next next = (MiningPlanner.Next) result;
        remaining.remove(next.target());
        BlockPos standPos = toPos(next.stand());
        if (CaveStand.cave(next.stand().y(), box.minY(), level.canSeeSky(standPos))) {
            unreachable++;
            tally.skipped();
            LOGGER.info(
                    "[clear] {}: клетка {} — стоянка {} ниже ящика (пещера под участком), пропуск",
                    spirit.getName().getString(),
                    next.target(),
                    next.stand());
            return;
        }
        if (reservations.foreign(next.target(), spirit.getUUID())) {
            foreignSkipped++;
            tally.skipped();
            return;
        }
        reservations.reserve(next.target(), spirit.getUUID());
        currentTarget = next.target();
        currentStand = next.stand();
        pickedKind = ClearBlocked.blockAt(level, currentTarget);
        approach.beginWithRoutes(
                new Vec3(next.stand().x() + 0.5, next.stand().y(), next.stand().z() + 0.5), APPROACH_ARRIVAL);
        approaches++;
        state = State.APPROACH;
    }

    private void approachTick() {
        approachSpent++;
        BlockPos feet = spirit.blockPosition();
        if (spirit.onGround()
                && feet.getX() == currentStand.x()
                && feet.getY() == currentStand.y()
                && feet.getZ() == currentStand.z()) {
            approach.cancel();
            arrived();
            return;
        }
        switch (approach.tick()) {
            case MOVING -> {}
            case ARRIVED -> arrived();
            case STUCK, TIMEOUT -> {
                approachSpent = 0;
                approachFailed++;
                if (approachFailed <= PROTECTED_LOGGED) {
                    BlockPos at = spirit.blockPosition();
                    LOGGER.info(
                            "[clear] {}: срыв подхода к стоянке {} для {} из {}, {}",
                            spirit.getName().getString(),
                            currentStand,
                            currentTarget,
                            at.toShortString(),
                            approach.refusal() == null ? "без причины" : approach.refusal());
                }
                if (approachFailures.recordFailure(currentStand, currentTarget)) {
                    apply(blocked.approachFailure(currentTarget, currentStand, approach.refusal()));
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

    private void apply(ClearBlocked.Sweep sweep) {
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

    private boolean clear(BlockCoord cell) {
        BlockState state = level.getBlockState(toPos(cell));
        return state.isAir() || (keepGround && PlotGround.natural(state));
    }

    private void arrived() {
        if (approachSpent > LONG_APPROACH && longApproaches++ < PROTECTED_LOGGED) {
            LOGGER.info(
                    "[clear] {}: долгий подход {} тиков к стоянке {} для {} ({})",
                    spirit.getName().getString(),
                    approachSpent,
                    currentStand,
                    currentTarget,
                    ClearBlocked.blockAt(level, currentTarget));
        }
        approachSpent = 0;
        prepareBreak();
    }

    private void prepareBreak() {
        if (!demolish && PlanCells.keep(level, toPos(currentTarget))) {
            protectedSkipped++;
            if (protectedSkipped <= PROTECTED_LOGGED) {
                LOGGER.info(
                        "[clear] {}: {} — постройка плана поселения, не ломается",
                        spirit.getName().getString(),
                        currentTarget);
            }
            tally.skipped();
            releaseCurrent();
            state = State.PICK;
            return;
        }
        if (!(MiningSafety.canBreak(currentTarget, safety) instanceof BreakSafety.Safe)) {
            unsafe++;
            tally.skipped();
            releaseCurrent();
            state = State.PICK;
            return;
        }
        BlockBreaker.Begin begin = breaker.begin(toPos(currentTarget), false, demolish);
        if (begin instanceof BlockBreaker.Protected kept) {
            protectedSkipped++;
            if (protectedSkipped <= PROTECTED_LOGGED) {
                LOGGER.info(
                        "[clear] {}: защищено {} — {}: {} ({})",
                        spirit.getName().getString(),
                        currentTarget,
                        BuiltInRegistries.BLOCK.getKey(
                                level.getBlockState(toPos(currentTarget)).getBlock()),
                        kept.policy(),
                        kept.reason());
            }
            tally.skipped();
            releaseCurrent();
            state = State.PICK;
            return;
        }
        if (begin instanceof BlockBreaker.Refused refused && refused.reason() == BlockBreaker.RefuseReason.NEED_TOOL) {
            fail(refused.message());
            return;
        }
        if (begin instanceof BlockBreaker.Refused refused && refused.reason() == BlockBreaker.RefuseReason.AIR) {
            vanished++;
            if (vanished <= PROTECTED_LOGGED) {
                LOGGER.info(
                        "[clear] {}: клетка {} ({} при выборе) пуста по приходу на {}",
                        spirit.getName().getString(),
                        currentTarget,
                        pickedKind,
                        currentStand);
            }
            releaseCurrent();
            state = State.PICK;
            return;
        }
        if (begin instanceof BlockBreaker.Refused refused) {
            if (refused.reason() == BlockBreaker.RefuseReason.NEED_TOOL) {
                fail(refused.message());
                return;
            }
            BreakSkipTally.Reason reason = BreakSkips.reason(refused.reason());
            breakSkips
                    .record(reason, currentTarget, currentStand)
                    .ifPresent(sample -> SkipLog.skipped(LOGGER, spirit, "clear", sample));
            tally.skipped();
            if (BreakSight.retryable(refused.reason()) && retryOtherStand()) {
                return;
            }
            releaseCurrent();
            state = State.PICK;
            return;
        }
        brokenKind = ClearBlocked.blockAt(level, currentTarget);
        state = State.BREAK;
    }

    private boolean retryOtherStand() {
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

    private void breakTick() {
        switch (breaker.tick()) {
            case BREAKING -> {}
            case BROKEN -> {
                removed++;
                felling.removed(currentTarget);
                blocked.reached(currentTarget);
                brokenKinds.merge(brokenKind, 1, Integer::sum);
                tally.removed();
                if (removed % PROGRESS_EVERY == 1) {
                    ClearReport.progress(LOGGER, spirit, box, removed, remaining.size(), currentStand);
                }
                collector = new DropCollector(
                        spirit,
                        IdFilter.matchAll(),
                        currentTarget,
                        COLLECT_RADIUS,
                        PickupTuning.DEFAULT,
                        OverflowMode.RETURN_TO_BASE);
                state = State.COLLECT;
            }
            case GONE -> {
                vanished++;
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
                            brokenKinds,
                            phaseTicks.getOrDefault(State.APPROACH, 0),
                            phaseTicks.getOrDefault(State.BREAK, 0),
                            phaseTicks.getOrDefault(State.COLLECT, 0),
                            approachFailed,
                            approaches,
                            fromPlace,
                            vanished),
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
