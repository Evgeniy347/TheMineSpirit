package com.theminespirit.neoforge;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.chunk.ChunkCoord;
import com.theminespirit.core.chunk.ChunkDiff;
import com.theminespirit.core.chunk.ChunkSquare;
import com.theminespirit.core.chunk.SettlementChunks;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;

public final class SettlementChunkTickets {
    public static final ResourceLocation CONTROLLER_ID =
            ResourceLocation.fromNamespaceAndPath(ModIdentity.MOD_ID, "settlement");

    private static final UUID OWNER =
            UUID.nameUUIDFromBytes((ModIdentity.MOD_ID + ":settlement").getBytes(StandardCharsets.UTF_8));
    private static final TicketController CONTROLLER =
            new TicketController(CONTROLLER_ID, SettlementChunkTickets::validateTickets);
    private static final Map<ServerLevel, Set<ChunkCoord>> HELD = new ConcurrentHashMap<>();

    private SettlementChunkTickets() {}

    public static void registerControllers(RegisterTicketControllersEvent event) {
        event.register(CONTROLLER);
    }

    static void tick(ServerLevel level) {
        TownPlan plan = SettlementPlotStore.get(level).town().orElse(null);
        var anchors = SettlementAnchorStore.get(level).anchors();
        Set<ChunkCoord> settlement =
                new TreeSet<>(SettlementChunks.forPlan(plan, anchors, SettlementChunks.WORK_RADIUS_BLOCKS));
        settlement.addAll(SettlementChunks.around(
                anchors, DataTables.INSTANCE.town().current().ground().probeRadius()));
        Set<ChunkCoord> held = HELD.getOrDefault(level, Set.of());
        ChunkDiff diff = ChunkDiff.between(held, settlement);
        if (!diff.isEmpty()) {
            for (ChunkCoord chunk : diff.toRemove()) {
                CONTROLLER.forceChunk(level, OWNER, chunk.x(), chunk.z(), false, true);
            }
            for (ChunkCoord chunk : diff.toAdd()) {
                CONTROLLER.forceChunk(level, OWNER, chunk.x(), chunk.z(), true, true);
            }
        }
        HELD.put(level, ChunkSquare.apply(held, diff));
    }

    static void clear() {
        HELD.clear();
    }

    private static void validateTickets(ServerLevel level, TicketHelper helper) {
        if (level == null || helper == null) {
            throw new IllegalArgumentException("Билеты поселения без уровня или без набора билетов не проверить");
        }
    }
}
