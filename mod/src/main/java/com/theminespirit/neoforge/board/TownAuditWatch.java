package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.ConstructionBatches.ChunkCell;
import com.theminespirit.core.board.audit.AuditBudgets;
import com.theminespirit.core.board.audit.AuditFill;
import com.theminespirit.core.board.audit.AuditRegistry;
import com.theminespirit.core.board.audit.ChangeRing;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.SettlementPlot;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.lease.LeaseStates;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class TownAuditWatch {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int BELOW = 16;
    private static final int ABOVE = 31;
    private static final int DRAIN_TICKS = 20;
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
            if (level.getGameTime() % DRAIN_TICKS == 0) {
                List<ChangeRing.Change> drained = registry.drain();
                if (!drained.isEmpty()) {
                    LOGGER.debug(
                            "[audit] разобрано изменений: {}, грязных клеток: {}",
                            drained.size(),
                            registry.dirty().size());
                }
            }
        } catch (RuntimeException error) {
            LOGGER.warn("[audit] наполнение кэша поселения не продвинулось: {}", error.getMessage(), error);
        }
    }

    public static void spiritChanged(
            ServerLevel level, BlockPos pos, BlockState before, BlockState after, SpiritEntity spirit, String basis) {
        changed(level, pos, before, after, ChangeRing.Actor.SPIRIT, basis, ticketOf(level, spirit));
    }

    public static void playerChanged(
            ServerLevel level, BlockPos pos, BlockState before, BlockState after, String basis) {
        changed(level, pos, before, after, ChangeRing.Actor.PLAYER, basis, "");
    }

    public static void explosionChanged(ServerLevel level, BlockPos pos, BlockState before, BlockState after) {
        changed(level, pos, before, after, ChangeRing.Actor.EXPLOSION, "взрыв", "");
    }

    public static void naturalChanged(ServerLevel level, BlockPos pos) {
        AuditRegistry registry = peek(level);
        if (registry == null) {
            return;
        }
        BlockCoord cell = new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
        String before = registry.blockAt(cell);
        String after = blockAt(level, cell);
        if (before == null || after == null) {
            return;
        }
        record(level, pos, before, after, ChangeRing.Actor.NATURAL, "соседнее обновление", "");
    }

    public static @Nullable AuditRegistry peek(ServerLevel level) {
        return BY_LEVEL.get(level);
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

    private static void changed(
            ServerLevel level,
            BlockPos pos,
            BlockState before,
            BlockState after,
            ChangeRing.Actor actor,
            String basis,
            String ticket) {
        record(level, pos, LeaseStates.text(before), LeaseStates.text(after), actor, basis, ticket);
    }

    private static void record(
            ServerLevel level,
            BlockPos pos,
            String before,
            String after,
            ChangeRing.Actor actor,
            String basis,
            String ticket) {
        AuditRegistry registry = peek(level);
        if (registry == null) {
            return;
        }
        registry.record(new ChangeRing.Change(
                level.getGameTime(),
                new BlockCoord(pos.getX(), pos.getY(), pos.getZ()),
                before,
                after,
                actor,
                basis,
                ticket,
                ""));
    }

    private static String ticketOf(ServerLevel level, SpiritEntity spirit) {
        return BoardRegistry.get(level).board().assignedTo(spirit.getUUID()).stream()
                .map(BoardTicket::id)
                .findFirst()
                .orElse("");
    }

    private static @Nullable String blockAt(ServerLevel level, BlockCoord cell) {
        BlockPos at = new BlockPos(cell.x(), cell.y(), cell.z());
        if (!level.isLoaded(at)) {
            return null;
        }
        return LeaseStates.text(level.getBlockState(at));
    }
}
