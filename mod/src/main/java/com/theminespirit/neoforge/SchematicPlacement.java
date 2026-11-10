package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.BuildPit;
import com.theminespirit.core.building.BuildStandLevel;
import com.theminespirit.core.building.BuildTuning;
import com.theminespirit.core.building.CellPartition;
import com.theminespirit.core.building.ReachPlanner;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicActions;
import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.engine.ConditionRegistry;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.engine.TaskRunner;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

final class SchematicPlacement {
    static final int HARD_APPROACH_PATH_BUILDS = 4;
    private static final Logger LOGGER = LogUtils.getLogger();

    private final BuildSchematicScript host;

    SchematicPlacement(BuildSchematicScript host) {
        this.host = host;
    }

    ScriptStatus placeStep(List<Schematic.PlacedEntry> targets, boolean lastStep) {
        Map<BlockCoord, Schematic.PlacedEntry> byCell = new LinkedHashMap<>();
        for (Schematic.PlacedEntry entry : targets) {
            byCell.put(entry.pos(), entry);
        }
        List<BlockCoord> cells = List.copyOf(byCell.keySet());
        ReachPlanner.World world = placeWorld(byCell, lastStep);
        boolean mayPlaceHere = !lastStep || host.site.outsideFootprint(host.feet());
        List<Schematic.PlacedEntry> trapping = new ArrayList<>();
        boolean overlapped = false;
        if (mayPlaceHere) {
            for (BlockCoord cell : ReachPlanner.workableNow(SpiritReach.current(), host.feet(), cells, world)) {
                Schematic.PlacedEntry entry = byCell.get(cell);
                if (host.site.overlaps(entry, host.spirit.getBoundingBox())) {
                    overlapped = true;
                    continue;
                }
                if (!host.site.keepsRetreat(host.feet(), List.of(entry))) {
                    trapping.add(entry);
                    host.retreatRefusals++;
                    continue;
                }
                Optional<ScriptStatus> placedNow = placeOne(entry);
                if (placedNow.isPresent()) {
                    return placedNow.get();
                }
            }
        }
        if (trapping.isEmpty() && overlapped) {
            if (++host.centring <= BuildSchematicScript.MAX_CENTRING_TICKS) {
                BlockCoord here = host.feet();
                host.spirit.getNavigation().stop();
                host.spirit.getMoveControl().setWantedPosition(here.x() + 0.5, host.spirit.getY(), here.z() + 0.5, 1.0);
                return ScriptStatus.RUNNING;
            }
            host.centring = 0;
            host.excludedStands.add(host.feet());
            return moveTowards(cells, world, lastStep);
        }
        if (trapping.isEmpty()) {
            return moveTowards(cells, world, lastStep);
        }
        List<BlockCoord> trapped = new ArrayList<>();
        for (Schematic.PlacedEntry entry : trapping) {
            trapped.add(entry.pos());
        }
        ReachPlanner.World safeWorld = retreatWorld(world, trapping);
        if (BuildStandPath.choose(
                        trapped,
                        safeWorld,
                        host.excludedStands,
                        new HashSet<>(host.site.schemeCells()),
                        lastStep,
                        host.feet())
                .isEmpty()) {
            LOGGER.warn(
                    "Стройка '{}': блок {} запирает духа, а стоянки с путём наружу нет — ставлю с места",
                    host.buildKey,
                    trapped.get(0));
            return placeOne(trapping.get(0)).orElse(ScriptStatus.RUNNING);
        }
        return moveTowards(trapped, safeWorld, lastStep);
    }

    private ReachPlanner.World retreatWorld(ReachPlanner.World world, List<Schematic.PlacedEntry> trapping) {
        return new ReachPlanner.World() {
            @Override
            public boolean ready(BlockCoord cell) {
                return world.ready(cell);
            }

            @Override
            public boolean standable(BlockCoord feet) {
                return world.standable(feet) && host.site.keepsRetreat(feet, trapping);
            }
        };
    }

