package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.chunk.ChunkCoord;
import com.theminespirit.core.chunk.SettlementChunks;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

public final class SettlementChunkLifecycle {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<ServerLevel> WOKEN = ConcurrentHashMap.newKeySet();
    private static final Set<ServerLevel> REPORTED = ConcurrentHashMap.newKeySet();

    private SettlementChunkLifecycle() {}

    public static void register(IEventBus bus) {
        if (bus == null) {
            throw new IllegalArgumentException(
                    "bus не может быть null: без шины событий слушатель чанков поселения не встанет");
        }
        bus.addListener((ServerTickEvent.Post event) -> onServerTick(event));
        bus.addListener((ServerStoppingEvent event) -> clear());
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            if (WOKEN.add(level)) {
                SpiritChunkTickets.wakeAll(level);
            }
            List<BlockCoord> spirits =
                    new ArrayList<>(SpiritRegistry.get(level).positions().values());
            TownPlan plan = SettlementPlotStore.get(level).town().orElse(null);
            Set<ChunkCoord> zone = SettlementChunks.workZone(
                    plan, SettlementAnchorStore.get(level).anchors(), spirits, SpiritConfig.radiusFor(level));
            WorkZoneRegistry.update(level, zone);
            SettlementChunkTickets.tick(level);
            if (REPORTED.add(level)) {
                LOGGER.info(
                        "[forceload] зона поселения и работ: {} чанков, духов {} ({}), в /forceload {}",
                        zone.size(),
                        spirits.size(),
                        spirits,
                        level.getForcedChunks().size());
            }
        }
    }

    private static void clear() {
        WOKEN.clear();
        REPORTED.clear();
        WorkZoneRegistry.clear();
        SettlementChunkTickets.clear();
    }
}
