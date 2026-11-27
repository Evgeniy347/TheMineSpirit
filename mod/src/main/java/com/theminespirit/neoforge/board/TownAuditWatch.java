package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.ConstructionBatches.ChunkCell;
import com.theminespirit.core.board.audit.AuditBudgets;
import com.theminespirit.core.board.audit.AuditFill;
import com.theminespirit.core.board.audit.AuditRegistry;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.SettlementPlot;
import com.theminespirit.core.town.TownPlan;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class TownAuditWatch {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int BELOW = 16;
    private static final int ABOVE = 31;
    private static final Map<ServerLevel, AuditRegistry> BY_LEVEL = new ConcurrentHashMap<>();

    private TownAuditWatch() {}

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        try {
            ChunkPos pos = event.getChunk().getPos();
            registry(level).loaded(new ChunkCell(pos.x, pos.z));
        } catch (RuntimeException error) {
            LOGGER.warn("[audit] чанк не встал в очередь наполнения: {}", error.getMessage(), error);
        }
    }

    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        AuditRegistry registry = BY_LEVEL.get(level);
        ChunkPos pos = event.getChunk().getPos();
        if (registry != null) {
            registry.unloaded(new ChunkCell(pos.x, pos.z));
        }
    }

    public static void tick(ServerLevel level) {
        try {
            SettlementPlotStore store = SettlementPlotStore.get(level);
            Optional<SettlementPlot> plot = store.plot();
            Optional<TownPlan> town = store.town();
            if (plot.isEmpty() || town.isEmpty()) {
                BY_LEVEL.remove(level);
                return;
            }
            int levelY = plot.orElseThrow().anchor().y();
            AuditRegistry registry = registry(level);
            if (registry.plan(store.planRevision(), levelY)) {
                Set<ChunkCell> chunks = regionChunks(town.orElseThrow().region());
                registry.retain(chunks);
                chunks.forEach(registry::ensure);
                LOGGER.info(
                        "[audit] окно кэша поселения: levelY={}, y={}, ревизия {}, чанков={}, в очереди={}",
                        levelY,
                        registry.minY(),
                        registry.planRevision(),
                        registry.caches(),
                        registry.waiting());
            }
            registry.fill((AuditFill.Reader) cell -> blockAt(level, cell), level.getGameTime());
        } catch (RuntimeException error) {
            LOGGER.warn("[audit] наполнение кэша поселения не продвинулось: {}", error.getMessage(), error);
        }
    }

    public static void forget(ServerLevel level) {
        BY_LEVEL.remove(level);
    }

    static Set<ChunkCell> regionChunks(ColumnMask region) {
        Set<ChunkCell> chunks = new HashSet<>();
        for (ColumnMask.Cell cell : region.cells()) {
            chunks.add(new ChunkCell(cell.x() >> 4, cell.z() >> 4));
        }
        return chunks;
    }

    private static AuditRegistry registry(ServerLevel level) {
        return BY_LEVEL.computeIfAbsent(level, ignored -> new AuditRegistry(AuditBudgets.current(), BELOW, ABOVE));
    }

    private static @Nullable String blockAt(ServerLevel level, BlockCoord cell) {
        BlockPos at = new BlockPos(cell.x(), cell.y(), cell.z());
        if (!level.isLoaded(at)) {
            return null;
        }
        return BuiltInRegistries.BLOCK
                .getKey(level.getBlockState(at).getBlock())
                .toString();
    }
}