    Optional<ScriptStatus> placeOne(Schematic.PlacedEntry entry) {
        if (SchematicActions.isFluidPlaced(entry.block())) {
            return waterOne(entry);
        }
        BlockCoord cell = entry.pos();
        BlockCoord weed = SchematicWild.entryWeed(host.level, host.breaker, entry);
        if (weed != null) {
            host.breaking = weed;
            host.breakingId = BlockStates.idOf(host.level.getBlockState(SchematicSite.toPos(weed)));
            return Optional.of(ScriptStatus.RUNNING);
        }
        if (BuildMakeWay.selfIn(host.spirit, cell)) {
            host.lastRefusal = "строитель стоит в клетке " + cell + " — сначала отойти";
            return BuildMakeWay.outsideStand(host.site, host.feet()).map(this::beginApproach);
        }
        if (!BuildMakeWay.clear(host.spirit, host.level, host.site, host.buildKey, cell)) {
            host.lastRefusal = "клетку " + cell + " занимает живое существо: сначала отойти";
            return Optional.empty();
        }
        host.reservations.reserve(cell, host.spirit.getUUID());
        BlockPlacer.Begin begin = host.placer.begin(SchematicSite.toPos(cell), host.site.wanted(entry), entry.block());
        host.reservations.release(cell, host.spirit.getUUID());
        switch (begin) {
            case BlockPlacer.Started started -> {
                if (!entry.text().isBlank()) {
                    SignTexts.write(host.level, started.pos(), entry.text());
                }
                host.placed++;
                host.centring = 0;
                host.tally.placed(entry.block());
                host.lastPlaceTick = host.ticks;
                host.refusals.remove(cell);
                host.excludedStands.clear();
                host.standsRetried = false;
                return Optional.of(ScriptStatus.RUNNING);
            }
            case BlockPlacer.Protected refused -> {
                host.blocked.put(cell, "запрещено: " + refused.reason());
                host.lastRefusal = "запрещено: " + refused.reason();
                return Optional.empty();
            }
            case BlockPlacer.Refused refused -> {
                host.lastRefusal = refused.reason() + ": " + refused.message();
                if (refused.reason() == BlockPlacer.RefuseReason.NO_MATERIAL) {
                    return Optional.of(host.report.noMaterial(entry.block(), refused.message()));
                }
                int attempts = host.refusals.merge(cell, 1, Integer::sum);
                if (attempts >= BuildTuning.MAX_CONSECUTIVE_SKIPS) {
                    host.blocked.put(cell, host.lastRefusal);
                }
                return Optional.empty();
            }
        }
    }

    private Optional<ScriptStatus> waterOne(Schematic.PlacedEntry entry) {
        BuildWater.Plan water = BuildWater.plan(host.spirit, host.site, entry, host.buildKey);
        if (water.task().isEmpty()) {
            host.blocked.put(entry.pos(), water.reason());
            host.lastRefusal = water.reason();
            return Optional.empty();
        }
        host.approach.cancel();
        host.approachStand = null;
        host.supplyRunner = TaskRunner.start(
                water.task().orElseThrow(), SpiritScripts.registryFor(host.spirit), ConditionRegistry.of(Map.of()));
        return Optional.of(ScriptStatus.RUNNING);
    }

