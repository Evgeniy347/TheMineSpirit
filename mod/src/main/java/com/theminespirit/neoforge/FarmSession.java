package com.theminespirit.neoforge;

import com.theminespirit.core.farming.CropKind;
import com.theminespirit.core.farming.FarmJob;
import com.theminespirit.core.farming.FarmPlot;
import com.theminespirit.core.farming.FarmTally;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Map;
import net.minecraft.core.BlockPos;

public final class FarmSession {
    public enum Outcome {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    enum State {
        FIND,
        PICK,
        APPROACH,
        ACT,
        BREAK,
        COLLECT,
        REPLANT,
        DEPOSIT
    }

    static final String DONE_KEY = "done";
    static final String REPLANT_X_KEY = "replantX";
    static final String REPLANT_Y_KEY = "replantY";
    static final String REPLANT_Z_KEY = "replantZ";
    static final String REPLANT_KIND_KEY = "replantKind";

    private final SpiritEntity spirit;
    private final FarmJob job;
    private final CropKind crop;
    private final int count;
    private final HarvestTargets targets;
    private final FarmActions actions;
    private final BlockBreaker breaker;
    private final FarmCounters counters;
    private final FarmSearch search;
    private final FarmLoot loot;
    private final FarmWork work;

    private State state = State.FIND;
    private Outcome outcome = Outcome.RUNNING;
    private String failureReason;

    FarmSession(
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
        this.targets = targets;
        LevelFarmView view = new LevelFarmView(spirit.level());
        this.actions = new FarmActions(spirit);
        this.breaker = new BlockBreaker(spirit);
        this.counters = new FarmCounters(job, count, radius);
        this.search = new FarmSearch(
                spirit,
                job,
                crop,
                targets,
                view,
                counters,
                new FarmSearch.Callbacks(this::fail, this::concludeExhausted));
        this.loot = new FarmLoot(spirit, search, breaker, counters, replant);
        this.work = new FarmWork(
                new FarmWork.Setup(spirit, job, crop, count, replant, view, actions, targets, counters),
                new FarmWork.Helpers(search, loot, breaker, this::fail, this::beginUnlimited));
    }

    public static FarmSession till(SpiritEntity spirit, int radius, int count) {
        return FarmSessions.till(spirit, radius, count);
    }

    public static FarmSession plantCrop(SpiritEntity spirit, CropKind crop, int radius, int count) {
        return FarmSessions.plantCrop(spirit, crop, radius, count);
    }

    public static FarmSession harvestCrop(SpiritEntity spirit, CropKind crop, int radius, boolean replant) {
        return FarmSessions.harvestCrop(spirit, crop, radius, replant);
    }

    public static FarmSession boneMeal(SpiritEntity spirit, CropKind crop, int radius, int count) {
        return FarmSessions.boneMeal(spirit, crop, radius, count);
    }

    public static FarmSession tillPlot(SpiritEntity spirit, FarmPlot plot) {
        return FarmSessions.tillPlot(spirit, plot);
    }

    public static FarmSession plantPlot(SpiritEntity spirit, CropKind crop, FarmPlot plot) {
        return FarmSessions.plantPlot(spirit, crop, plot);
    }

    public Outcome tick() {
        if (outcome != Outcome.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время работы на грядке");
        }
        if (counters.firstTick() && !actions.hasSupplies(job, crop)) {
            return fail(counters.withoutSuppliesReason());
        }
        if (counters.done() >= count) {
            outcome = Outcome.SUCCEEDED;
            return outcome;
        }
        if (!actions.hasSupplies(job, crop)) {
            fail(counters.lostSuppliesReason());
            return outcome;
        }
        counters.beginTick();
        switch (state) {
            case FIND -> state = search.find();
            case PICK -> state = search.pick();
            case APPROACH -> approachTick();
            case ACT -> state = work.act();
            case BREAK -> state = work.breakTick();
            case COLLECT -> collectTick();
            case REPLANT -> state = work.replantTick();
            case DEPOSIT -> {
                if (loot.depositTick()) {
                    outcome = Outcome.SUCCEEDED;
                }
            }
        }
        return outcome;
    }

    private void approachTick() {
        switch (search.approachTick()) {
            case MOVING -> {}
            case ARRIVED -> state = State.ACT;
            case LOST -> {
                work.finishTarget();
                state = State.PICK;
            }
        }
    }

    private void collectTick() {
        switch (loot.tick()) {
            case RUNNING -> {}
            case REPLANT -> state = State.REPLANT;
            case PICK -> {
                work.finishTarget();
                state = State.PICK;
            }
            case FAILED -> fail("не удалось подобрать урожай у " + search.ground() + ": " + loot.failureReason());
        }
    }

    public void cancel() {
        breaker.cancel();
        search.cancelApproach();
        loot.cancelCollector();
        loot.cancelDeposit();
    }

    public Outcome outcome() {
        return outcome;
    }

    public String failureReason() {
        return failureReason;
    }

    public int done() {
        return counters.done();
    }

    public Map<String, Object> saveState() {
        return work.save(counters.done(), state);
    }

    public void restoreState(Map<String, Object> saved) {
        if (saved.get(DONE_KEY) instanceof Number number) {
            counters.restoreDone(number.intValue());
        }
        if (saved.get(REPLANT_KIND_KEY) instanceof String kind
                && saved.get(REPLANT_X_KEY) instanceof Number x
                && saved.get(REPLANT_Y_KEY) instanceof Number y
                && saved.get(REPLANT_Z_KEY) instanceof Number z) {
            search.restoreGround(new BlockCoord(x.intValue(), y.intValue(), z.intValue()));
            work.restoreKind(CropKind.parse(kind));
            loot.startCollect(work.kind(), work.cropPos(), search.ground());
            state = State.COLLECT;
        }
    }

    public int planCalls() {
        return counters.planCalls();
    }

    public int slicesUsed() {
        return targets.slicesUsed();
    }

    public int maxSlicesPerTick() {
        return targets.maxSlicesPerTick();
    }

    public int maxActionsPerTick() {
        return counters.maxActionsPerTick();
    }

    public int maxEntityQueriesPerTick() {
        return counters.maxEntityQueriesPerTick();
    }

    public int entityQueries() {
        return counters.entityQueries();
    }

    public int collectRuns() {
        return counters.collectRuns();
    }

    BlockBreaker.Begin beginUnlimited(BlockPos target) {
        return breaker.beginAnyDistance(target, true);
    }

    private State concludeExhausted() {
        FarmTally.Outcome tallyOutcome = counters.conclusion();
        if (tallyOutcome instanceof FarmTally.Failed failed) {
            fail(failed.text());
            return State.FIND;
        }
        if (job == FarmJob.HARVEST && counters.done() > 0 && !loot.depositTried()) {
            return loot.startDeposit();
        }
        outcome = Outcome.SUCCEEDED;
        return State.FIND;
    }

    private Outcome fail(String reason) {
        outcome = Outcome.FAILED;
        failureReason = reason;
        cancel();
        return outcome;
    }
}
