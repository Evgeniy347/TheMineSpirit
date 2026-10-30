package com.theminespirit.neoforge;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.chunk.ChunkCoord;
import com.theminespirit.core.chunk.ChunkDiff;
import com.theminespirit.core.chunk.ChunkSquare;
import com.theminespirit.core.chunk.SettlementChunks;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
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

    static void tick(ServerLevel level, boolean anyPlayerOnline) {
        TownPlan plan = SettlementPlotStore.get(level).town().orElse(null);
        Set<ChunkCoord> settlement = SettlementChunks.forPlan(
                plan, SettlementAnchorStore.get(level).anchors(), SettlementChunks.WORK_RADIUS_BLOCKS);
        Set<ChunkCoord> held = HELD.getOrDefault(level, Set.of());
        ChunkDiff diff = SettlementChunks.transition(held, settlement, anyPlayerOnline);
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
        if (!level.getServer().getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        for (UUID owner : new ArrayList<>(helper.getEntityTickets().keySet())) {
            helper.removeAllTickets(owner);
        }
    }
}
