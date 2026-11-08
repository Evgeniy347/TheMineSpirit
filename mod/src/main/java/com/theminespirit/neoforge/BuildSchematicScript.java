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
import com.theminespirit.core.building.BuildTuning;
import com.theminespirit.core.building.CellPartition;
import com.theminespirit.core.building.ReachPlanner;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicActions;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.building.SchematicWood;
import com.theminespirit.core.building.SchemeFilter;
import com.theminespirit.core.building.SpiritReach;
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
import com.theminespirit.core.town.PlotProtection;
import com.theminespirit.neoforge.movement.PathNetworkService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class BuildSchematicScript implements ScriptInstance {
    static final int MAX_OWN_CRAFTS = 8;
    static final int TORCHES_PER_COAL = 4;
    static final int MAX_FINISH_WAITS = 200;
    static final int MAX_CENTRING_TICKS = 20;
    static final int WALK_MARGIN = 6;
    static final int MAX_PLAN_TOP_UPS = 8;
    private static final int SITE_NETWORK_RING = 6;
    private static final int MAX_PLAN_TRIP_RETRIES = 2;
    private static final int HARD_APPROACH_PATH_BUILDS = 4;

    private static final Logger LOGGER = LogUtils.getLogger();
    final SpiritEntity spirit;
    final Level level;
    final SchematicSite site;
    final BlockBreaker breaker;
    final Approach approach;
    final BuildPitWatch pit;
    final BuildReservations reservations;
    final BuildKey buildKey;
    final BuildSquad squad;
    final BuildResult tally = new BuildResult();
    final Set<BlockCoord> excludedStands = new HashSet<>();
    final Map<BlockCoord, String> blocked = new LinkedHashMap<>();
    final Map<BlockCoord, Integer> refusals = new HashMap<>();
    final BuildToolRestock toolRestock = new BuildToolRestock();
    BlockCoord breaking;
    String breakingId = "-";
    final RemovalLog removals = new RemovalLog();
    long ticks;
    int placed;
    int cleared;
    int failed;
    int finishWaits;
    boolean standsRetried;
    int bottomY = Integer.MAX_VALUE;
    ScriptStatus outcome = ScriptStatus.RUNNING;
    String failureReason;
    String lastRefusal = "";
    TaskRunner supplyRunner;
    int crafts;
    int trips;
    int standsChosen;
    int roofStands;
    int retreatRefusals;
    int centring;
    int phaseBand = -1;
    int highestBand = -1;

    @Nullable
    BuildPlanCursor plan;

    boolean planChecked;

    @Nullable
    CompletableFuture<BuildPlanner.Plan> planPending;

    long planStartTick;
    long planBegan;
    BlockCoord planSupply = new BlockCoord(0, 0, 0);
    Map<String, Item> planItems = Map.of();
    Map<BlockCoord, Schematic.PlacedEntry> primaryAt = Map.of();
    boolean planStandVisited;
    BuildPlanCursor.@Nullable Fetch planFetch;
    BuildPlanCursor.@Nullable Fetch retryFetch;
    int planFetchRetries;
    int planTopUps;

    @Nullable
    List<ScriptCall> supplyWalk;

    int approachPathMark;
    final Set<BlockCoord> planCarried = new HashSet<>();
    long planStands;
    long planTrips;
    int planDeviations;
    private final BlockPlacer placer;
    private Set<BlockCoord> ownShare = Set.of();
    private long squadVersion = -1;
    private BlockCoord approachStand;
    private long lastPlaceTick = Long.MIN_VALUE / 2;
    private final SchematicPlanPhase planPhase;
    private final SchematicResult report;

    BuildSchematicScript(SpiritEntity spirit, BuildSchematicParams params) {
        this(
                spirit,
                SchemeFilter.only(
                        params.wood() == null
                                ? SchematicResources.load(params.file())
                                : SchematicWood.swap(SchematicResources.load(params.file()), params.wood()),
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
        this.placer = new BlockPlacer(spirit, PlotProtection.Ground.PLAN_BUILD);
        this.breaker = new BlockBreaker(spirit);
        this.approach = new Approach(spirit);
        this.reservations = BuildReservationHub.forLevel(level);
        this.buildKey = new BuildKey(file, origin, rotation);
        this.pit = new BuildPitWatch(spirit, level, site, buildKey);
        this.squad = BuildSquadRegistry.join(level, buildKey, spirit.getUUID());
        this.planPhase = new SchematicPlanPhase(this);
        this.report = new SchematicResult(this);
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
            return report.fail("дух исчез во время стройки по схеме");
        }
        ticks++;
        if (pit.stalled(ticks, placed, cleared, trips)) {
            pit.stalledReport(feet(), placed, cleared, trips, blocked);
        }
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
                    pit.hardApproach(
                            approachStand,
                            approach.refusal(),
                            approach.networkNote(),
                            spirit.pathBuilds() - approachPathMark,
                            "дошёл");
                }
            }
            case STUCK, TIMEOUT -> {
                excludedStands.add(approachStand);
                lastRefusal = "стоянка " + approachStand + " недостижима: "
                        + (approach.refusal() == null ? "путь не найден" : approach.refusal());
                pit.hardApproach(
                        approachStand,
                        approach.refusal(),
                        approach.networkNote(),
                        spirit.pathBuilds() - approachPathMark,
                        "срыв");
            }
        }
        approach.cancel();
        approachStand = null;
        return planTick();
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
            planPhase.startPlan();
        }
        CompletableFuture<BuildPlanner.Plan> pending = planPending;
        if (pending != null) {
            if (ticks == planStartTick) {
                return ScriptStatus.RUNNING;
            }
            planPhase.acceptPlan(pending);
        }
        Optional<ScriptStatus> planned = planPhase.step();
        if (planned.isPresent()) {
            return planned.get();
        }
        List<Schematic.PlacedEntry> missing = new ArrayList<>();
        List<Schematic.PlacedEntry> unsupplied = new ArrayList<>();
        boolean waitingForeign = false;
        for (Schematic.PlacedEntry entry : site.primaries()) {
            if (SchematicActions.isDecorFlower(entry.block())) {
                continue;
            }
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
                    && !SchematicActions.isActionPlaced(entry.block())
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
            return report.noMaterial(unsupplied.get(0).block(), null);
        }
        if (missing.isEmpty()) {
            return waitingForeign && ++finishWaits <= MAX_FINISH_WAITS ? ScriptStatus.RUNNING : report.finish();
        }
        List<Schematic.PlacedEntry> main = new ArrayList<>();
        List<Schematic.PlacedEntry> entrances = new ArrayList<>();
        for (Schematic.PlacedEntry entry : missing) {
            (SchematicBlocks.isEntranceBlock(entry.block()) ? entrances : main).add(entry);
        }
        boolean lastStep = main.isEmpty();
        if (lastStep && unsupplied.stream().anyMatch(entry -> !SchematicBlocks.isEntranceBlock(entry.block()))) {
            return report.noMaterial(unsupplied.get(0).block(), null);
        }
        List<Schematic.PlacedEntry> targets = ownFirst(lastStep ? entrances : main);
        return placeStep(targets, lastStep);
    }

    private ScriptStatus clearStep(List<BlockCoord> clear) {
        SpiritReach reach = SpiritReach.current();
        ReachPlanner.World world = clearWorld();
        for (BlockCoord cell : ReachPlanner.workableNow(reach, feet(), clear, world)) {
            BlockBreaker.Begin begin = breaker.beginRepair(SchematicSite.toPos(cell));
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
        if (BuildStandPath.choose(
                        trapped, safeWorld, excludedStands, new HashSet<>(site.schemeCells()), lastStep, feet())
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

    Optional<ScriptStatus> placeOne(Schematic.PlacedEntry entry) {
        if (SchematicActions.isFluidPlaced(entry.block())) return waterOne(entry);
        BlockCoord cell = entry.pos();
        BlockCoord weed = SchematicWild.entryWeed(level, breaker, entry);
        if (weed != null) {
            breaking = weed;
            breakingId = BlockStates.idOf(level.getBlockState(SchematicSite.toPos(weed)));
            return Optional.of(ScriptStatus.RUNNING);
        }
        if (BuildMakeWay.selfIn(spirit, cell)) {
            lastRefusal = "строитель стоит в клетке " + cell + " — сначала отойти";
            return BuildMakeWay.outsideStand(site, feet()).map(this::beginApproach);
        }
        if (!BuildMakeWay.clear(spirit, level, site, buildKey, cell)) {
            lastRefusal = "клетку " + cell + " занимает живое существо: сначала отойти";
            return Optional.empty();
        }
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
                    return Optional.of(report.noMaterial(entry.block(), refused.message()));
                }
                int attempts = refusals.merge(cell, 1, Integer::sum);
                if (attempts >= BuildTuning.MAX_CONSECUTIVE_SKIPS) {
                    blocked.put(cell, lastRefusal);
                }
                return Optional.empty();
            }
        }
    }

    private Optional<ScriptStatus> waterOne(Schematic.PlacedEntry entry) {
        BuildWater.Plan water = BuildWater.plan(spirit, site, entry, buildKey);
        if (water.task().isEmpty()) {
            blocked.put(entry.pos(), water.reason());
            lastRefusal = water.reason();
            return Optional.empty();
        }
        approach.cancel();
        approachStand = null;
        supplyRunner = TaskRunner.start(
                water.task().orElseThrow(), SpiritScripts.registryFor(spirit), ConditionRegistry.of(Map.of()));
        return Optional.of(ScriptStatus.RUNNING);
    }

    private ScriptStatus moveTowards(List<BlockCoord> cells, ReachPlanner.World base, boolean outsideOnly) {
        Set<BlockCoord> occupied = SchematicWalkways.free(site.primaries(), site.schemeCells());
        Optional<Set<BlockCoord>> reachable = site.walkable(feet(), WALK_MARGIN);
        boolean walkKnown = site.networkBox(WALK_MARGIN).contains(feet()) && reachable.isPresent();
        ReachPlanner.World world = walkKnown ? BuildStandPath.walkableOnly(base, reachable.orElseThrow()) : base;
        Optional<BlockCoord> stand = BuildStandPath.choose(cells, world, excludedStands, occupied, outsideOnly, feet());
        if (stand.isEmpty()) {
            Set<BlockCoord> blockers = SchematicWalkways.free(site.primaries(), site.standBlockers());
            stand = BuildStandPath.choose(cells, world, excludedStands, blockers, outsideOnly, feet());
        }
        if (stand.isEmpty() && !standsRetried && !excludedStands.isEmpty()) {
            standsRetried = true;
            excludedStands.clear();
            stand = BuildStandPath.choose(cells, world, excludedStands, occupied, outsideOnly, feet());
        }
        if (stand.isEmpty()) {
            for (BlockCoord cell : cells) {
                blocked.putIfAbsent(
                        cell,
                        "ни с одной стоянки не дотянуться" + (outsideOnly ? " снаружи постройки" : "")
                                + (walkKnown ? " пешком от " + feet() : "")
                                + (lastRefusal.isBlank() ? "" : "; " + lastRefusal));
            }
            return ScriptStatus.RUNNING;
        }
        return beginApproach(stand.get());
    }

    ScriptStatus beginApproach(BlockCoord stand) {
        approachStand = stand;
        pit.descent(feet(), stand);
        approachPathMark = spirit.pathBuilds();
        standsChosen++;
        if (site.onRoof(stand)) {
            roofStands++;
        }
        approach.beginWithRoutes(new Vec3(stand.x() + 0.5, stand.y(), stand.z() + 0.5), BuildTuning.STAND_ARRIVAL);
        return ScriptStatus.RUNNING;
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

    ReachPlanner.World placeWorld(Map<BlockCoord, Schematic.PlacedEntry> byCell, boolean outsideOnly) {
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

    ScriptStatus startSupply(List<ScriptCall> steps) {
        return startSupply(new SequenceNode(new ArrayList<TaskNode>(steps)));
    }

    ScriptStatus startSupply(TaskNode plan) {
        approach.cancel();
        approachStand = null;
        supplyRunner = TaskRunner.start(plan, SpiritScripts.registryFor(spirit), ConditionRegistry.of(Map.of()));
        return ScriptStatus.RUNNING;
    }

    @Override
    public Optional<Progress> progress() {
        return report.progress();
    }

    @Override
    public Optional<ScriptResult> result() {
        return report.result();
    }

    @Override
    public void cancel() {
        report.cancel();
    }

    @Override
    public void suspend() {
        report.suspend();
    }

    @Override
    public String failureReason() {
        return report.failureReason();
    }

    @Override
    public Map<String, Object> saveState() {
        return report.saveState();
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        report.restoreState(state);
    }

    BlockCoord feet() {
        BlockPos pos = spirit.blockPosition();
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
