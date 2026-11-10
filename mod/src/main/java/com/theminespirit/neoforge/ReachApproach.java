package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.ReachPlanner;
import com.theminespirit.core.building.ReachStands;
import com.theminespirit.core.building.RetreatPath;
import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.movement.NetworkLevelView;
import com.theminespirit.neoforge.movement.PathNetworkService;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class ReachApproach {
    private static final int MAX_STANDS = 8;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double STAND_ARRIVAL = 0.8;

    private enum Probe {
        ACCEPTED,
        SKIPPED,
        EXHAUSTED
    }

    private final SpiritEntity spirit;
    private final Approach approach;
    private final LevelSafetyView safety;
    private final ReachPlanner.World world;
    private final Set<BlockCoord> tried = new HashSet<>();
    private ReachStands.@Nullable Search search;
    private BlockCoord target;
    private BlockCoord stand;
    private boolean active;
    private String refusal;
    private boolean routes;
    private int rejected;
    private int probes;
    private String rejection;

    public ReachApproach(SpiritEntity spirit) {
        this.spirit = spirit;
        this.approach = new Approach(spirit);
        this.safety = new LevelSafetyView(spirit.level());
        this.world = new ReachPlanner.World() {
            @Override
            public boolean ready(BlockCoord cell) {
                return true;
            }

            @Override
            public boolean standable(BlockCoord feet) {
                return MiningSafety.canStand(feet, safety);
            }
        };
    }

    public static ReachApproach withRoutes(SpiritEntity spirit) {
        ReachApproach approach = new ReachApproach(spirit);
        approach.routes = true;
        return approach;
    }

    public void begin(BlockPos block) {
        approach.cancel();
        target = new BlockCoord(block.getX(), block.getY(), block.getZ());
        tried.clear();
        search = null;
        stand = null;
        refusal = null;
        rejected = 0;
        rejection = null;
        active = true;
    }

    public Approach.State tick() {
        if (!active) {
            throw new IllegalStateException("Подход к блоку не начат: сначала begin(...)");
        }
        if (inReach()) {
            finish();
            return Approach.State.ARRIVED;
        }
        if (!approach.isActive()) {
            if (stand != null) {
                tried.add(stand);
                stand = null;
                search = null;
            }
            if (tried.size() >= MAX_STANDS) {
                return refuse();
            }
            Probe probe = probeStand();
            if (probe == Probe.SKIPPED) {
                return Approach.State.MOVING;
            }
            if (probe == Probe.EXHAUSTED) {
                return refuse();
            }
        }
        Approach.State state = approach.tick();
        if (state == Approach.State.MOVING) {
            return state;
        }
        if (approach.refusal() != null) {
            refusal = approach.refusal();
        }
        approach.cancel();
        if (inReach()) {
            finish();
            return Approach.State.ARRIVED;
        }
        return Approach.State.MOVING;
    }

    public boolean inReach() {
        return target != null && SpiritReach.reachable(feet(), target);
    }

    public boolean isActive() {
        return active;
    }

    public String refusal() {
        return refusal;
    }

    public int probes() {
        return probes;
    }

    public void cancel() {
        approach.cancel();
        active = false;
    }

    public void suspend() {
        approach.suspend();
    }

    private Probe probeStand() {
        if (search == null) {
            search = ReachStands.search(SpiritReach.current(), feet(), List.of(target), world, tried);
        }
        Optional<BlockCoord> next = search.nextCandidate();
        if (next.isEmpty()) {
            return Probe.EXHAUSTED;
        }
        BlockCoord candidate = next.get();
        if (!standReachable(candidate)) {
            search.skip(candidate);
            return Probe.SKIPPED;
        }
        stand = candidate;
        search = null;
        Vec3 point = new Vec3(stand.x() + 0.5, stand.y(), stand.z() + 0.5);
        if (routes && !DigOutScript.open(spirit.level(), spirit.blockPosition())) {
            approach.beginWithRoutes(point, STAND_ARRIVAL);
        } else {
            approach.begin(point, STAND_ARRIVAL);
        }
        return Probe.ACCEPTED;
    }

    private Approach.State refuse() {
        refusal = "ни одной достижимой стоянки у " + target + " ("
                + SpiritReach.current().describe() + "), проверены стоянки " + tried
                + (refusal == null ? "" : "; " + refusal);
        LOGGER.info(
                "[move] {}: цель {} — перебрано стоянок {}, ни одна не достижима ({})",
                spirit.displayName(),
                target,
                rejected,
                rejection == null ? "подходящих стоянок нет" : rejection);
        finish();
        return Approach.State.STUCK;
    }

    private boolean standReachable(BlockCoord feet) {
        probes++;
        boolean reachable = routes
                && spirit.level() instanceof ServerLevel level
                && PathNetworkService.route(level, feet(), feet).isPresent();
        if (!reachable) {
            Path path = spirit.getNavigation().createPath(new BlockPos(feet.x(), feet.y(), feet.z()), 1);
            reachable = path != null && path.canReach();
        }
        if (!reachable) {
            reachable = twoWayWalk(feet);
        }
        if (!reachable) {
            rejected++;
            rejection = "до стоянки нет пути пешком от " + feet();
        }
        return reachable;
    }

    private boolean twoWayWalk(BlockCoord stand) {
        BlockCoord from = feet();
        int margin = 8;
        int minX = Math.min(from.x(), stand.x()) - margin;
        int maxX = Math.max(from.x(), stand.x()) + margin;
        int minZ = Math.min(from.z(), stand.z()) - margin;
        int maxZ = Math.max(from.z(), stand.z()) + margin;
        int minY = Math.min(from.y(), stand.y()) - margin;
        int maxY = Math.max(from.y(), stand.y()) + margin;
        return RetreatPath.walkable(
                        from,
                        new NetworkLevelView(spirit.level()),
                        cell -> cell.x() >= minX
                                && cell.x() <= maxX
                                && cell.z() >= minZ
                                && cell.z() <= maxZ
                                && cell.y() >= minY
                                && cell.y() <= maxY)
                .map(cells -> cells.contains(stand))
                .orElse(false);
    }

    private void finish() {
        approach.cancel();
        active = false;
    }

    private BlockCoord feet() {
        BlockPos pos = spirit.blockPosition();
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