    ScriptStatus moveTowards(List<BlockCoord> cells, ReachPlanner.World base, boolean outsideOnly) {
        if (BuildPit.needsEscape(host.feet().y(), host.site.origin().y(), host.pit::trapped)) {
            host.pit.escape(host.feet());
            return ScriptStatus.RUNNING;
        }
        ReachPlanner.World stands =
                BuildStandLevel.atOrAbove(base, host.site.origin().y() - 1);
        Set<BlockCoord> occupied = SchematicWalkways.free(host.site.primaries(), host.site.schemeCells());
        Optional<Set<BlockCoord>> reachable = host.site.walkable(host.feet(), BuildSchematicScript.WALK_MARGIN);
        boolean walkKnown =
                host.site.networkBox(BuildSchematicScript.WALK_MARGIN).contains(host.feet()) && reachable.isPresent();
        ReachPlanner.World world = walkKnown ? BuildStandPath.walkableOnly(stands, reachable.orElseThrow()) : stands;
        Optional<BlockCoord> stand =
                BuildStandPath.choose(cells, world, host.excludedStands, occupied, outsideOnly, host.feet());
        if (stand.isEmpty()) {
            Set<BlockCoord> blockers = SchematicWalkways.free(host.site.primaries(), host.site.standBlockers());
            stand = BuildStandPath.choose(cells, world, host.excludedStands, blockers, outsideOnly, host.feet());
        }
        if (stand.isEmpty() && !host.standsRetried && !host.excludedStands.isEmpty()) {
            host.standsRetried = true;
            host.excludedStands.clear();
            stand = BuildStandPath.choose(cells, world, host.excludedStands, occupied, outsideOnly, host.feet());
        }
        if (stand.isEmpty()) {
            for (BlockCoord cell : cells) {
                host.blocked.putIfAbsent(
                        cell,
                        "ни с одной стоянки не дотянуться" + (outsideOnly ? " снаружи постройки" : "")
                                + (walkKnown ? " пешком от " + host.feet() : "")
                                + (host.lastRefusal.isBlank() ? "" : "; " + host.lastRefusal));
            }
            return ScriptStatus.RUNNING;
        }
        return beginApproach(stand.get());
    }

    ScriptStatus beginApproach(BlockCoord stand) {
        host.approachStand = stand;
        host.pit.descent(host.feet(), stand);
        host.approachPathMark = host.spirit.pathBuilds();
        host.standsChosen++;
        if (host.site.onRoof(stand)) {
            host.roofStands++;
        }
        host.approach.beginWithRoutes(new Vec3(stand.x() + 0.5, stand.y(), stand.z() + 0.5), BuildTuning.STAND_ARRIVAL);
        return ScriptStatus.RUNNING;
    }

    Reset clearWorld() {
        return new Reset(host);
    }

    ReachPlanner.World placeWorld(Map<BlockCoord, Schematic.PlacedEntry> byCell, boolean outsideOnly) {
        return new ReachPlanner.World() {
            @Override
            public boolean ready(BlockCoord cell) {
                Schematic.PlacedEntry entry = byCell.get(cell);
                return entry != null && host.site.freeForPlacing(entry) && host.site.supported(entry);
            }

            @Override
            public boolean standable(BlockCoord feet) {
                return host.site.standable(feet) && (!outsideOnly || host.site.outsideFootprint(feet));
            }
        };
    }

    List<Schematic.PlacedEntry> ownFirst(List<Schematic.PlacedEntry> targets) {
        List<Schematic.PlacedEntry> own = new ArrayList<>();
        List<Schematic.PlacedEntry> others = new ArrayList<>();
        for (Schematic.PlacedEntry entry : targets) {
            (host.ownShare.contains(entry.pos()) ? own : others).add(entry);
        }
        own.addAll(others);
        return own;
    }

    void refreshShare() {
        if (host.squad.slotOf(host.spirit.getUUID()) < 0) {
            host.squad.join(host.spirit.getUUID());
        }
        if (host.squad.version() == host.squadVersion) {
            return;
        }
        host.squadVersion = host.squad.version();
        Set<BlockCoord> share = new HashSet<>();
        for (Schematic.PlacedEntry entry : CellPartition.split(host.site.primaries(), host.squad.size())
                .get(host.squad.slotOf(host.spirit.getUUID()))) {
            share.add(entry.pos());
        }
        host.ownShare = Set.copyOf(share);
    }

    static final class Reset implements ReachPlanner.World {
        private final BuildSchematicScript host;

        Reset(BuildSchematicScript host) {
            this.host = host;
        }

        @Override
        public boolean ready(BlockCoord cell) {
            return true;
        }

        @Override
        public boolean standable(BlockCoord feet) {
            return host.site.standable(feet);
        }
    }
}
