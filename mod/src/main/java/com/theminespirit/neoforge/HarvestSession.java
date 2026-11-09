package com.theminespirit.neoforge;

import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.coordinator.WorkKind;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.mining.OreKind;
import com.theminespirit.core.mining.SoilLayer;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

public final class HarvestSession {
    public enum Outcome {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    enum State {
        FIND,
        PICK,
        APPROACH,
        BREAK,
        COLLECT,
        DEPOSIT,
        TUNNEL,
        REARM
    }

    private final SpiritEntity spirit;
    private final HarvestRearm rearm;
    private final BlockBreaker breaker;
    private final int count;
    private final boolean vein;
    private final HarvestCounters counters;
    private final HarvestTunnel tunnel;
    private final HarvestSearch search;
    private final HarvestLoot loot;
    private final HarvestWork work;

    private State state = State.FIND;
    private Outcome outcome = Outcome.RUNNING;
    private String failureReason;

    private HarvestSession(SpiritEntity spirit, HarvestSpec spec) {
        this.spirit = spirit;
        this.rearm = new HarvestRearm(spirit);
        this.breaker = new BlockBreaker(spirit);
        this.count = spec.count();
        this.vein = spec.vein();
        LevelSafetyView safety = new LevelSafetyView(spirit.level());
        this.counters = new HarvestCounters(spec.count());
        this.tunnel = new HarvestTunnel(spirit, spec, safety, counters);
        this.search = new HarvestSearch(spirit, spec, safety, counters, tunnel, this::fail);
        this.loot = new HarvestLoot(spirit, spec, search, breaker, rearm, counters);
        this.work = new HarvestWork(
                spirit, spec, safety, breaker, rearm, search, loot, counters, this::fail, this::beginBreak);
        search.prepareWith(work::prepareBreak);
        search.acceptWith(work::acceptTarget);
    }

    public static HarvestSession mineVein(
            SpiritEntity spirit, OreKind ore, int count, int radius, boolean allowTunnel) {
        Set<Block> ores = BlockSets.resolve(IdFilter.of(List.copyOf(OreKind.blocks(ore))));
        return new HarvestSession(
                spirit,
                new HarvestSpec(
                        new VeinTargets(spirit.level(), spirit.blockPosition(), radius, ores),
                        WorkKind.VEIN,
                        ore.name().toLowerCase(Locale.ROOT),
                        count,
                        radius,
                        ore == OreKind.ANY,
                        true,
                        allowTunnel));
    }

    public static HarvestSession gather(SpiritEntity spirit, String block, int count, int radius) {
        return new HarvestSession(
                spirit,
                new HarvestSpec(
                        new GatherTargets(
                                spirit.level(),
                                spirit.blockPosition(),
                                radius,
                                IdFilter.of(SoilLayer.dirt(block) ? SoilLayer.TURF_BLOCKS : List.of(block)),
                                spirit.home().isPresent()),
                        WorkKind.GATHER,
                        block,
                        count,
                        radius,
                        IdFilter.isPattern(block),
                        false,
                        true));
    }

    public Outcome tick() {
        if (outcome != Outcome.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время добычи");
        }
        if (counters.mined() >= count && !loot.active()) {
            if (loot.retriesEmpty()) {
                outcome = Outcome.SUCCEEDED;
                return outcome;
            }
            loot.beginRetry();
            state = State.COLLECT;
        }
        if (state == State.REARM) {
            if (rearm.tick()) {
                state = State.PICK;
            }
            return outcome;
        }
        counters.tickWatchdogs(vein);
        int limit = counters.stallLimit(vein);
        if (limit >= 0) {
            return fail(HarvestReasonText.stallReason(
                    counters.tally(), limit, searching(), state.name(), search.targetForReason()));
        }
        switch (state) {
            case FIND -> state = search.find();
            case PICK -> state = search.pick();
            case APPROACH -> state = search.approachTick();
            case BREAK -> state = work.breakTick();
            case COLLECT -> collectTick();
            case DEPOSIT -> depositTick();
            case TUNNEL -> state = tunnel.tick();
            case REARM -> {
                if (rearm.tick()) {
                    state = State.PICK;
                }
            }
        }
        return outcome;
    }

    private void collectTick() {
        switch (loot.collectTick()) {
            case RUNNING -> {}
            case REARM -> state = State.REARM;
            case PICK -> state = State.PICK;
            case FAILED -> fail("не удалось подобрать дроп у " + search.target() + ": " + loot.failureReason());
        }
    }

    private void depositTick() {
        switch (loot.depositTick()) {
            case RUNNING -> {}
            case PICK -> state = State.PICK;
            case FAILED -> fail(loot.depositFailureReason());
        }
    }

    public void cancel() {
        work.releaseReserved();
        work.cancelBreaker();
        search.cancelApproach();
        loot.cancelCollector();
        loot.cancelDeposit();
        tunnel.cancel();
        rearm.cancel();
    }

    public void suspend() {
        search.suspendApproach();
        if (state == State.BREAK) {
            work.suspendBreak();
            state = State.PICK;
        }
        loot.suspendCollector();
        loot.suspendDeposit();
        tunnel.suspend();
        rearm.suspend();
    }

    public String failureReason() {
        return failureReason;
    }

    public ScriptInstance.Progress progress() {
        return new ScriptInstance.Progress(Math.min(counters.mined(), count), count);
    }

    public Map<String, Object> saveState() {
        return Map.of("mined", counters.mined());
    }

    public void restoreState(Map<String, Object> state) {
        Object value = state.get("mined");
        if (value instanceof Number number) {
            counters.restoreMined(number.intValue());
        }
    }

    BlockBreaker.Begin beginBreak(BlockCoord target) {
        BlockPos feet = spirit.blockPosition();
        boolean inReach = SpiritReach.reachable(new BlockCoord(feet.getX(), feet.getY(), feet.getZ()), target);
        BlockPos pos = new BlockPos(target.x(), target.y(), target.z());
        return inReach ? breaker.beginAnyDistance(pos, true) : breaker.begin(pos, true);
    }

    private boolean searching() {
        return counters.mined() == 0 && (state == State.FIND || state == State.PICK || state == State.APPROACH);
    }

    private Outcome fail(String reason) {
        outcome = Outcome.FAILED;
        failureReason = reason;
        cancel();
        return outcome;
    }
}
