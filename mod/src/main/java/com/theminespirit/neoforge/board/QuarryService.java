package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.route.QuarryArea;
import com.theminespirit.core.board.route.QuarryCandidate;
import com.theminespirit.core.board.route.QuarryGrid;
import com.theminespirit.core.board.route.QuarryLimits;
import com.theminespirit.core.board.route.QuarryPaths;
import com.theminespirit.core.board.route.QuarryPlan;
import com.theminespirit.core.board.route.QuarryPlanner;
import com.theminespirit.core.board.route.QuarryRanking;
import com.theminespirit.core.board.route.QuarrySite;
import com.theminespirit.core.board.route.QuarryStatus;
import com.theminespirit.core.board.route.RouteMarker;
import com.theminespirit.core.board.route.RouteResource;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.StageSite;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

public final class QuarryService {
    public static final int SEARCH_LIMIT = QuarryLimits.SEARCH_LIMIT;
    private static final long CACHE_TICKS = 200;
    private static final Map<ServerLevel, Cache> CACHE = new WeakHashMap<>();

    private record Cache(long tick, List<QuarryStatus> statuses) {}

    private QuarryService() {}

    public static List<QuarryStatus> cachedStatuses(ServerLevel level) {
        long now = level.getGameTime();
        Cache cache = CACHE.get(level);
        if (cache != null && now >= cache.tick() && now - cache.tick() < CACHE_TICKS) {
            return cache.statuses();
        }
        List<QuarryStatus> fresh = statuses(level);
        CACHE.put(level, new Cache(now, fresh));
        return fresh;
    }

    private static void invalidate(ServerLevel level) {
        CACHE.remove(level);
    }

    public static List<QuarryStatus> statuses(ServerLevel level) {
        List<QuarryStatus> out = new ArrayList<>();
        RouteStore store = RouteStore.get(level);
        Optional<StageSite> stage = SettlementPlotStore.liveSite(level);
        for (RouteResource resource : RouteResource.values()) {
            Optional<RouteMarker> marker = store.plan().marker(resource);
            if (marker.isEmpty()) {
                out.add(QuarryStatus.unset(resource));
                continue;
            }
            QuarryStatus status = status(level, marker.orElseThrow(), stage.orElse(null), true);
            out.add(status == null ? QuarryStatus.unset(resource) : status);
        }
        return List.copyOf(out);
    }

    public static QuarryStatus status(ServerLevel level, RouteResource resource) {
        RouteStore store = RouteStore.get(level);
        Optional<RouteMarker> marker = store.plan().marker(resource);
        if (marker.isEmpty()) {
            return QuarryStatus.unset(resource);
        }
        StageSite stage = SettlementPlotStore.liveSite(level).orElse(null);
        QuarryStatus status = status(level, marker.orElseThrow(), stage, true);
        if (status == null) {
            throw new IllegalStateException("Место добычи " + resource + " не оценено: мир не загрузил его область");
        }
        return status;
    }

    static @Nullable QuarryStatus status(
            ServerLevel level, RouteMarker marker, @Nullable StageSite stage, boolean forceLoad) {
        QuarryArea area = new QuarryArea(marker.resource(), marker.point(), marker.radius());
        if (forceLoad) {
            QuarryWorld.load(level, area);
        }
        QuarryWorld probe = QuarryWorld.of(level, area, stage);
        if (!probe.loaded(area.point().x(), area.point().z())) {
            return null;
        }
        QuarryPlan plan = QuarryPlanner.plan(area, probe);
        return QuarryStatus.of(marker.site(), plan.reserve(), marker.pathBuilt(), marker.routeBuilt());
    }

    public static QuarryStatus install(ServerLevel level, QuarrySite site) {
        StageSite stage = SettlementPlotStore.liveSite(level).orElse(null);
        QuarryArea area = site.area();
        QuarryWorld.load(level, area);
        QuarryPlan plan = QuarryPlanner.plan(area, QuarryWorld.of(level, area, stage));
        if (plan.reserve() == 0) {
            throw new IllegalArgumentException(
                    "В области " + area.point().x() + "," + area.point().z() + " радиусом "
                            + area.radius() + " нет добываемого ресурса " + site.resource()
                            + ": место не ставится — иначе тропа ведёт к пустой яме");
        }
        QuarrySite stored = new QuarrySite(site.resource(), site.point(), site.radius(), site.biome(), plan.reserve());
        RouteStore store = RouteStore.get(level);
        store.mark(stored);
        invalidate(level);
        return QuarryStatus.of(
                stored,
                plan.reserve(),
                store.plan().pathDone(stored.resource()),
                store.plan().routeDone(stored.resource()));
    }

    public static QuarryStatus remove(ServerLevel level, RouteResource resource) {
        RouteStore.get(level).clear(resource);
        invalidate(level);
        return QuarryStatus.unset(resource);
    }

    public static List<QuarryCandidate> search(
            ServerLevel level, RouteResource resource, @Nullable BlockCoord base, int min, int max) {
        BlockCoord origin = base == null ? basePoint(level) : base;
        List<QuarryCandidate> candidates = new ArrayList<>();
        StageSite stage = SettlementPlotStore.liveSite(level).orElse(null);
        for (BlockCoord point : QuarryGrid.points(origin, min, max)) {
            QuarryArea area = QuarryArea.at(resource, point);
            QuarryWorld.load(level, area);
            QuarryPlan plan = QuarryPlanner.plan(area, QuarryWorld.of(level, area, stage));
            if (plan.reserve() == 0) {
                continue;
            }
            int feet = RouteWorks.standY(level, point.x(), point.z(), origin.y());
            BlockCoord sitePoint = new BlockCoord(point.x(), feet, point.z());
            candidates.add(new QuarryCandidate(
                    resource,
                    sitePoint,
                    area.radius(),
                    plan.reserve(),
                    QuarryPaths.length(origin, sitePoint),
                    biome(level, sitePoint)));
        }
        return QuarryRanking.best(candidates, SEARCH_LIMIT);
    }

    public static BlockCoord basePoint(ServerLevel level) {
        Optional<StageSite> stage = SettlementPlotStore.liveSite(level);
        if (stage.isPresent()) {
            return stage.orElseThrow().anchor();
        }
        BlockPos spawn = level.getSharedSpawnPos();
        return new BlockCoord(spawn.getX(), spawn.getY(), spawn.getZ());
    }

    private static String biome(ServerLevel level, BlockCoord point) {
        return level.getBiome(new BlockPos(point.x(), point.y(), point.z()))
                .unwrapKey()
                .map(key -> key.location().toString())
                .orElse("");
    }
}
