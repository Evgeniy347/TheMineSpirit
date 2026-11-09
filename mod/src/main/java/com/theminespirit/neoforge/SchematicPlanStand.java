package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.BuildPlanCursor;
import com.theminespirit.core.building.ReachPlanner;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicActions;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;

final class SchematicPlanStand {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final BuildSchematicScript host;
    private final SchematicPlanPhase plan;

    SchematicPlanStand(BuildSchematicScript host, SchematicPlanPhase plan) {
        this.host = host;
        this.plan = plan;
    }

    ScriptStatus standStep(BuildPlanCursor cursor, BuildPlanCursor.StandAt at) {
        if (!host.planStandVisited) {
            host.planStandVisited = true;
            if (!host.feet().equals(at.stand())) {
                if (BuildStandPath.plannedReachable(
                        host.site, at.stand(), BuildSchematicScript.WALK_MARGIN, host.feet())) {
                    return host.beginApproach(at.stand());
                }
                host.lastRefusal = "стоянка плана " + at.stand() + " недостижима пешком от " + host.feet();
                LOGGER.info(
                        "Стройка {}: стоянка плана {} недостижима пешком от {} — шаг {} идёт обычной стройкой",
                        host.buildKey,
                        at.stand(),
                        host.feet(),
                        cursor.index());
            }
        }
        host.planCarried.removeIf(cell -> !plan.pendingCell(cell));
        List<BlockCoord> cells = new ArrayList<>(at.cells());
        for (BlockCoord carried : host.planCarried) {
            if (!cells.contains(carried) && plan.pendingCell(carried)) {
                cells.add(carried);
            }
        }
        Map<BlockCoord, Schematic.PlacedEntry> byCell = new LinkedHashMap<>();
        if (repairStates(cells, byCell)) {
            return ScriptStatus.RUNNING;
        }
        List<Schematic.PlacedEntry> targets = new ArrayList<>(byCell.values());
        PlanPlacing placing = placeFromPlanStand(cells, byCell);
        if (placing.status().isPresent()) {
            return placing.status().get();
        }
        if (placing.overlapped() && ++host.centring <= BuildSchematicScript.MAX_CENTRING_TICKS) {
            BlockCoord here = host.feet();
            host.spirit.getNavigation().stop();
            host.spirit.getMoveControl().setWantedPosition(here.x() + 0.5, host.spirit.getY(), here.z() + 0.5, 1.0);
            return ScriptStatus.RUNNING;
        }
        host.centring = 0;
        List<Schematic.PlacedEntry> supplied = new ArrayList<>();
        Map<Item, Integer> lacking = new LinkedHashMap<>();
        for (Schematic.PlacedEntry entry : targets) {
            if (SchematicActions.isFluidPlaced(entry.block())) continue;
            if (BlockItems.countIn(host.spirit.getInventory(), entry.block()) > 0) {
                supplied.add(entry);
            } else {
                lacking.merge(BlockItems.itemFor(entry.block()), 1, Integer::sum);
            }
        }
        if (supplied.isEmpty() && !lacking.isEmpty() && host.planTopUps < BuildSchematicScript.MAX_PLAN_TOP_UPS) {
            for (Schematic.PlacedEntry entry : host.site.primaries()) {
                if (SchematicActions.isFluidPlaced(entry.block())) continue;
                Item item = BlockItems.itemFor(entry.block());
                if (lacking.containsKey(item) && !host.site.matches(entry)) {
                    lacking.merge(item, 1, Integer::sum);
                }
            }
            List<ScriptCall> steps = BuildSupplyTrip.exact(host.spirit, host.site, lacking);
            if (!steps.isEmpty()) {
                host.planTopUps++;
                host.trips++;
                LOGGER.info(
                        "Стройка {}: дозаход {} — шагу плана {} не хватило {}",
                        host.buildKey,
                        host.trips,
                        cursor.index(),
                        lacking);
                return host.startSupply(steps);
            }
        }
        if (!supplied.isEmpty()) {
            carryRest(cursor, supplied);
            return ScriptStatus.RUNNING;
        }
        deferPlanStep(cursor, at, targets);
        return ScriptStatus.RUNNING;
    }

    private boolean repairStates(List<BlockCoord> cells, Map<BlockCoord, Schematic.PlacedEntry> byCell) {
        for (BlockCoord cell : cells) {
            Schematic.PlacedEntry entry = host.primaryAt.get(cell);
            if (entry == null) {
                continue;
            }
            if (host.site.rightBlockWrongState(entry)) {
                host.site.repairState(entry, host.spirit);
                return true;
            }
            byCell.put(cell, entry);
        }
        return false;
    }

    private void carryRest(BuildPlanCursor cursor, List<Schematic.PlacedEntry> supplied) {
        for (Schematic.PlacedEntry entry : supplied) {
            if (!host.site.matches(entry)) {
                host.planCarried.add(entry.pos());
            }
        }
        plan.nextPlanStep(cursor);
    }

    private record PlanPlacing(Optional<ScriptStatus> status, boolean overlapped) {}

    private PlanPlacing placeFromPlanStand(List<BlockCoord> cells, Map<BlockCoord, Schematic.PlacedEntry> byCell) {
        boolean overlapped = false;
        ReachPlanner.World world = host.placeWorld(byCell, false);
        for (BlockCoord cell : ReachPlanner.workableNow(SpiritReach.current(), host.feet(), cells, world)) {
            Schematic.PlacedEntry entry = byCell.get(cell);
            if (entry == null
                    || (SchematicBlocks.isEntranceBlock(entry.block()) && !host.site.outsideFootprint(host.feet()))) {
                continue;
            }
            if (host.site.overlaps(entry, host.spirit.getBoundingBox())) {
                overlapped = true;
                continue;
            }
            if (!host.site.keepsRetreat(host.feet(), List.of(entry))) {
                host.retreatRefusals++;
                continue;
            }
            if (BlockItems.countIn(host.spirit.getInventory(), entry.block()) == 0) {
                continue;
            }
            Optional<ScriptStatus> placedNow = host.placeOne(entry);
            if (placedNow.isPresent()) {
                return new PlanPlacing(placedNow, overlapped);
            }
        }
        return new PlanPlacing(Optional.empty(), overlapped);
    }

    private void deferPlanStep(
            BuildPlanCursor cursor, BuildPlanCursor.StandAt at, List<Schematic.PlacedEntry> targets) {
        host.planDeviations += at.cells().size();
        Schematic.PlacedEntry first = targets.isEmpty() ? null : targets.get(0);
        LOGGER.info(
                "Стройка {}: шаг плана {} — со стоянки {} не поставлено {} (например {}: свободна {}, "
                        + "опора {}, материал {}; последний отказ: {}), дух {}; клетки — обычной стройке",
                host.buildKey,
                cursor.index(),
                at.stand(),
                at.cells().size(),
                first == null ? "?" : first.block() + "@" + first.pos(),
                first != null && host.site.freeForPlacing(first),
                first != null && host.site.supported(first),
                first == null ? 0 : BlockItems.countIn(host.spirit.getInventory(), first.block()),
                host.lastRefusal.isBlank() ? "нет" : host.lastRefusal,
                host.feet());
        for (BlockCoord cell : at.cells()) {
            host.blocked.putIfAbsent(cell, "план: со стоянки " + at.stand() + " не поставить");
        }
        plan.nextPlanStep(cursor);
    }
}
