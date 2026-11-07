package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.BuildPlanCursor;
import com.theminespirit.core.building.BuildPlanner;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.BuildTuning;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

final class SchematicPlanPhase {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final BuildSchematicScript host;
    private final SchematicPlanStand stand;

    SchematicPlanPhase(BuildSchematicScript host) {
        this.host = host;
        this.stand = new SchematicPlanStand(host, this);
    }

    void startPlan() {
        host.planChecked = true;
        if (host.squad.size() > 1) {
            return;
        }
        host.planBegan = System.nanoTime();
        host.planStartTick = host.ticks;
        BuildPlanStart.Prepared prepared = BuildPlanStart.of(host.spirit, host.site);
        Map<BlockCoord, Schematic.PlacedEntry> byCell = new HashMap<>();
        host.site.primaries().forEach(entry -> byCell.put(entry.pos(), entry));
        host.primaryAt = Map.copyOf(byCell);
        host.planItems = prepared.items();
        host.planSupply = prepared.supply();
        Schematic schematic = host.site.schematic();
        BuildRotation rotation = host.site.rotation();
        SpiritReach reach = SpiritReach.current();
        host.planPending = CompletableFuture.supplyAsync(
                () -> BuildPlanner.plan(
                        schematic, rotation, prepared.supply(), reach, BuildPlanner.Portion.FILL, prepared.start()),
                Util.backgroundExecutor());
    }

    void acceptPlan(CompletableFuture<BuildPlanner.Plan> pending) {
        host.planPending = null;
        BuildPlanner.Plan built;
        try {
            built = pending.join();
        } catch (CompletionException | CancellationException e) {
            LOGGER.warn("Стройка {}: план не построен ({}) — обычная стройка", host.buildKey, e.getMessage());
            return;
        }
        host.planStands = built.stands();
        host.planTrips = built.trips();
        host.plan = new BuildPlanCursor(built, host.site.origin());
        LOGGER.info(
                "Стройка {}: план — стоянок {}, заходов {}, не поставлено {}, сундук снабжения {} (от участка {}), "
                        + "расчёт {} мс",
                host.buildKey,
                host.planStands,
                host.planTrips,
                built.stuck(),
                host.planSupply.plus(host.site.origin()),
                host.site.origin(),
                (System.nanoTime() - host.planBegan) / 1_000_000);
    }

    Optional<ScriptStatus> step() {
        BuildPlanCursor cursor = host.plan;
        if (cursor == null) {
            return Optional.empty();
        }
        return planStep(cursor);
    }

    boolean pendingCell(BlockCoord cell) {
        Schematic.PlacedEntry entry = host.primaryAt.get(cell);
        return entry != null
                && !host.blocked.containsKey(cell)
                && !(host.site.matches(entry) && host.site.secondPartMatches(entry));
    }

    private Optional<ScriptStatus> planStep(BuildPlanCursor cursor) {
        BuildPlanCursor.Fetch again = host.retryFetch;
        if (again != null) {
            host.retryFetch = null;
            return Optional.of(planFetch(again));
        }
        BuildPlanCursor.Next next = cursor.next(this::pendingCell);
        switch (next) {
            case BuildPlanCursor.Done ignored -> {
                LOGGER.info(
                        "Стройка {}: план исполнен — стоянок {} (план {}), заходов {} (план {}), клеток на обычную "
                                + "стройку {}",
                        host.buildKey,
                        host.standsChosen,
                        host.planStands,
                        host.trips,
                        host.planTrips,
                        host.planDeviations);
                leavePlan();
                return Optional.empty();
            }
            case BuildPlanCursor.Fetch fetch -> {
                nextPlanStep(cursor);
                host.planFetchRetries = 0;
                return Optional.of(planFetch(fetch));
            }
            case BuildPlanCursor.StandAt at -> {
                if (at.cells().stream()
                                .allMatch(cell -> SchematicBlocks.isEntranceBlock(
                                        host.primaryAt.get(cell).block()))
                        && innerLeft()) {
                    LOGGER.info(
                            "Стройка {}: до дверей плана остались клетки внутри — дальше обычная стройка",
                            host.buildKey);
                    leavePlan();
                    return Optional.empty();
                }
                return Optional.of(stand.standStep(cursor, at));
            }
        }
    }

    private void leavePlan() {
        host.plan = null;
        host.planDeviations += host.planCarried.size();
        host.planCarried.clear();
        host.blocked.clear();
        host.refusals.clear();
        host.standsRetried = false;
    }

    private boolean innerLeft() {
        for (Schematic.PlacedEntry entry : host.site.primaries()) {
            if (!SchematicBlocks.isEntranceBlock(entry.block())
                    && !(host.site.matches(entry) && host.site.secondPartMatches(entry))) {
                return true;
            }
        }
        return false;
    }

    private ScriptStatus planFetch(BuildPlanCursor.Fetch fetch) {
        host.planFetch = fetch;
        Map<Item, Integer> wanted = new LinkedHashMap<>();
        for (BuildPlanner.Taken taken : fetch.taken()) {
            Item item = host.planItems.get(taken.item());
            if (item != null) {
                wanted.merge(item, taken.count(), Integer::sum);
            }
        }
        List<ScriptCall> steps = BuildSupplyTrip.exact(host.spirit, host.site, wanted);
        if (steps.isEmpty()) {
            host.planFetch = null;
            return ScriptStatus.RUNNING;
        }
        host.trips++;
        LOGGER.info(
                "Стройка {}: заход {} по плану (заход плана {}, {} шагов withdraw), поставлено {} из {}",
                host.buildKey,
                host.trips,
                fetch.number(),
                steps.size(),
                host.placed,
                host.site.primaries().size());
        BlockCoord origin = host.site.origin();
        BlockCoord supply = new BlockCoord(
                origin.x() + host.planSupply.x(), origin.y() + host.planSupply.y(), origin.z() + host.planSupply.z());
        if (host.feet().equals(supply)) {
            return host.startSupply(steps);
        }
        host.supplyWalk = steps;
        host.approach.begin(new Vec3(supply.x() + 0.5, supply.y(), supply.z() + 0.5), BuildTuning.STAND_ARRIVAL);
        return ScriptStatus.RUNNING;
    }

    void nextPlanStep(BuildPlanCursor cursor) {
        cursor.advance();
        host.planStandVisited = false;
        host.centring = 0;
    }
}
