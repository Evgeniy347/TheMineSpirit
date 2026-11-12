package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.AccessPath;
import com.theminespirit.core.town.SettlementScan;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.SettlementChestStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class AccessScan {
    static final String EVENT = "access_blocked";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CACHE_TICKS = 600;
    private static final int STATION_RADIUS = 16;
    private static final int STATION_BELOW = 1;
    private static final int STATION_ABOVE = 1;
    private static final List<String> STATIONS = List.of("crafting_table", "furnace", "_door");
    private static final List<String> KEEP = List.of(
            "chest",
            "barrel",
            "crafting_table",
            "furnace",
            "smoker",
            "_door",
            "_bed",
            "anvil",
            "fence",
            "_gate",
            "torch",
            "lantern",
            "ladder");
    private static final Predicate<String> KEPT = id -> KEEP.stream().anyMatch(id::contains);
    private static final Map<ServerLevel, Cached> LAST = new WeakHashMap<>();
    private static final Object LOCK = new Object();

    private record Cached(long bucket, BlockCoord home, AccessPath.Plan plan) {}

    private AccessScan() {}

    static AccessPath.Plan plan(ServerLevel level, StageSite site, BlockCoord home) {
        synchronized (LOCK) {
            return planLocked(level, site, home);
        }
    }

    private static AccessPath.Plan planLocked(ServerLevel level, StageSite site, BlockCoord home) {
        long bucket = level.getGameTime() / CACHE_TICKS;
        Cached before = LAST.get(level);
        if (before != null && before.bucket() == bucket) {
            return before.plan();
        }
        long started = System.nanoTime();
        SettlementScan.World world = WorldAreaScan.view(level, site);
        AccessPath.Plan plan = AccessPath.plan(world, KEPT, home, targets(level, home));
        long millis = (System.nanoTime() - started) / 1_000_000L;
        if (!plan.clear() || !plan.unreachable().isEmpty()) {
            LOGGER.info(
                    "[access] от дома {} за {} мс: срезать {}, подсыпать {}, недостижимо {} — проход к сундукам, "
                            + "станциям и дверям",
                    home,
                    millis,
                    plan.cut().size(),
                    plan.fill().size(),
                    plan.unreachable());
        }
        LAST.put(level, new Cached(bucket, home, plan));
        return plan;
    }

    static boolean blocked(ServerLevel level, StageSite site, Optional<BlockCoord> home) {
        return home.isPresent() && !plan(level, site, home.orElseThrow()).clear();
    }

    static List<BlockCoord> targets(ServerLevel level, BlockCoord home) {
        List<BlockCoord> targets = new ArrayList<>();
        for (BlockCoord chest : SettlementChestStore.get(level).chests().all()) {
            if (Math.max(Math.abs(chest.x() - home.x()), Math.abs(chest.z() - home.z())) <= AccessPath.RADIUS) {
                targets.add(chest);
            }
        }
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dx = -STATION_RADIUS; dx <= STATION_RADIUS; dx++) {
            for (int dz = -STATION_RADIUS; dz <= STATION_RADIUS; dz++) {
                for (int dy = -STATION_BELOW; dy <= STATION_ABOVE; dy++) {
                    at.set(home.x() + dx, home.y() + dy, home.z() + dz);
                    if (!level.isLoaded(at)) {
                        continue;
                    }
                    String id = level.getBlockState(at).getBlock().getDescriptionId();
                    if (STATIONS.stream().anyMatch(id::endsWith) && !lowerDoorHalf(level, at, id)) {
                        targets.add(new BlockCoord(at.getX(), at.getY(), at.getZ()));
                    }
                }
            }
        }
        return List.copyOf(targets);
    }

    private static boolean lowerDoorHalf(ServerLevel level, BlockPos at, String id) {
        return id.endsWith("_door")
                && level.getBlockState(at.below()).getBlock().getDescriptionId().equals(id);
    }
}
