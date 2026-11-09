package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildKey;
import com.theminespirit.core.building.BuildPlanCursor;
import com.theminespirit.core.building.BuildPlanner;
import com.theminespirit.core.building.BuildReservations;
import com.theminespirit.core.building.BuildResult;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.BuildSquad;
import com.theminespirit.core.building.ReachPlanner;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicWood;
import com.theminespirit.core.building.SchemeFilter;
import com.theminespirit.core.engine.ConditionRegistry;
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
import org.jspecify.annotations.Nullable;

final class BuildSchematicScript implements ScriptInstance {
    static final int MAX_OWN_CRAFTS = 8;
    static final int TORCHES_PER_COAL = 4;
    static final int MAX_FINISH_WAITS = 200;
    static final int MAX_CENTRING_TICKS = 20;
    static final int WALK_MARGIN = 6;
    static final int MAX_PLAN_TOP_UPS = 8;
    static final int MAX_PLAN_TRIP_RETRIES = 2;
    private static final int SITE_NETWORK_RING = 6;

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
    final BlockPlacer placer;
    Set<BlockCoord> ownShare = Set.of();
    long squadVersion = -1;
    BlockCoord approachStand;
    long lastPlaceTick = Long.MIN_VALUE / 2;
    final SchematicPlanPhase planPhase;
    final SchematicResult report;
    final SchematicPlacement placement;
    private final SchematicWorkPhase workPhase;

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
        this.placement = new SchematicPlacement(this);
        this.workPhase = new SchematicWorkPhase(this);
        if (level instanceof ServerLevel server) {
            PathNetworkService.beginSite(server, buildKey, site.networkBox(SITE_NETWORK_RING), site.doorCells());
        }
    }

    @Override
    public ScriptStatus tick() {
        return workPhase.tick();
    }

    Optional<ScriptStatus> placeOne(Schematic.PlacedEntry entry) {
        return placement.placeOne(entry);
    }

    ReachPlanner.World placeWorld(Map<BlockCoord, Schematic.PlacedEntry> byCell, boolean outsideOnly) {
        return placement.placeWorld(byCell, outsideOnly);
    }

    ScriptStatus beginApproach(BlockCoord stand) {
        return placement.beginApproach(stand);
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
