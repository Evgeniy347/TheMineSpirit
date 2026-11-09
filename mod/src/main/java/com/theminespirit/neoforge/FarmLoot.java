package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.farming.CropKind;
import com.theminespirit.core.farming.FarmTuning;
import com.theminespirit.core.farming.HarvestMethod;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.pickup.OverflowMode;
import com.theminespirit.core.pickup.PickupTuning;
import com.theminespirit.neoforge.inventory.UnloadScript;
import java.util.List;

final class FarmLoot {
    enum Step {
        RUNNING,
        REPLANT,
        PICK,
        FAILED
    }

    private static final int COLLECT_RADIUS = 3;
    private static final PickupTuning PICK_PICKUP = new PickupTuning(
            PickupTuning.REFRESH_TICKS,
            FarmTuning.REACH,
            PickupTuning.TOTAL_TIMEOUT_TICKS,
            PickupTuning.MAX_DELAY_WAIT_TICKS);
    private static final String FARM_ACTIVITY = "farming";

    private final SpiritEntity spirit;
    private final FarmSearch search;
    private final BlockBreaker breaker;
    private final FarmCounters counters;
    private final boolean replant;

    private CropKind kind;
    private DropCollector collector;
    private UnloadScript deposit;
    private boolean depositTried;

    FarmLoot(SpiritEntity spirit, FarmSearch search, BlockBreaker breaker, FarmCounters counters, boolean replant) {
        this.spirit = spirit;
        this.search = search;
        this.breaker = breaker;
        this.counters = counters;
        this.replant = replant;
    }

    void startCollect(CropKind collected, BlockCoord cropPos, BlockCoord ground) {
        kind = collected;
        counters.collectRun();
        IdFilter harvested = IdFilter.of(List.of(kind.produceItem(), kind.plantItem()));
        boolean column = kind.harvestMethod() == HarvestMethod.BREAK_COLUMN;
        PickupTuning tuning = kind.harvestMethod() == HarvestMethod.PICK || column ? PICK_PICKUP : PickupTuning.DEFAULT;
        BlockCoord center = column ? FarmSearch.coordOf(spirit.blockPosition()) : cropPos != null ? cropPos : ground;
        collector = new DropCollector(
                spirit, harvested, center, COLLECT_RADIUS, tuning, OverflowMode.forSpirit(spirit.isFollowingPlayer()));
    }

    Step tick() {
        int before = collector.entityQueries();
        DropCollector.State collected = collector.tick();
        counters.observeEntityQueries(collector.entityQueries(), before);
        if (collected == DropCollector.State.RUNNING) {
            return Step.RUNNING;
        }
        counters.addEntityQueries(collector.entityQueries());
        if (collected == DropCollector.State.FAILED) {
            return Step.FAILED;
        }
        collector = null;
        if (replant && kind.harvestMethod() == HarvestMethod.BREAK) {
            return Step.REPLANT;
        }
        return Step.PICK;
    }

    String failureReason() {
        return collector.failureReason();
    }

    boolean depositTried() {
        return depositTried;
    }

    FarmSession.State startDeposit() {
        depositTried = true;
        breaker.cancel();
        search.cancelApproach();
        if (collector != null) {
            collector.cancel();
            collector = null;
        }
        deposit = UnloadScript.reliefFor(spirit, -1, FARM_ACTIVITY);
        return FarmSession.State.DEPOSIT;
    }

    boolean depositTick() {
        ScriptStatus status = deposit.tick();
        if (status == ScriptStatus.RUNNING) {
            return false;
        }
        deposit = null;
        return true;
    }

    void cancelCollector() {
        if (collector != null) {
            collector.cancel();
        }
    }

    void cancelDeposit() {
        if (deposit != null) {
            deposit.cancel();
        }
    }
}
