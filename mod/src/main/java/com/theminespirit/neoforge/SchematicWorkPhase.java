package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.BuildPhase;
import com.theminespirit.core.building.BuildPlanner;
import com.theminespirit.core.building.ReachPlanner;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicActions;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;

final class SchematicWorkPhase {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final BuildSchematicScript host;

    SchematicWorkPhase(BuildSchematicScript host) {
        this.host = host;
    }

    ScriptStatus tick() {
        if (host.outcome != ScriptStatus.RUNNING) {
            return host.outcome;
        }
        if (host.spirit.isRemoved()) {
            return host.report.fail("дух исчез во время стройки по схеме");
        }
        ScriptStatus escaping = host.pit.tickEscape();
        if (escaping == ScriptStatus.RUNNING) {
            return ScriptStatus.RUNNING;
        }
        if (escaping == ScriptStatus.FAILED) {
            return host.report.fail("не удалось выйти из ямы: " + host.pit.escapeFailure());
        }
        host.ticks++;
        if (host.pit.stalled(host.ticks, host.placed, host.cleared, host.trips)) {
            host.pit.stalledReport(host.feet(), host.placed, host.cleared, host.trips, host.blocked);
        }
        host.placement.refreshShare();
        if (host.supplyRunner != null) {
            RunStatus supply = host.supplyRunner.tick();
            if (supply == RunStatus.RUNNING) {
                return ScriptStatus.RUNNING;
            }
            if (supply != RunStatus.SUCCEEDED) {
                LOGGER.info(
                        "Стройка {}: заход {} за материалами закончился {}: {}; дух {}",
                        host.buildKey,
                        host.trips,
                        supply,
                        host.supplyRunner.failureReason().orElse("причина не названа"),
                        host.feet());
                if (host.planFetch != null && host.planFetchRetries < BuildSchematicScript.MAX_PLAN_TRIP_RETRIES) {
                    host.planFetchRetries++;
                    host.retryFetch = host.planFetch;
                }
            }
            host.supplyRunner = null;
            host.planFetch = null;
            host.blocked.clear();
            host.refusals.clear();
            host.standsRetried = false;
        }
        if (host.breaking != null) {
            return breakTick();
        }
        List<ScriptCall> walkThenFetch = host.supplyWalk;
        if (walkThenFetch != null) {
            if (host.approach.isActive() && host.approach.tick() == Approach.State.MOVING) {
                return ScriptStatus.RUNNING;
            }
            host.approach.cancel();
            host.supplyWalk = null;
            return host.startSupply(walkThenFetch);
        }
        if (host.approach.isActive()) {
            return approachTick();
        }
        return planTick();
    }

    private ScriptStatus approachTick() {
        switch (host.approach.tick()) {
            case MOVING -> {
                return ScriptStatus.RUNNING;
            }
            case ARRIVED -> {
                host.excludedStands.add(host.approachStand);
                if (host.spirit.pathBuilds() - host.approachPathMark >= SchematicPlacement.HARD_APPROACH_PATH_BUILDS) {
                    host.pit.hardApproach(
                            host.approachStand,
                            host.approach.refusal(),
                            host.approach.networkNote(),
                            host.spirit.pathBuilds() - host.approachPathMark,
                            "дошёл");
                }
            }
            case STUCK, TIMEOUT -> {
                host.excludedStands.add(host.approachStand);
                host.lastRefusal = "стоянка " + host.approachStand + " недостижима: "
                        + (host.approach.refusal() == null ? "путь не найден" : host.approach.refusal());
                host.pit.hardApproach(
                        host.approachStand,
                        host.approach.refusal(),
                        host.approach.networkNote(),
                        host.spirit.pathBuilds() - host.approachPathMark,
                        "срыв");
            }
        }
        host.approach.cancel();
        host.approachStand = null;
        return planTick();
    }

