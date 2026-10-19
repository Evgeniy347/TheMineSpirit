package com.theminespirit.neoforge;

import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.farming.Above;
import com.theminespirit.core.farming.CropKind;
import com.theminespirit.core.farming.FarmJob;
import com.theminespirit.core.farming.FarmPlanner;
import com.theminespirit.core.farming.FarmPlot;
import com.theminespirit.core.farming.FarmTally;
import com.theminespirit.core.farming.FarmTile;
import com.theminespirit.core.farming.FarmTuning;
import com.theminespirit.core.farming.HarvestMethod;
import com.theminespirit.core.farming.SeedPolicy;
import com.theminespirit.core.mining.TargetBuffer;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.pickup.OverflowMode;
import com.theminespirit.core.pickup.PickupTuning;
import com.theminespirit.neoforge.inventory.UnloadScript;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public final class FarmSession {
    // Дух на пашне (высота 15/16) стоит в клетке самой пашни: земля грядки — блок под точкой чуть ниже ног.
    private static final double GROUND_PROBE = 0.2;

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
        REPLANT,
        DEPOSIT
    }

    private static final String FARM_ACTIVITY = "farming";

    private static final double APPROACH_ARRIVAL = 0.6;
    private static final int COLLECT_RADIUS = 3;
    private static final PickupTuning PICK_PICKUP = new PickupTuning(
            PickupTuning.REFRESH_TICKS,
            FarmTuning.REACH,
            PickupTuning.TOTAL_TIMEOUT_TICKS,
            PickupTuning.MAX_DELAY_WAIT_TICKS);
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
    private final HarvestTargets targets;
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
    private BlockCoord cropPos;
    private int columnHeight;
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
    private UnloadScript deposit;
    private boolean depositTried;

    private FarmSession(
            SpiritEntity spirit,
            FarmJob job,
            CropKind crop,
            int count,
            int radius,
            boolean replant,
            HarvestTargets targets) {
        this.spirit = spirit;
        this.job = job;
        this.crop = crop;
        this.count = count;
        this.radius = radius;
        this.replant = replant;
        this.targets = targets;
        this.view = new LevelFarmView(spirit.level());
        this.safety = new LevelSafetyView(spirit.level());
        this.actions = new FarmActions(spirit);
        this.breaker = new BlockBreaker(spirit);
        this.approach = new Approach(spirit);
    }

    public static FarmSession till(SpiritEntity spirit, int radius, int count) {
        return new FarmSession(
                spirit,
                FarmJob.TILL,
                CropKind.ANY,
                count,
                radius,
                false,
                new FarmTargets(spirit.level(), spirit.blockPosition(), radius, FarmJob.TILL, CropKind.ANY));
    }

    public static FarmSession plantCrop(SpiritEntity spirit, CropKind crop, int radius, int count) {
        return new FarmSession(
                spirit,
                FarmJob.PLANT,
                crop,
                count,
                radius,
                false,
                new FarmTargets(spirit.level(), spirit.blockPosition(), radius, FarmJob.PLANT, crop));
    }

    public static FarmSession harvestCrop(SpiritEntity spirit, CropKind crop, int radius, boolean replant) {
        return new FarmSession(
                spirit,
                FarmJob.HARVEST,
                crop,
                UNLIMITED,
                radius,
                replant,
                new FarmTargets(spirit.level(), spirit.blockPosition(), radius, FarmJob.HARVEST, crop));
    }

    public static FarmSession boneMeal(SpiritEntity spirit, CropKind crop, int radius, int count) {
        return new FarmSession(
                spirit,
                FarmJob.BONE_MEAL,
                crop,
                count,
                radius,
                false,
                new FarmTargets(spirit.level(), spirit.blockPosition(), radius, FarmJob.BONE_MEAL, crop));
    }

    public static FarmSession tillPlot(SpiritEntity spirit, FarmPlot plot) {
        int groundY = plotGroundY(spirit);
        return new FarmSession(
                spirit,
                FarmJob.TILL,
                CropKind.ANY,
                plot.cells(),
                plot.searchRadius(groundY),
                false,
                new FarmTargets(
                        spirit.level(),
                        plotPos(plot, groundY),
                        plot.searchRadius(groundY),
                        plot.grounds(groundY),
                        FarmJob.TILL));
    }

    public static FarmSession plantPlot(SpiritEntity spirit, CropKind crop, FarmPlot plot) {
        int groundY = plotGroundY(spirit);
        return new FarmSession(
                spirit,
                FarmJob.PLANT,
                crop,
                plot.cells(),
                plot.searchRadius(groundY),
                false,
                new FarmTargets(
                        spirit.level(),
                        plotPos(plot, groundY),
                        plot.searchRadius(groundY),
                        plot.grounds(groundY),
                        FarmJob.PLANT));
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
        if (!actions.hasSupplies(job, crop)) {
            lostSupplies();
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
            case DEPOSIT -> depositTick();
        }
        return outcome;
    }

    public void cancel() {
        breaker.cancel();
        approach.cancel();
        if (collector != null) {
            collector.cancel();
        }
        if (deposit != null) {
            deposit.cancel();
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
        boolean replantPending = replant
                && currentGround != null
                && currentKind != null
                && currentKind.harvestMethod() == HarvestMethod.BREAK
                && !currentKind.hasSupport()
                && (state == State.COLLECT || state == State.REPLANT);
        if (!replantPending) {
            return Map.of(DONE_KEY, done);
        }
        return Map.of(
                DONE_KEY,
                done,
                REPLANT_X_KEY,
                currentGround.x(),
                REPLANT_Y_KEY,
                currentGround.y(),
                REPLANT_Z_KEY,
                currentGround.z(),
                REPLANT_KIND_KEY,
                currentKind.name());
    }

    public void restoreState(Map<String, Object> saved) {
        if (saved.get(DONE_KEY) instanceof Number number) {
            done = Math.max(done, number.intValue());
        }
        if (saved.get(REPLANT_KIND_KEY) instanceof String kind
                && saved.get(REPLANT_X_KEY) instanceof Number x
                && saved.get(REPLANT_Y_KEY) instanceof Number y
                && saved.get(REPLANT_Z_KEY) instanceof Number z) {
            currentGround = new BlockCoord(x.intValue(), y.intValue(), z.intValue());
            currentKind = CropKind.parse(kind);
            startCollect();
        }
    }

    public int planCalls() {
        return planCalls;
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
        if (buffer.isEmpty()) {
            state = State.FIND;
            return;
        }
        if (buffer.needsRefill() && !targets.done()) {
            buffer.offer(targets.advance());
        }
        planCalls++;
        BlockCoord origin = coordOf(spirit.blockPosition());
        FarmPlanner.Outcome result =
                FarmPlanner.next(job, crop, origin, buffer.targets(origin), view, safety, targets.hydration());
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
        if (SpiritReach.reachable(origin, currentGround)) {
            state = State.ACT;
            return;
        }
        approach.begin(
                new Vec3(next.stand().x() + 0.5, next.stand().y(), next.stand().z() + 0.5), APPROACH_ARRIVAL);
        state = State.APPROACH;
    }

    private void approachTick() {
        switch (approach.tick()) {
            case MOVING -> {}
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
        return SpiritReach.reachable(coordOf(spirit.blockPosition()), currentGround);
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
                harvestCrop(ground);
            }
            case BONE_MEAL -> boneMealNow(ground.above());
        }
    }

    private void harvestCrop(BlockPos ground) {
        HarvestMethod method = currentKind.harvestMethod();
        if (method == HarvestMethod.PICK) {
            pickNow(ground.above(), currentKind);
        } else if (method == HarvestMethod.BREAK_COLUMN) {
            int height = view.columnHeight(currentGround);
            if (height < 2) {
                ineligible++;
                finishTarget();
            } else {
                columnHeight = height;
                beginBreak(ground.above(height));
            }
        } else if (method == HarvestMethod.BREAK_FRUIT) {
            harvestAttached(view.fruitAt(currentGround, currentKind));
        } else if (currentKind.hasSupport()) {
            harvestAttached(view.attachedCrop(currentGround, currentKind));
        } else {
            beginBreak(ground.above());
        }
    }

    private void harvestAttached(Optional<BlockCoord> target) {
        if (target.isEmpty()) {
            ineligible++;
            finishTarget();
            return;
        }
        cropPos = target.get();
        beginBreak(posOf(cropPos));
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
        if (plantAt(crop, ground)) {
            done++;
        } else {
            rejected++;
        }
        finishTarget();
    }

    private boolean plantAt(CropKind kind, BlockPos ground) {
        if (!kind.hasSupport()) {
            return actions.plant(ground, kind);
        }
        Optional<BlockCoord> spot = view.attachedSpot(currentGround, kind);
        if (spot.isEmpty()) {
            return false;
        }
        Direction side = Direction.fromDelta(
                spot.get().x() - ground.getX(), 0, spot.get().z() - ground.getZ());
        if (side == null) {
            throw new IllegalStateException("Клетка посадки " + spot.get() + " не сосед опоры " + ground
                    + ": какао растёт только на боку ствола");
        }
        return actions.plantOnSide(ground, side, kind);
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
        if (breaker.beginAnyDistance(target, true) instanceof BlockBreaker.Started) {
            state = State.BREAK;
        } else {
            noStand++;
            finishTarget();
        }
    }

    private void pickNow(BlockPos target, CropKind kind) {
        recordAction();
        if (actions.pick(target, kind)) {
            done++;
            startCollect();
        } else {
            rejected++;
            finishTarget();
        }
    }

    private void breakTick() {
        recordAction();
        switch (breaker.tick()) {
            case BREAKING -> {}
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
        if (currentKind.harvestMethod() == HarvestMethod.BREAK_COLUMN) {
            BlockPos broken = posOf(currentGround).above(columnHeight);
            actions.rescueDrops(broken);
            columnHeight--;
            if (columnHeight >= 2) {
                beginBreak(posOf(currentGround).above(columnHeight));
                return;
            }
        }
        done++;
        startCollect();
    }

    private void startCollect() {
        collectRuns++;
        IdFilter harvested = IdFilter.of(List.of(currentKind.produceItem(), currentKind.plantItem()));
        boolean column = currentKind.harvestMethod() == HarvestMethod.BREAK_COLUMN;
        PickupTuning tuning =
                currentKind.harvestMethod() == HarvestMethod.PICK || column ? PICK_PICKUP : PickupTuning.DEFAULT;
        BlockCoord center = column ? coordOf(spirit.blockPosition()) : cropPos != null ? cropPos : currentGround;
        collector = new DropCollector(
                spirit, harvested, center, COLLECT_RADIUS, tuning, OverflowMode.forSpirit(spirit.isFollowingPlayer()));
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
        if (replant && currentKind.harvestMethod() == HarvestMethod.BREAK) {
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
            if (!plantAt(currentKind, posOf(currentGround))) {
                unreachable++;
            }
        }
        finishTarget();
    }

    private void finishTarget() {
        currentGround = null;
        currentKind = null;
        cropPos = null;
        columnHeight = 0;
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
        int noTool = job == FarmJob.TILL ? 1 : 0;
        int noSeeds = job == FarmJob.TILL ? 0 : 1;
        FarmTally tally = new FarmTally(done, count, ineligible, noStand, noSeeds, noTool, dry, unreachable, radius);
        fail(((FarmTally.Failed) tally.outcome(job)).text());
    }

    private void concludeExhausted() {
        int eligibleAndBlocking = done + noStand + noSeeds + rejected + unreachable;
        int requested = Math.min(count, eligibleAndBlocking);
        FarmTally tally =
                new FarmTally(done, requested, ineligible + rejected, noStand, noSeeds, 0, dry, unreachable, radius);
        if (tally.outcome(job) instanceof FarmTally.Failed failed) {
            fail(failed.text());
        } else if (job == FarmJob.HARVEST && done > 0 && !depositTried) {
            startDeposit();
        } else {
            outcome = Outcome.SUCCEEDED;
        }
    }

    private void startDeposit() {
        depositTried = true;
        breaker.cancel();
        approach.cancel();
        if (collector != null) {
            collector.cancel();
            collector = null;
        }
        deposit = UnloadScript.reliefFor(spirit, -1, FARM_ACTIVITY);
        state = State.DEPOSIT;
    }

    private void depositTick() {
        ScriptStatus status = deposit.tick();
        if (status == ScriptStatus.RUNNING) {
            return;
        }
        deposit = null;
        outcome = Outcome.SUCCEEDED;
    }

    private Outcome fail(String reason) {
        outcome = Outcome.FAILED;
        failureReason = reason;
        cancel();
        return outcome;
    }

    private static int plotGroundY(SpiritEntity spirit) {
        return (int) Math.floor(spirit.getY() - GROUND_PROBE);
    }

    private static BlockPos plotPos(FarmPlot plot, int groundY) {
        BlockCoord center = plot.center(groundY);
        return new BlockPos(center.x(), center.y(), center.z());
    }

    private static BlockCoord coordOf(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }

    private static BlockPos posOf(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}
