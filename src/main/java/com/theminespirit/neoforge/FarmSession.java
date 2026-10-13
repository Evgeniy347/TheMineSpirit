package com.theminespirit.neoforge;

import com.theminespirit.core.farming.Above;
import com.theminespirit.core.farming.CropKind;
import com.theminespirit.core.farming.FarmJob;
import com.theminespirit.core.farming.FarmPlanner;
import com.theminespirit.core.farming.FarmTally;
import com.theminespirit.core.farming.FarmTile;
import com.theminespirit.core.farming.FarmTuning;
import com.theminespirit.core.farming.SeedPolicy;
import com.theminespirit.core.mining.TargetBuffer;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.pickup.OverflowMode;
import com.theminespirit.core.pickup.PickupTuning;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class FarmSession {
    public enum Outcome {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    private enum State {
        FIND,
        PICK,
        APPROACH,
        ACT,
        BREAK,
        COLLECT,
        REPLANT
    }

    private static final double APPROACH_ARRIVAL = 0.6;
    private static final int COLLECT_RADIUS = 3;
    private static final int UNLIMITED = Integer.MAX_VALUE;
    private static final String DONE_KEY = "done";
    private static final String REPLANT_X_KEY = "replantX";
    private static final String REPLANT_Y_KEY = "replantY";
    private static final String REPLANT_Z_KEY = "replantZ";
    private static final String REPLANT_KIND_KEY = "replantKind";

    private final SpiritEntity spirit;
    private final FarmJob job;
    private final CropKind crop;
    private final int count;
    private final int radius;
    private final boolean replant;
    private final FarmTargets targets;
    private final LevelFarmView view;
    private final LevelSafetyView safety;
    private final FarmActions actions;
    private final BlockBreaker breaker;
    private final Approach approach;
    private final TargetBuffer buffer = new TargetBuffer();

    private State state = State.FIND;
    private Outcome outcome = Outcome.RUNNING;
    private String failureReason;
    private BlockCoord currentGround;
    private CropKind currentKind;
    private DropCollector collector;

    private int done;
    private int ineligible;
    private int dry;
    private int noStand;
    private int noSeeds;
    private int rejected;
    private int unreachable;
    private int planCalls;
    private int collectRuns;
    private int ticks;
    private int entityQueries;
    private int maxEntityQueriesPerTick;
    private int actionTick = -1;
    private int actionsThisTick;
    private int maxActionsPerTick;

    private FarmSession(SpiritEntity spirit, FarmJob job, CropKind crop, int count, int radius, boolean replant) {
        this.spirit = spirit;
        this.job = job;
        this.crop = crop;
        this.count = count;
        this.radius = radius;
        this.replant = replant;
        this.targets = new FarmTargets(spirit.level(), spirit.blockPosition(), radius, job, crop);
        this.view = new LevelFarmView(spirit.level());
        this.safety = new LevelSafetyView(spirit.level());
        this.actions = new FarmActions(spirit);
        this.breaker = new BlockBreaker(spirit);
        this.approach = new Approach(spirit);
    }

    public static FarmSession till(SpiritEntity spirit, int radius, int count) {
        return new FarmSession(spirit, FarmJob.TILL, CropKind.ANY, count, radius, false);
    }

    public static FarmSession plantCrop(SpiritEntity spirit, CropKind crop, int radius, int count) {
        return new FarmSession(spirit, FarmJob.PLANT, crop, count, radius, false);
    }

    public static FarmSession harvestCrop(SpiritEntity spirit, CropKind crop, int radius, boolean replant) {
        return new FarmSession(spirit, FarmJob.HARVEST, crop, UNLIMITED, radius, replant);
    }

    public static FarmSession boneMeal(SpiritEntity spirit, CropKind crop, int radius, int count) {
        return new FarmSession(spirit, FarmJob.BONE_MEAL, crop, count, radius, false);
    }

    public Outcome tick() {
        if (outcome != Outcome.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время работы на грядке");
        }
        if (ticks == 0 && !actions.hasSupplies(job, crop)) {
            return refuseWithoutSupplies();
        }
        if (done >= count) {
            outcome = Outcome.SUCCEEDED;
            return outcome;
        }
        ticks++;
        switch (state) {
            case FIND -> find();
            case PICK -> pick();
            case APPROACH -> approachTick();
            case ACT -> act();
            case BREAK -> breakTick();
            case COLLECT -> collectTick();
            case REPLANT -> replantTick();
        }
        return outcome;
    }

    public void cancel() {
        breaker.cancel();
        approach.cancel();
        if (collector != null) {
            collector.cancel();
        }
    }

    public Outcome outcome() {
        return outcome;
    }

    public String failureReason() {
        return failureReason;
    }

    public int done() {
        return done;
    }

    public Map<String, Object> saveState() {
        boolean replantPending = replant && currentGround != null && currentKind != null
                && (state == State.COLLECT || state == State.REPLANT);
        if (!replantPending) {
            return Map.of(DONE_KEY, done);
        }
        return Map.of(DONE_KEY, done, REPLANT_X_KEY, currentGround.x(), REPLANT_Y_KEY, currentGround.y(),
                REPLANT_Z_KEY, currentGround.z(), REPLANT_KIND_KEY, currentKind.name());
    }

    public void restoreState(Map<String, Object> saved) {
        if (saved.get(DONE_KEY) instanceof Number number) {
            done = Math.max(done, number.intValue());
        }
        if (saved.get(REPLANT_KIND_KEY) instanceof String kind && saved.get(REPLANT_X_KEY) instanceof Number x
                && saved.get(REPLANT_Y_KEY) instanceof Number y && saved.get(REPLANT_Z_KEY) instanceof Number z) {
            currentGround = new BlockCoord(x.intValue(), y.intValue(), z.intValue());
            currentKind = CropKind.parse(kind);
            startCollect();
        }
    }

    public int planCalls() {
        return planCalls;
    }

    public int viewReads() {
        return view.reads();
    }

    public int safetyReads() {
        return safety.reads();
    }

    public int slicesUsed() {
        return targets.slicesUsed();
    }

    public int maxSlicesPerTick() {
        return targets.maxSlicesPerTick();
    }

    public int maxActionsPerTick() {
        return maxActionsPerTick;
    }

    public int maxEntityQueriesPerTick() {
        return maxEntityQueriesPerTick;
    }

    public int entityQueries() {
        return entityQueries;
    }

    public int collectRuns() {
        return collectRuns;
    }

    public int totalTicks() {
        return ticks;
    }

    private void find() {
        if (targets.refusalReason() != null) {
            fail(targets.refusalReason());
            return;
        }
        if (!buffer.needsRefill()) {
            state = State.PICK;
            return;
        }
        buffer.offer(targets.advance());
        if (targets.refusalReason() != null) {
            fail(targets.refusalReason());
        } else if (!buffer.isEmpty()) {
            state = State.PICK;
        } else if (targets.done()) {
            concludeExhausted();
        }
    }

    private void pick() {
        if (!actions.hasSupplies(job, crop)) {
            lostSupplies();
            return;
        }
        if (buffer.isEmpty()) {
            state = State.FIND;
            return;
        }
        if (buffer.needsRefill() && !targets.done()) {
            buffer.offer(targets.advance());
        }
        planCalls++;
        BlockCoord origin = coordOf(spirit.blockPosition());
        FarmPlanner.Outcome result = FarmPlanner.next(job, crop, origin, buffer.targets(origin), view, safety,
                targets.hydration());
        if (result instanceof FarmPlanner.Exhausted exhausted) {
            ineligible += exhausted.ineligible();
            dry += exhausted.dry();
            noStand += exhausted.noStand();
            buffer.clearActive();
            state = State.FIND;
            return;
        }
        FarmPlanner.Next next = (FarmPlanner.Next) result;
        buffer.remove(next.target());
        currentGround = next.target();
        approach.begin(new Vec3(next.stand().x() + 0.5, next.stand().y(), next.stand().z() + 0.5),
                APPROACH_ARRIVAL);
        state = State.APPROACH;
    }

    private void approachTick() {
        switch (approach.tick()) {
            case MOVING -> {
            }
            case ARRIVED -> state = State.ACT;
            case STUCK, TIMEOUT -> {
                if (inReachOfTarget()) {
                    state = State.ACT;
                } else {
                    noStand++;
                    finishTarget();
                }
            }
        }
    }

    private boolean inReachOfTarget() {
        return spirit.getEyePosition().distanceTo(Vec3.atCenterOf(posOf(currentGround))) <= FarmTuning.REACH;
    }

    private void act() {
        FarmTile tile = view.tileAt(currentGround);
        if (!job.eligible(tile, crop, targets.hydration().watered(currentGround))) {
            ineligible++;
            finishTarget();
            return;
        }
        BlockPos ground = posOf(currentGround);
        switch (job) {
            case TILL -> tillOrClearWeed(tile, ground);
            case PLANT -> plantNow(ground);
            case HARVEST -> {
                currentKind = tile.crop().orElseThrow().kind();
                beginBreak(ground.above());
            }
            case BONE_MEAL -> boneMealNow(ground.above());
        }
    }

    private void tillOrClearWeed(FarmTile tile, BlockPos ground) {
        if (tile.above() == Above.WEED) {
            beginBreak(ground.above());
            return;
        }
        recordAction();
        if (actions.till(ground)) {
            done++;
        } else {
            rejected++;
        }
        finishTarget();
    }

    private void plantNow(BlockPos ground) {
        int seeds = actions.count(CropRegistry.plantItem(crop));
        if (SeedPolicy.forPlanting(crop, seeds, count - done) == 0) {
            lostSupplies();
            return;
        }
        recordAction();
        if (actions.plant(ground, crop)) {
            done++;
        } else {
            rejected++;
        }
        finishTarget();
    }

    private void boneMealNow(BlockPos cropPos) {
        recordAction();
        if (actions.boneMeal(cropPos)) {
            done++;
            if (job.eligible(view.tileAt(currentGround), crop, false)) {
                buffer.offer(List.of(currentGround));
            }
        } else {
            rejected++;
        }
        finishTarget();
    }

    private void beginBreak(BlockPos target) {
        if (breaker.begin(target, true) instanceof BlockBreaker.Started) {
            state = State.BREAK;
        } else {
            noStand++;
            finishTarget();
        }
    }

    private void breakTick() {
        recordAction();
        switch (breaker.tick()) {
            case BREAKING -> {
            }
            case BROKEN -> afterBroken();
            case GONE -> {
                if (job == FarmJob.HARVEST) {
                    finishTarget();
                } else {
                    state = State.ACT;
                }
            }
        }
    }

    private void afterBroken() {
        if (job != FarmJob.HARVEST) {
            state = State.ACT;
            return;
        }
        done++;
        startCollect();
    }

    private void startCollect() {
        collectRuns++;
        IdFilter harvested = IdFilter.of(List.of(currentKind.produceItem(), currentKind.plantItem()));
        collector = new DropCollector(spirit, harvested, currentGround, COLLECT_RADIUS, PickupTuning.DEFAULT,
                OverflowMode.FAIL);
        state = State.COLLECT;
    }

    private void collectTick() {
        int before = collector.entityQueries();
        DropCollector.State collected = collector.tick();
        maxEntityQueriesPerTick = Math.max(maxEntityQueriesPerTick, collector.entityQueries() - before);
        if (collected == DropCollector.State.RUNNING) {
            return;
        }
        entityQueries += collector.entityQueries();
        if (collected == DropCollector.State.FAILED) {
            fail("не удалось подобрать урожай у " + currentGround + ": " + collector.failureReason());
            return;
        }
        collector = null;
        if (replant) {
            state = State.REPLANT;
        } else {
            finishTarget();
        }
    }

    private void replantTick() {
        int seeds = actions.count(CropRegistry.plantItem(currentKind));
        if (SeedPolicy.forReplant(currentKind, seeds) == SeedPolicy.Decision.OUT_OF_SEEDS) {
            noSeeds++;
        } else {
            recordAction();
            if (!actions.plant(posOf(currentGround), currentKind)) {
                unreachable++;
            }
        }
        finishTarget();
    }

    private void finishTarget() {
        currentGround = null;
        currentKind = null;
        state = State.PICK;
    }

    private void recordAction() {
        int now = spirit.tickCount;
        actionsThisTick = now == actionTick ? actionsThisTick + 1 : 1;
        actionTick = now;
        maxActionsPerTick = Math.max(maxActionsPerTick, actionsThisTick);
    }

private Outcome refuseWithoutSupplies() {
        FarmTally.Outcome refusal = new FarmTally(0, 0, 0, 0, 0, 1, 0, 0, 0).outcome(job);
        return fail(((FarmTally.Failed) refusal).text());
    }

    private void lostSupplies() {
        int known = done + 1 + buffer.size() + noStand + rejected;
        fail(actions.lostSuppliesText(job) + ": сделано " + done + " из " + Math.min(count, known));
    }

    private void concludeExhausted() {
        int eligibleAndBlocking = done + noStand + noSeeds + rejected + unreachable;
        int requested = Math.min(count, eligibleAndBlocking);
        FarmTally tally = new FarmTally(done, requested, ineligible + rejected, noStand, noSeeds, 0, dry, unreachable, radius);
        if (tally.outcome(job) instanceof FarmTally.Failed failed) {
            fail(failed.text());
        } else {
            outcome = Outcome.SUCCEEDED;
        }
    }

    private Outcome fail(String reason) {
        outcome = Outcome.FAILED;
        failureReason = reason;
        cancel();
        return outcome;
    }

    private static BlockCoord coordOf(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }

    private static BlockPos posOf(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}
