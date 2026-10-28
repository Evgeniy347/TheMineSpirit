package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.BuildKey;
import com.theminespirit.core.building.BuildPhase;
import com.theminespirit.core.building.BuildPlanCursor;
import com.theminespirit.core.building.BuildPlanner;
import com.theminespirit.core.building.BuildReservations;
import com.theminespirit.core.building.BuildResult;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.BuildSquad;
import com.theminespirit.core.building.BuildState;
import com.theminespirit.core.building.BuildTuning;
import com.theminespirit.core.building.CellPartition;
import com.theminespirit.core.building.ReachPlanner;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.building.SchemeFilter;
import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.engine.ConditionRegistry;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.engine.TaskRunner;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.policy.RemovalLog;
import com.theminespirit.core.script.BuildSchematicParams;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import com.theminespirit.neoforge.movement.PathNetworkService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class BuildSchematicScript implements ScriptInstance {
    private static final int MAX_OWN_CRAFTS = 8;
    private static final int TORCHES_PER_COAL = 4;
    private static final int MAX_FINISH_WAITS = 200;
    private static final int MAX_CENTRING_TICKS = 20;
    private static final int SITE_NETWORK_RING = 6;
    private static final int WALK_MARGIN = 6;
    private static final int MAX_PLAN_TRIP_RETRIES = 2;
    private static final int MAX_PLAN_TOP_UPS = 8;
    private static final int HARD_APPROACH_PATH_BUILDS = 4;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAKE_WAY_RADIUS = 6;
    private final SpiritEntity spirit;
    private final Level level;
    private final SchematicSite site;
    private final BlockPlacer placer;
    private final BlockBreaker breaker;
    private final Approach approach;
    private final BuildReservations reservations;
    private final BuildKey buildKey;
    private final BuildSquad squad;
    private final BuildResult tally = new BuildResult();
    private final Set<BlockCoord> excludedStands = new HashSet<>();
    private final Map<BlockCoord, String> blocked = new LinkedHashMap<>();
    private final Map<BlockCoord, Integer> refusals = new HashMap<>();

    private Set<BlockCoord> ownShare = Set.of();
    private long squadVersion = -1;
    private BlockCoord approachStand;
    private BlockCoord breaking;
    private String breakingId = "-";
    private final RemovalLog removals = new RemovalLog();
    private long ticks;
    private long lastPlaceTick = Long.MIN_VALUE / 2;
    private int placed;
    private int cleared;
    private int failed;
    private int finishWaits;
    private boolean standsRetried;
    private int bottomY = Integer.MAX_VALUE;
    private ScriptStatus outcome = ScriptStatus.RUNNING;
    private String failureReason;
    private String lastRefusal = "";
    private TaskRunner supplyRunner;
    private int crafts;
    private int trips;
    private int standsChosen;
    private int roofStands;
    private int retreatRefusals;
    private int centring;
    private int phaseBand = -1;
    private int highestBand = -1;
    private @Nullable BuildPlanCursor plan;
    private boolean planChecked;
    private @Nullable CompletableFuture<BuildPlanner.Plan> planPending;
    private long planStartTick;
    private long planBegan;
    private BlockCoord planSupply = new BlockCoord(0, 0, 0);
    private Map<String, Item> planItems = Map.of();
    private Map<BlockCoord, Schematic.PlacedEntry> primaryAt = Map.of();
    private boolean planStandVisited;
    private BuildPlanCursor.@Nullable Fetch planFetch;
    private BuildPlanCursor.@Nullable Fetch retryFetch;
    private int planFetchRetries;
    private int planTopUps;
    private @Nullable List<ScriptCall> supplyWalk;
    private int approachPathMark;
    private final Set<BlockCoord> planCarried = new HashSet<>();
    private int planPlacedMark;
    private long planStands;
    private long planTrips;
    private int planDeviations;

    BuildSchematicScript(SpiritEntity spirit, BuildSchematicParams params) {
        this(
                spirit,
                SchemeFilter.only(
                        params.wood() == null
                                ? SchematicResources.load(params.file())
                                : WoodSpecies.swap(SchematicResources.load(params.file()), params.wood()),
                        params.only()),
                params.file(),
                new BlockCoord(params.x(), params.y(), params.z()),
                params.rotation());
    }

    BuildSchematicScript(
            SpiritEntity spirit, Schematic schematic, String file, BlockCoord origin, BuildRotation rotation) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.site = new SchematicSite(level, schematic, origin, rotation);
        this.placer = new BlockPlacer(spirit);
        this.breaker = new BlockBreaker(spirit);
        this.approach = new Approach(spirit);
        this.reservations = BuildReservationHub.forLevel(level);
        this.buildKey = new BuildKey(file, origin, rotation);
        this.squad = BuildSquadRegistry.join(level, buildKey, spirit.getUUID());
        if (level instanceof ServerLevel server) {
            PathNetworkService.beginSite(server, buildKey, site.networkBox(SITE_NETWORK_RING), site.doorCells());
        }
    }

    @Override
    public ScriptStatus tick() {
        if (outcome != ScriptStatus.RUNNING) {
            return outcome;
        }
        if (spirit.isRemoved()) {
            return fail("дух исчез во время стройки по схеме");
        }
        ticks++;
        refreshShare();
        if (supplyRunner != null) {
            RunStatus supply = supplyRunner.tick();
            if (supply == RunStatus.RUNNING) {
                return ScriptStatus.RUNNING;
            }
            if (supply != RunStatus.SUCCEEDED) {
                LOGGER.info(
                        "Стройка {}: заход {} за материалами закончился {}: {}; дух {}",
                        buildKey,
                        trips,
                        supply,
                        supplyRunner.failureReason().orElse("причина не названа"),
                        feet());
                if (planFetch != null && planFetchRetries < MAX_PLAN_TRIP_RETRIES) {
                    planFetchRetries++;
                    retryFetch = planFetch;
                }
            }
            supplyRunner = null;
            planFetch = null;
            blocked.clear();
            refusals.clear();
            standsRetried = false;
        }
        if (breaking != null) {
            return breakTick();
        }
        List<ScriptCall> walkThenFetch = supplyWalk;
        if (walkThenFetch != null) {
            if (approach.isActive() && approach.tick() == Approach.State.MOVING) {
                return ScriptStatus.RUNNING;
            }
            approach.cancel();
            supplyWalk = null;
            return startSupply(walkThenFetch);
        }
        if (approach.isActive()) {
            return approachTick();
        }
        return planTick();
    }

    private ScriptStatus approachTick() {
        switch (approach.tick()) {
            case MOVING -> {
                return ScriptStatus.RUNNING;
            }
            case ARRIVED -> {
                excludedStands.add(approachStand);
                if (spirit.pathBuilds() - approachPathMark >= HARD_APPROACH_PATH_BUILDS) {
                    noteHardApproach("дошёл");
                }
            }
            case STUCK, TIMEOUT -> {
                excludedStands.add(approachStand);
                lastRefusal = "стоянка " + approachStand + " недостижима: "
                        + (approach.refusal() == null ? "путь не найден" : approach.refusal());
                noteHardApproach("срыв");
            }
        }
        approach.cancel();
        approachStand = null;
        return planTick();
    }

    private void noteHardApproach(String outcome) {
        BlockCoord stand = approachStand;
        if (stand == null) {
            return;
        }
        BlockPos below = SchematicSite.toPos(new BlockCoord(stand.x(), stand.y() - 1, stand.z()));
        LOGGER.info(
                "Стройка {}: подход {} к стоянке {} (пол {}), построений пути {}, дух {} в {} от стоянки; {}",
                buildKey,
                outcome,
                stand,
                level.getBlockState(below).getBlock().getDescriptionId(),
                spirit.pathBuilds() - approachPathMark,
                spirit.position(),
                String.format(
                        Locale.ROOT,
                        "%.2f",
                        spirit.position().distanceTo(new Vec3(stand.x() + 0.5, stand.y(), stand.z() + 0.5))),
                (approach.refusal() == null ? "без отказа" : approach.refusal()) + "; " + approach.networkNote());
    }

    private ScriptStatus breakTick() {
        BlockBreaker.Progress progress = breaker.tick();
        if (progress == BlockBreaker.Progress.BREAKING) {
            return ScriptStatus.RUNNING;
        }
        if (progress == BlockBreaker.Progress.BROKEN) {
            cleared++;
            removals.removed(breakingId, breaking);
        }
        breaking = null;
        excludedStands.clear();
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus planTick() {
        List<BlockCoord> clear = new ArrayList<>(site.toClear());
        clear.removeIf(blocked::containsKey);
        if (!clear.isEmpty()) {
            return clearStep(clear);
        }
        if (ticks - lastPlaceTick < BuildPace.intervalTicks(spirit)) {
            return ScriptStatus.RUNNING;
        }
        if (!planChecked) {
            startPlan();
        }
        CompletableFuture<BuildPlanner.Plan> pending = planPending;
        if (pending != null) {
            if (ticks == planStartTick) {
                return ScriptStatus.RUNNING;
            }
            acceptPlan(pending);
        }
        BuildPlanCursor cursor = plan;
        if (cursor != null) {
            Optional<ScriptStatus> planned = planStep(cursor);
            if (planned.isPresent()) {
                return planned.get();
            }
        }
        List<Schematic.PlacedEntry> missing = new ArrayList<>();
        List<Schematic.PlacedEntry> unsupplied = new ArrayList<>();
        boolean waitingForeign = false;
        for (Schematic.PlacedEntry entry : site.primaries()) {
            if (site.matches(entry) && site.secondPartMatches(entry)) {
                continue;
            }
            if (blocked.containsKey(entry.pos())) {
                continue;
            }
            if (reservations.foreign(entry.pos(), spirit.getUUID())) {
                waitingForeign = true;
                continue;
            }
            if (site.rightBlockWrongState(entry)) {
                site.repairState(entry);
                return ScriptStatus.RUNNING;
            }
            if (BlockItems.hasBlockItem(entry.block())
                    && BlockItems.countIn(spirit.getInventory(), entry.block()) == 0) {
                unsupplied.add(entry);
                continue;
            }
            missing.add(entry);
        }
        Set<Schematic.PlacedEntry> now = phaseNow(missing, unsupplied);
        missing.retainAll(now);
        unsupplied.retainAll(now);
        if (!unsupplied.isEmpty()) {
            missing.removeIf(entry -> !site.supported(entry));
        }
        if (missing.isEmpty() && !unsupplied.isEmpty()) {
            return noMaterial(unsupplied.get(0).block());
        }
        if (missing.isEmpty()) {
            return waitingForeign && ++finishWaits <= MAX_FINISH_WAITS ? ScriptStatus.RUNNING : finish();
        }
        List<Schematic.PlacedEntry> main = new ArrayList<>();
        List<Schematic.PlacedEntry> entrances = new ArrayList<>();
        for (Schematic.PlacedEntry entry : missing) {
            (SchematicBlocks.isEntranceBlock(entry.block()) ? entrances : main).add(entry);
        }
        boolean lastStep = main.isEmpty();
        if (lastStep && unsupplied.stream().anyMatch(entry -> !SchematicBlocks.isEntranceBlock(entry.block()))) {
            return noMaterial(unsupplied.get(0).block());
        }
        List<Schematic.PlacedEntry> targets = ownFirst(lastStep ? entrances : main);
        return placeStep(targets, lastStep);
    }

    private ScriptStatus clearStep(List<BlockCoord> clear) {
        SpiritReach reach = SpiritReach.current();
        ReachPlanner.World world = clearWorld();
        for (BlockCoord cell : ReachPlanner.workableNow(reach, feet(), clear, world)) {
            BlockBreaker.Begin begin = breaker.begin(SchematicSite.toPos(cell), BlockBreaker.Intent.DEMOLISH, false);
            switch (begin) {
                case BlockBreaker.Started ignored -> {
                    breaking = cell;
                    breakingId = BlockStates.idOf(level.getBlockState(SchematicSite.toPos(cell)));
                    return ScriptStatus.RUNNING;
                }
                case BlockBreaker.Protected refused -> blocked.put(cell, "очистка запрещена: " + refused.reason());
                case BlockBreaker.Refused refused -> {
                    if (refused.reason() != BlockBreaker.RefuseReason.AIR && !BreakSight.retryable(refused.reason())) {
                        blocked.put(cell, "очистка не удалась: " + refused.message());
                    }
                }
            }
        }
        return moveTowards(clear, world, false);
    }

    private Set<Schematic.PlacedEntry> phaseNow(
            List<Schematic.PlacedEntry> missing, List<Schematic.PlacedEntry> unsupplied) {
        if (bottomY == Integer.MAX_VALUE) {
            for (Schematic.PlacedEntry entry : site.primaries()) {
                bottomY = Math.min(bottomY, entry.pos().y());
            }
        }
        List<Schematic.PlacedEntry> unfinished = new ArrayList<>(missing);
        unfinished.addAll(unsupplied);
        List<Schematic.PlacedEntry> now = BuildPhase.current(
                unfinished,
                entry -> entry.pos().y() - bottomY,
                entry -> BuildPhase.rough(entry.block()) && site.supported(entry));
        int band = now.size() == unfinished.size()
                ? -1
                : BuildPhase.band(now.get(0).pos().y() - bottomY);
        int rank = band < 0 ? Integer.MAX_VALUE : band;
        if (rank > highestBand) {
            highestBand = rank;
            blocked.clear();
            refusals.clear();
            standsRetried = false;
        }
        phaseBand = band;
        return new HashSet<>(now);
    }

    private ScriptStatus placeStep(List<Schematic.PlacedEntry> targets, boolean lastStep) {
        Map<BlockCoord, Schematic.PlacedEntry> byCell = new LinkedHashMap<>();
        for (Schematic.PlacedEntry entry : targets) {
            byCell.put(entry.pos(), entry);
        }
        List<BlockCoord> cells = List.copyOf(byCell.keySet());
        ReachPlanner.World world = placeWorld(byCell, lastStep);
        boolean mayPlaceHere = !lastStep || site.outsideFootprint(feet());
        List<Schematic.PlacedEntry> trapping = new ArrayList<>();
        boolean overlapped = false;
        if (mayPlaceHere) {
            for (BlockCoord cell : ReachPlanner.workableNow(SpiritReach.current(), feet(), cells, world)) {
                Schematic.PlacedEntry entry = byCell.get(cell);
                if (site.overlaps(entry, spirit.getBoundingBox())) {
                    overlapped = true;
                    continue;
                }
                if (!site.keepsRetreat(feet(), List.of(entry))) {
                    trapping.add(entry);
                    retreatRefusals++;
                    continue;
                }
                Optional<ScriptStatus> placedNow = placeOne(entry);
                if (placedNow.isPresent()) {
                    return placedNow.get();
                }
            }
        }
        if (trapping.isEmpty() && overlapped) {
            if (++centring <= MAX_CENTRING_TICKS) {
                BlockCoord here = feet();
                spirit.getNavigation().stop();
                spirit.getMoveControl().setWantedPosition(here.x() + 0.5, spirit.getY(), here.z() + 0.5, 1.0);
                return ScriptStatus.RUNNING;
            }
            centring = 0;
            excludedStands.add(feet());
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
        if (stand(trapped, safeWorld, new HashSet<>(site.schemeCells()), lastStep)
                .isEmpty()) {
            LOGGER.warn(
                    "Стройка '{}': блок {} запирает духа, а стоянки с путём наружу нет — ставлю с места",
                    buildKey,
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
                return world.standable(feet) && site.keepsRetreat(feet, trapping);
            }
        };
    }

    private Optional<ScriptStatus> placeOne(Schematic.PlacedEntry entry) {
        BlockCoord cell = entry.pos();
        reservations.reserve(cell, spirit.getUUID());
        BlockPlacer.Begin begin = placer.begin(SchematicSite.toPos(cell), site.wanted(entry), entry.block());
        reservations.release(cell, spirit.getUUID());
        switch (begin) {
            case BlockPlacer.Started started -> {
                if (!entry.text().isBlank()) {
                    SignTexts.write(level, started.pos(), entry.text());
                }
                placed++;
                centring = 0;
                tally.placed(entry.block());
                lastPlaceTick = ticks;
                refusals.remove(cell);
                excludedStands.clear();
                standsRetried = false;
                return Optional.of(ScriptStatus.RUNNING);
            }
            case BlockPlacer.Protected refused -> {
                blocked.put(cell, "запрещено: " + refused.reason());
                lastRefusal = "запрещено: " + refused.reason();
                return Optional.empty();
            }
            case BlockPlacer.Refused refused -> {
                lastRefusal = refused.reason() + ": " + refused.message();
                if (refused.reason() == BlockPlacer.RefuseReason.NO_MATERIAL) {
                    return Optional.of(noMaterial(entry.block()));
                }
                if (refused.reason() == BlockPlacer.RefuseReason.OCCUPIED) {
                    makeWay(cell);
                }
                int attempts = refusals.merge(cell, 1, Integer::sum);
                if (attempts >= BuildTuning.MAX_CONSECUTIVE_SKIPS) {
                    blocked.put(cell, lastRefusal);
                }
                return Optional.empty();
            }
        }
    }

    private void makeWay(BlockCoord cell) {
        AABB box = new AABB(SchematicSite.toPos(cell));
        for (SpiritEntity other : level.getEntitiesOfClass(SpiritEntity.class, box, e -> !e.equals(spirit))) {
            if (other.taskStatus().orElse(null) == RunStatus.RUNNING) {
                continue;
            }
            BlockPos feet = other.blockPosition();
            outsideStand(new BlockCoord(feet.getX(), feet.getY(), feet.getZ())).ifPresent(stand -> {
                LOGGER.info(
                        "Стройка {}: дух {} стоит в клетке {} — отходит к {}",
                        buildKey,
                        other.displayName(),
                        cell,
                        stand);
                other.startTask(new ScriptCall("goto", Map.of("x", stand.x(), "y", stand.y(), "z", stand.z())));
            });
        }
    }

    private Optional<BlockCoord> outsideStand(BlockCoord from) {
        for (int ring = 1; ring <= MAKE_WAY_RADIUS; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockCoord feet = new BlockCoord(from.x() + dx, from.y() + dy, from.z() + dz);
                        if (site.outsideFootprint(feet) && site.standable(feet)) {
                            return Optional.of(feet);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private ScriptStatus moveTowards(List<BlockCoord> cells, ReachPlanner.World base, boolean outsideOnly) {
        Set<BlockCoord> occupied = new HashSet<>(site.schemeCells());
        Optional<BlockCoord> stand = stand(cells, walkableOnly(base), occupied, outsideOnly);
        ReachPlanner.World world = base;
        if (stand.isEmpty()) {
            stand = stand(cells, world, occupied, outsideOnly);
        }
        if (stand.isEmpty()) {
            occupied = new HashSet<>(site.standBlockers());
            stand = stand(cells, world, occupied, outsideOnly);
        }
        if (stand.isEmpty() && !standsRetried && !excludedStands.isEmpty()) {
            standsRetried = true;
            excludedStands.clear();
            stand = stand(cells, world, occupied, outsideOnly);
        }
        if (stand.isEmpty()) {
            for (BlockCoord cell : cells) {
                blocked.putIfAbsent(
                        cell,
                        "ни с одной стоянки не дотянуться" + (outsideOnly ? " снаружи постройки" : "")
                                + (lastRefusal.isBlank() ? "" : "; " + lastRefusal));
            }
            return ScriptStatus.RUNNING;
        }
        return beginApproach(stand.get());
    }

    private Optional<BlockCoord> stand(
            List<BlockCoord> cells, ReachPlanner.World world, Set<BlockCoord> occupied, boolean outsideOnly) {
        Optional<BlockCoord> near = outsideOnly
                ? Optional.empty()
                : ReachPlanner.nextStand(SpiritReach.current(), feet(), cells, world, excludedStands, occupied);
        return near.isPresent()
                ? near
                : ReachPlanner.nextStandFromAfar(SpiritReach.current(), feet(), cells, world, excludedStands, occupied);
    }

    private ReachPlanner.World walkableOnly(ReachPlanner.World world) {
        Optional<Set<BlockCoord>> reachable = site.walkable(feet(), WALK_MARGIN);
        if (reachable.isEmpty()) {
            return world;
        }
        Set<BlockCoord> cells = reachable.get();
        return new ReachPlanner.World() {
            @Override
            public boolean ready(BlockCoord cell) {
                return world.ready(cell);
            }

            @Override
            public boolean standable(BlockCoord feet) {
                return cells.contains(feet) && world.standable(feet);
            }
        };
    }

    private ReachPlanner.World clearWorld() {
        return new ReachPlanner.World() {
            @Override
            public boolean ready(BlockCoord cell) {
                return true;
            }

            @Override
            public boolean standable(BlockCoord feet) {
                return site.standable(feet);
            }
        };
    }

    private ReachPlanner.World placeWorld(Map<BlockCoord, Schematic.PlacedEntry> byCell, boolean outsideOnly) {
        return new ReachPlanner.World() {
            @Override
            public boolean ready(BlockCoord cell) {
                Schematic.PlacedEntry entry = byCell.get(cell);
                return entry != null && site.freeForPlacing(entry) && site.supported(entry);
            }

            @Override
            public boolean standable(BlockCoord feet) {
                return site.standable(feet) && (!outsideOnly || site.outsideFootprint(feet));
            }
        };
    }

    private List<Schematic.PlacedEntry> ownFirst(List<Schematic.PlacedEntry> targets) {
        List<Schematic.PlacedEntry> own = new ArrayList<>();
        List<Schematic.PlacedEntry> others = new ArrayList<>();
        for (Schematic.PlacedEntry entry : targets) {
            (ownShare.contains(entry.pos()) ? own : others).add(entry);
        }
        own.addAll(others);
        return own;
    }

    private void refreshShare() {
        if (squad.slotOf(spirit.getUUID()) < 0) {
            squad.join(spirit.getUUID());
        }
        if (squad.version() == squadVersion) {
            return;
        }
        squadVersion = squad.version();
        Set<BlockCoord> share = new HashSet<>();
        for (Schematic.PlacedEntry entry :
                CellPartition.split(site.primaries(), squad.size()).get(squad.slotOf(spirit.getUUID()))) {
            share.add(entry.pos());
        }
        ownShare = Set.copyOf(share);
    }

    private ScriptStatus finish() {
        BuildStats stats = noteStats();
        reservations.releaseAll(spirit.getUUID());
        List<Schematic.PlacedEntry> missing = site.missing();
        List<BlockCoord> clear = site.toClear();
        LOGGER.info(
                "Стройка {}: подходов {} (план {}), стоянок выбрано {} (план {}), на крыше {}, отказов правила отхода {}, "
                        + "заходов {} (план {}), клеток плана на обычную стройку {}",
                buildKey,
                stats.approaches(),
                stats.planApproaches(),
                standsChosen,
                planStands,
                roofStands,
                retreatRefusals,
                trips,
                planTrips,
                planDeviations);
        if (missing.isEmpty() && clear.isEmpty()) {
            leaveSquad();
            if (level instanceof ServerLevel server) {
                PathNetworkService.endSite(server, buildKey);
            }
            tally.supplies(placed, 0);
            logRemovals();
            outcome = ScriptStatus.SUCCEEDED;
            return outcome;
        }
        for (int i = 0; i < blocked.size(); i++) {
            tally.skipped();
        }
        return fail("не совпало со схемой " + missing.size() + " из "
                + site.entries().size() + " клеток"
                + (clear.isEmpty() ? "" : ", не очищено " + clear.size()) + ": " + sample(missing, clear)
                + "; дух " + feet() + "; поставлено " + placed + ", очищено " + cleared + "; причины: "
                + reasons());
    }

    private String reasons() {
        List<Schematic.PlacedEntry> missing = site.missing();
        if (!missing.isEmpty()) {
            Schematic.PlacedEntry first = missing.get(0);
            String why = blocked.get(first.pos());
            String state = "в мире "
                    + level.getBlockState(SchematicSite.toPos(first.pos()))
                            .getBlock()
                            .getDescriptionId()
                    + ", свободна " + site.freeForPlacing(first) + ", опора " + site.supported(first) + ", материал "
                    + BlockItems.countIn(spirit.getInventory(), first.block()) + ", основная часть "
                    + site.primaries().contains(first) + ", занята другим "
                    + reservations.foreign(first.pos(), spirit.getUUID());
            if (why != null || blocked.isEmpty()) {
                return first.pos() + " — " + (why == null ? "не отложена" : why) + " (" + state + ")"
                        + (lastRefusal.isBlank() ? "" : "; последний отказ: " + lastRefusal);
            }
        }
        if (blocked.isEmpty()) {
            return lastRefusal.isBlank() ? "нет" : lastRefusal;
        }
        Map.Entry<BlockCoord, String> first = blocked.entrySet().iterator().next();
        return first.getKey() + " — " + first.getValue() + (blocked.size() > 1 ? " и ещё " + (blocked.size() - 1) : "");
    }

    private static String sample(List<Schematic.PlacedEntry> missing, List<BlockCoord> clear) {
        List<String> shown = new ArrayList<>();
        for (Schematic.PlacedEntry entry : missing) {
            if (shown.size() == 6) {
                shown.add("…");
                break;
            }
            shown.add(entry.block() + "@" + entry.pos());
        }
        for (BlockCoord cell : clear) {
            if (shown.size() >= 8) {
                break;
            }
            shown.add("воздух@" + cell);
        }
        return String.join(", ", shown);
    }

    private ScriptStatus noMaterial(String blockId) {
        if (trips < BuildSupplyTrip.MAX_TRIPS) {
            int band = phaseBand;
            List<ScriptCall> steps = BuildSupplyTrip.plan(
                    spirit,
                    site,
                    entry -> band < 0
                            || (BuildPhase.rough(entry.block())
                                    && BuildPhase.band(entry.pos().y() - bottomY) >= band));
            if (!steps.isEmpty()) {
                trips++;
                LOGGER.info(
                        "Стройка {}: заход {} за материалами ({} шагов withdraw), поставлено {} из {}, не хватило '{}'",
                        buildKey,
                        trips,
                        steps.size(),
                        placed,
                        site.primaries().size(),
                        blockId);
                return startSupply(steps);
            }
        }
        int needed = countMissing(blockId);
        if (crafts < MAX_OWN_CRAFTS && "minecraft:torch".equals(blockId)) {
            int held = BlockItems.countIn(spirit.getInventory(), blockId);
            int count = Math.min(CatalogLimits.COUNT_MAX, held + Math.max(needed - held, 1));
            int coal = spirit.getInventory().countItem(Items.COAL)
                    + spirit.getInventory().countItem(Items.CHARCOAL);
            if (coal * TORCHES_PER_COAL >= count - held) {
                crafts++;
                LOGGER.info("Стройка {}: крафт своих факелов ×{} из угля духа", buildKey, count - held);
                return startSupply(ownCraft(blockId, count));
            }
        }
        if (crafts < MAX_OWN_CRAFTS && !OwnWood.species(blockId).isEmpty()) {
            int held = BlockItems.countIn(spirit.getInventory(), blockId);
            int count = Math.min(CatalogLimits.COUNT_MAX, held + Math.max(needed - held, 1));
            if (OwnWood.planksHeld(spirit.getInventory(), OwnWood.species(blockId))
                    >= OwnWood.plankCost(blockId, count - held)) {
                crafts++;
                LOGGER.info("Стройка {}: крафт своего {} ×{} из брёвен духа", buildKey, blockId, count - held);
                return startSupply(ownCraft(blockId, count));
            }
        }
        failed++;
        tally.failed();
        int have = BlockItems.countIn(spirit.getInventory(), blockId);
        tally.supplies(placed + have, Math.max(needed - have, 0));
        return fail("не хватило материала '" + blockId + "': поставлено " + placed + " из "
                + site.primaries().size() + "; нужно " + needed + ", есть " + have);
    }

    private List<ScriptCall> ownCraft(String blockId, int count) {
        List<ScriptCall> steps = new ArrayList<>();
        if (spirit.level() instanceof ServerLevel server) {
            SettlementPlotStore.get(server).site().ifPresent(site -> {
                BlockCoord yard = site.workYard();
                steps.add(new ScriptCall("goto", Map.of("x", yard.x(), "y", yard.y(), "z", yard.z())));
            });
        }
        steps.add(new ScriptCall("acquire", Map.of("item", blockId, "count", count)));
        return steps;
    }

    private ScriptStatus startSupply(List<ScriptCall> steps) {
        approach.cancel();
        approachStand = null;
        supplyRunner = TaskRunner.start(
                new SequenceNode(new ArrayList<TaskNode>(steps)),
                SpiritScripts.registryFor(spirit),
                ConditionRegistry.of(Map.of()));
        return ScriptStatus.RUNNING;
    }

    private void startPlan() {
        planChecked = true;
        if (squad.size() > 1) {
            return;
        }
        planBegan = System.nanoTime();
        planStartTick = ticks;
        BuildPlanStart.Prepared prepared = BuildPlanStart.of(spirit, site);
        Map<BlockCoord, Schematic.PlacedEntry> byCell = new HashMap<>();
        site.primaries().forEach(entry -> byCell.put(entry.pos(), entry));
        primaryAt = Map.copyOf(byCell);
        planItems = prepared.items();
        planSupply = prepared.supply();
        Schematic schematic = site.schematic();
        BuildRotation rotation = site.rotation();
        SpiritReach reach = SpiritReach.current();
        planPending = CompletableFuture.supplyAsync(
                () -> BuildPlanner.plan(
                        schematic, rotation, prepared.supply(), reach, BuildPlanner.Portion.FILL, prepared.start()),
                Util.backgroundExecutor());
    }

    private void acceptPlan(CompletableFuture<BuildPlanner.Plan> pending) {
        planPending = null;
        BuildPlanner.Plan built;
        try {
            built = pending.join();
        } catch (CompletionException | CancellationException e) {
            LOGGER.warn("Стройка {}: план не построен ({}) — обычная стройка", buildKey, e.getMessage());
            return;
        }
        planStands = built.stands();
        planTrips = built.trips();
        plan = new BuildPlanCursor(built, site.origin());
        LOGGER.info(
                "Стройка {}: план — стоянок {}, заходов {}, не поставлено {}, сундук у {}, расчёт {} мс",
                buildKey,
                planStands,
                planTrips,
                built.stuck(),
                planSupply,
                (System.nanoTime() - planBegan) / 1_000_000);
    }

    private boolean pendingCell(BlockCoord cell) {
        Schematic.PlacedEntry entry = primaryAt.get(cell);
        return entry != null && !blocked.containsKey(cell) && !(site.matches(entry) && site.secondPartMatches(entry));
    }

    private Optional<ScriptStatus> planStep(BuildPlanCursor cursor) {
        BuildPlanCursor.Fetch again = retryFetch;
        if (again != null) {
            retryFetch = null;
            return Optional.of(planFetch(again));
        }
        BuildPlanCursor.Next next = cursor.next(this::pendingCell);
        switch (next) {
            case BuildPlanCursor.Done ignored -> {
                LOGGER.info(
                        "Стройка {}: план исполнен — стоянок {} (план {}), заходов {} (план {}), клеток на обычную "
                                + "стройку {}",
                        buildKey,
                        standsChosen,
                        planStands,
                        trips,
                        planTrips,
                        planDeviations);
                leavePlan();
                return Optional.empty();
            }
            case BuildPlanCursor.Fetch fetch -> {
                nextPlanStep(cursor);
                planFetchRetries = 0;
                return Optional.of(planFetch(fetch));
            }
            case BuildPlanCursor.StandAt at -> {
                if (at.cells().stream()
                                .allMatch(cell -> SchematicBlocks.isEntranceBlock(
                                        primaryAt.get(cell).block()))
                        && innerLeft()) {
                    LOGGER.info(
                            "Стройка {}: до дверей плана остались клетки внутри — дальше обычная стройка", buildKey);
                    leavePlan();
                    return Optional.empty();
                }
                return Optional.of(standStep(cursor, at));
            }
        }
    }

    private void leavePlan() {
        plan = null;
        planDeviations += planCarried.size();
        planCarried.clear();
        blocked.clear();
        refusals.clear();
        standsRetried = false;
    }

    private boolean innerLeft() {
        for (Schematic.PlacedEntry entry : site.primaries()) {
            if (!SchematicBlocks.isEntranceBlock(entry.block())
                    && !(site.matches(entry) && site.secondPartMatches(entry))) {
                return true;
            }
        }
        return false;
    }

    private ScriptStatus planFetch(BuildPlanCursor.Fetch fetch) {
        planFetch = fetch;
        Map<Item, Integer> wanted = new LinkedHashMap<>();
        for (BuildPlanner.Taken taken : fetch.taken()) {
            Item item = planItems.get(taken.item());
            if (item != null) {
                wanted.merge(item, taken.count(), Integer::sum);
            }
        }
        List<ScriptCall> steps = BuildSupplyTrip.exact(spirit, site, wanted);
        if (steps.isEmpty()) {
            planFetch = null;
            return ScriptStatus.RUNNING;
        }
        trips++;
        LOGGER.info(
                "Стройка {}: заход {} по плану (заход плана {}, {} шагов withdraw), поставлено {} из {}",
                buildKey,
                trips,
                fetch.number(),
                steps.size(),
                placed,
                site.primaries().size());
        BlockCoord origin = site.origin();
        BlockCoord supply =
                new BlockCoord(origin.x() + planSupply.x(), origin.y() + planSupply.y(), origin.z() + planSupply.z());
        if (feet().equals(supply)) {
            return startSupply(steps);
        }
        supplyWalk = steps;
        approach.begin(new Vec3(supply.x() + 0.5, supply.y(), supply.z() + 0.5), BuildTuning.STAND_ARRIVAL);
        return ScriptStatus.RUNNING;
    }

    private void nextPlanStep(BuildPlanCursor cursor) {
        cursor.advance();
        planStandVisited = false;
        centring = 0;
    }

    private ScriptStatus standStep(BuildPlanCursor cursor, BuildPlanCursor.StandAt at) {
        if (!planStandVisited) {
            planStandVisited = true;
            planPlacedMark = placed;
            if (!feet().equals(at.stand())) {
                return beginApproach(at.stand());
            }
        }
        if (placed != planPlacedMark) {
            planPlacedMark = placed;
        }
        planCarried.removeIf(cell -> !pendingCell(cell));
        List<BlockCoord> cells = new ArrayList<>(at.cells());
        for (BlockCoord carried : planCarried) {
            if (!cells.contains(carried) && pendingCell(carried)) {
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
        if (placing.overlapped() && ++centring <= MAX_CENTRING_TICKS) {
            BlockCoord here = feet();
            spirit.getNavigation().stop();
            spirit.getMoveControl().setWantedPosition(here.x() + 0.5, spirit.getY(), here.z() + 0.5, 1.0);
            return ScriptStatus.RUNNING;
        }
        centring = 0;
        List<Schematic.PlacedEntry> supplied = new ArrayList<>();
        Map<Item, Integer> lacking = new LinkedHashMap<>();
        for (Schematic.PlacedEntry entry : targets) {
            if (BlockItems.countIn(spirit.getInventory(), entry.block()) > 0) {
                supplied.add(entry);
            } else {
                lacking.merge(BlockItems.itemFor(entry.block()), 1, Integer::sum);
            }
        }
        if (supplied.isEmpty() && !lacking.isEmpty() && planTopUps < MAX_PLAN_TOP_UPS) {
            for (Schematic.PlacedEntry entry : site.primaries()) {
                Item item = BlockItems.itemFor(entry.block());
                if (lacking.containsKey(item) && !site.matches(entry)) {
                    lacking.merge(item, 1, Integer::sum);
                }
            }
            List<ScriptCall> steps = BuildSupplyTrip.exact(spirit, site, lacking);
            if (!steps.isEmpty()) {
                planTopUps++;
                trips++;
                LOGGER.info(
                        "Стройка {}: дозаход {} — шагу плана {} не хватило {}",
                        buildKey,
                        trips,
                        cursor.index(),
                        lacking);
                return startSupply(steps);
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
            Schematic.PlacedEntry entry = primaryAt.get(cell);
            if (entry == null) {
                continue;
            }
            if (site.rightBlockWrongState(entry)) {
                site.repairState(entry);
                return true;
            }
            byCell.put(cell, entry);
        }
        return false;
    }

    private void carryRest(BuildPlanCursor cursor, List<Schematic.PlacedEntry> supplied) {
        for (Schematic.PlacedEntry entry : supplied) {
            if (!site.matches(entry)) {
                planCarried.add(entry.pos());
            }
        }
        nextPlanStep(cursor);
    }

    private ScriptStatus beginApproach(BlockCoord stand) {
        approachStand = stand;
        approachPathMark = spirit.pathBuilds();
        standsChosen++;
        if (site.onRoof(stand)) {
            roofStands++;
        }
        approach.beginWithRoutes(new Vec3(stand.x() + 0.5, stand.y(), stand.z() + 0.5), BuildTuning.STAND_ARRIVAL);
        return ScriptStatus.RUNNING;
    }

    private record PlanPlacing(Optional<ScriptStatus> status, boolean overlapped) {}

    private PlanPlacing placeFromPlanStand(List<BlockCoord> cells, Map<BlockCoord, Schematic.PlacedEntry> byCell) {
        boolean overlapped = false;
        ReachPlanner.World world = placeWorld(byCell, false);
        for (BlockCoord cell : ReachPlanner.workableNow(SpiritReach.current(), feet(), cells, world)) {
            Schematic.PlacedEntry entry = byCell.get(cell);
            if (entry == null || (SchematicBlocks.isEntranceBlock(entry.block()) && !site.outsideFootprint(feet()))) {
                continue;
            }
            if (site.overlaps(entry, spirit.getBoundingBox())) {
                overlapped = true;
                continue;
            }
            if (!site.keepsRetreat(feet(), List.of(entry))) {
                retreatRefusals++;
                continue;
            }
            if (BlockItems.countIn(spirit.getInventory(), entry.block()) == 0) {
                continue;
            }
            Optional<ScriptStatus> placedNow = placeOne(entry);
            if (placedNow.isPresent()) {
                return new PlanPlacing(placedNow, overlapped);
            }
        }
        return new PlanPlacing(Optional.empty(), overlapped);
    }

    private void deferPlanStep(
            BuildPlanCursor cursor, BuildPlanCursor.StandAt at, List<Schematic.PlacedEntry> targets) {
        planDeviations += at.cells().size();
        Schematic.PlacedEntry first = targets.isEmpty() ? null : targets.get(0);
        LOGGER.info(
                "Стройка {}: шаг плана {} — со стоянки {} не поставлено {} (например {}: свободна {}, "
                        + "опора {}, материал {}; последний отказ: {}), дух {}; клетки — обычной стройке",
                buildKey,
                cursor.index(),
                at.stand(),
                at.cells().size(),
                first == null ? "?" : first.block() + "@" + first.pos(),
                first != null && site.freeForPlacing(first),
                first != null && site.supported(first),
                first == null ? 0 : BlockItems.countIn(spirit.getInventory(), first.block()),
                lastRefusal.isBlank() ? "нет" : lastRefusal,
                feet());
        for (BlockCoord cell : at.cells()) {
            blocked.putIfAbsent(cell, "план: со стоянки " + at.stand() + " не поставить");
        }
        nextPlanStep(cursor);
    }

    private int countMissing(String blockId) {
        int total = 0;
        for (Schematic.PlacedEntry entry : site.primaries()) {
            if (entry.block().equals(blockId) && !site.matches(entry)) {
                total++;
            }
        }
        return Math.max(total, 1);
    }

    private BuildStats noteStats() {
        BuildStats stats = new BuildStats(planStands, planTrips, standsChosen, trips, planDeviations);
        spirit.noteBuildStats(stats);
        return stats;
    }

    private void logRemovals() {
        if (!removals.isEmpty()) {
            LOGGER.info("[clear] стройка {} духом {} сняла: {}", buildKey, spirit.displayName(), removals.summary());
        }
    }

    private ScriptStatus fail(String reason) {
        logRemovals();
        noteStats();
        failureReason = reason;
        outcome = ScriptStatus.FAILED;
        cancel();
        return outcome;
    }

    private void stopSupply() {
        if (supplyRunner != null) {
            supplyRunner.cancel();
            supplyRunner = null;
        }
    }

    private void leaveSquad() {
        BuildSquadRegistry.leave(level, buildKey, spirit.getUUID());
    }

    @Override
    public Optional<Progress> progress() {
        int total = Math.max(site.entries().size(), 1);
        return Optional.of(new Progress(site.entries().size() - site.missing().size(), total));
    }

    @Override
    public Optional<ScriptResult> result() {
        return outcome == ScriptStatus.SUCCEEDED
                ? Optional.of(new ScriptResult.Data(tally.toData()))
                : Optional.empty();
    }

    @Override
    public void cancel() {
        supplyWalk = null;
        if (planPending != null) {
            planPending.cancel(false);
            planPending = null;
        }
        stopSupply();
        approach.cancel();
        breaker.cancel();
        breaking = null;
        reservations.releaseAll(spirit.getUUID());
        leaveSquad();
    }

    @Override
    public void suspend() {
        stopSupply();
        approach.suspend();
        breaker.cancel();
        breaking = null;
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public Map<String, Object> saveState() {
        return Map.copyOf(new BuildState(placed, 0, blocked.size(), failed, outcome == ScriptStatus.RUNNING).toMap());
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        BuildState snapshot = BuildState.fromMap(state);
        placed = Math.max(placed, snapshot.placed());
        failed = Math.max(failed, snapshot.failed());
        String kind = site.entries().isEmpty() ? "" : site.entries().get(0).block();
        for (int i = 0; i < snapshot.placed() && !kind.isEmpty(); i++) {
            tally.placed(kind);
        }
        for (int i = 0; i < snapshot.failed(); i++) {
            tally.failed();
        }
    }

    private BlockCoord feet() {
        BlockPos pos = spirit.blockPosition();
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
