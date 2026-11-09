package com.theminespirit.neoforge;

import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.mining.DropRetries;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.pickup.OverflowMode;
import com.theminespirit.core.pickup.PickupTuning;
import com.theminespirit.core.script.CatalogLimits;
import java.util.List;

final class HarvestLoot {
    enum Step {
        RUNNING,
        REARM,
        PICK,
        FAILED
    }

    enum DepositStep {
        RUNNING,
        PICK,
        FAILED
    }

    private static final int COLLECT_RADIUS = 4;

    private final SpiritEntity spirit;
    private final HarvestSpec spec;
    private final HarvestSearch search;
    private final BlockBreaker breaker;
    private final HarvestRearm rearm;
    private final HarvestCounters counters;
    private final DropRetries dropRetries = new DropRetries(COLLECT_RADIUS);

    private DropCollector collector;
    private HarvestDeposit deposit;
    private boolean depositTried;
    private boolean depositFreed;
    private String depositReason;

    HarvestLoot(
            SpiritEntity spirit,
            HarvestSpec spec,
            HarvestSearch search,
            BlockBreaker breaker,
            HarvestRearm rearm,
            HarvestCounters counters) {
        this.spirit = spirit;
        this.spec = spec;
        this.search = search;
        this.breaker = breaker;
        this.rearm = rearm;
        this.counters = counters;
    }

    boolean active() {
        return collector != null;
    }

    boolean retriesEmpty() {
        return dropRetries.isEmpty();
    }

    void beginRetry() {
        search.target(dropRetries.poll());
        search.stand(null);
        collector = newCollector(search.target());
    }

    void begin(BlockCoord center) {
        collector = newCollector(center);
    }

    Step collectTick() {
        DropCollector.State collected = collector.tick();
        if (collected == DropCollector.State.RUNNING) {
            return Step.RUNNING;
        }
        if (collected == DropCollector.State.FAILED) {
            if (counters.mined() >= spec.count()) {
                return Step.FAILED;
            }
            dropRetries.failed(search.target());
            collector = null;
            search.clearTargets();
            return rearm.startPending(counters.mined(), spec.count()) ? Step.REARM : Step.PICK;
        }
        collector = null;
        search.clearTargets();
        if (depositFreed || HarvestDeposit.occupiedSlots(spirit) < CatalogLimits.UNLOAD_TRIGGER_SLOTS) {
            depositTried = false;
            depositFreed = false;
        }
        return rearm.startPending(counters.mined(), spec.count()) ? Step.REARM : Step.PICK;
    }

    String failureReason() {
        return collector.failureReason();
    }

    boolean depositTried() {
        return depositTried;
    }

    HarvestSession.State startDeposit(List<Stack> incoming, boolean mandatory) {
        int toolSlot = breaker.toolSlot();
        breaker.cancel();
        search.cancelApproach();
        depositTried = true;
        depositFreed = false;
        deposit = new HarvestDeposit(spirit, incoming, mandatory, toolSlot);
        search.returnTarget();
        return HarvestSession.State.DEPOSIT;
    }

    DepositStep depositTick() {
        HarvestDeposit.Step step = deposit.tick();
        if (step == HarvestDeposit.Step.RUNNING) {
            return DepositStep.RUNNING;
        }
        depositReason = deposit.failureReason();
        depositFreed = deposit.freed();
        deposit = null;
        if (step == HarvestDeposit.Step.FAILED) {
            return DepositStep.FAILED;
        }
        return DepositStep.PICK;
    }

    String depositFailureReason() {
        return depositReason;
    }

    private DropCollector newCollector(BlockCoord center) {
        return new DropCollector(
                spirit,
                IdFilter.matchAll(),
                center,
                COLLECT_RADIUS,
                PickupTuning.DEFAULT,
                OverflowMode.forSpirit(spirit.isFollowingPlayer()));
    }

    void cancelCollector() {
        if (collector != null) {
            collector.cancel();
        }
    }

    void suspendCollector() {
        if (collector != null) {
            collector.suspend();
        }
    }

    void cancelDeposit() {
        if (deposit != null) {
            deposit.cancel();
        }
    }

    void suspendDeposit() {
        if (deposit != null) {
            deposit.suspend();
        }
    }
}