    private ScriptStatus breakTick() {
        BlockBreaker.Progress progress = host.breaker.tick();
        if (progress == BlockBreaker.Progress.BREAKING) {
            return ScriptStatus.RUNNING;
        }
        if (progress == BlockBreaker.Progress.BROKEN) {
            host.cleared++;
            host.removals.removed(host.breakingId, host.breaking);
        }
        host.breaking = null;
        host.excludedStands.clear();
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus planTick() {
        List<BlockCoord> clear = new ArrayList<>(host.site.toClear());
        clear.removeIf(host.blocked::containsKey);
        if (!clear.isEmpty()) {
            return clearStep(clear);
        }
        if (host.ticks - host.lastPlaceTick < BuildPace.intervalTicks(host.spirit)) {
            return ScriptStatus.RUNNING;
        }
        if (!host.planChecked) {
            host.planPhase.startPlan();
        }
        CompletableFuture<BuildPlanner.Plan> pending = host.planPending;
        if (pending != null) {
            if (host.ticks == host.planStartTick) {
                return ScriptStatus.RUNNING;
            }
            host.planPhase.acceptPlan(pending);
        }
        Optional<ScriptStatus> planned = host.planPhase.step();
        if (planned.isPresent()) {
            return planned.get();
        }
        List<Schematic.PlacedEntry> missing = new ArrayList<>();
        List<Schematic.PlacedEntry> unsupplied = new ArrayList<>();
        boolean waitingForeign = false;
        for (Schematic.PlacedEntry entry : host.site.primaries()) {
            if (SchematicActions.isDecorFlower(entry.block())) {
                continue;
            }
            if (host.site.matches(entry) && host.site.secondPartMatches(entry)) {
                continue;
            }
            if (host.blocked.containsKey(entry.pos())) {
                continue;
            }
            if (host.reservations.foreign(entry.pos(), host.spirit.getUUID())) {
                waitingForeign = true;
                continue;
            }
            if (host.site.rightBlockWrongState(entry)) {
                host.site.repairState(entry, host.spirit);
                return ScriptStatus.RUNNING;
            }
            if (BlockItems.hasBlockItem(entry.block())
                    && !SchematicActions.isActionPlaced(entry.block())
                    && BlockItems.countIn(host.spirit.getInventory(), entry.block()) == 0) {
                unsupplied.add(entry);
                continue;
            }
            missing.add(entry);
        }
        Set<Schematic.PlacedEntry> now = phaseNow(missing, unsupplied);
        missing.retainAll(now);
        unsupplied.retainAll(now);
        if (!unsupplied.isEmpty()) {
            missing.removeIf(entry -> !host.site.supported(entry));
        }
        if (missing.isEmpty() && !unsupplied.isEmpty()) {
            return host.report.noMaterial(unsupplied.get(0).block(), null);
        }
        if (missing.isEmpty()) {
            return waitingForeign && ++host.finishWaits <= BuildSchematicScript.MAX_FINISH_WAITS
                    ? ScriptStatus.RUNNING
                    : host.report.finish();
        }
        List<Schematic.PlacedEntry> main = new ArrayList<>();
        List<Schematic.PlacedEntry> entrances = new ArrayList<>();
        for (Schematic.PlacedEntry entry : missing) {
            (SchematicBlocks.isEntranceBlock(entry.block()) ? entrances : main).add(entry);
        }
        boolean lastStep = main.isEmpty();
        if (lastStep && unsupplied.stream().anyMatch(entry -> !SchematicBlocks.isEntranceBlock(entry.block()))) {
            return host.report.noMaterial(unsupplied.get(0).block(), null);
        }
        List<Schematic.PlacedEntry> targets = host.placement.ownFirst(lastStep ? entrances : main);
        return host.placement.placeStep(targets, lastStep);
    }

    private ScriptStatus clearStep(List<BlockCoord> clear) {
        SpiritReach reach = SpiritReach.current();
        ReachPlanner.World world = host.placement.clearWorld();
        for (BlockCoord cell : ReachPlanner.workableNow(reach, host.feet(), clear, world)) {
            BlockBreaker.Begin begin = host.breaker.beginRepair(SchematicSite.toPos(cell));
            switch (begin) {
                case BlockBreaker.Started ignored -> {
                    host.breaking = cell;
                    host.breakingId = BlockStates.idOf(host.level.getBlockState(SchematicSite.toPos(cell)));
                    return ScriptStatus.RUNNING;
                }
                case BlockBreaker.Protected refused -> host.blocked.put(cell, "очистка запрещена: " + refused.reason());
                case BlockBreaker.Refused refused -> {
                    if (refused.reason() != BlockBreaker.RefuseReason.AIR && !BreakSight.retryable(refused.reason())) {
                        host.blocked.put(cell, "очистка не удалась: " + refused.message());
                    }
                }
            }
        }
        return host.placement.moveTowards(clear, world, false);
    }

    private Set<Schematic.PlacedEntry> phaseNow(
            List<Schematic.PlacedEntry> missing, List<Schematic.PlacedEntry> unsupplied) {
        if (host.bottomY == Integer.MAX_VALUE) {
            for (Schematic.PlacedEntry entry : host.site.primaries()) {
                host.bottomY = Math.min(host.bottomY, entry.pos().y());
            }
        }
        List<Schematic.PlacedEntry> unfinished = new ArrayList<>(missing);
        unfinished.addAll(unsupplied);
        List<Schematic.PlacedEntry> now = BuildPhase.current(
                unfinished,
                entry -> entry.pos().y() - host.bottomY,
                entry -> BuildPhase.rough(entry.block()) && host.site.supported(entry));
        int band = now.size() == unfinished.size()
                ? -1
                : BuildPhase.band(now.get(0).pos().y() - host.bottomY);
        int rank = band < 0 ? Integer.MAX_VALUE : band;
        if (rank > host.highestBand) {
            host.highestBand = rank;
            host.blocked.clear();
            host.refusals.clear();
            host.standsRetried = false;
        }
        host.phaseBand = band;
        return new HashSet<>(now);
    }
}
