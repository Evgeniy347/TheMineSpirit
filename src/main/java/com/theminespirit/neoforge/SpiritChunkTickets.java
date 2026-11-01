package com.theminespirit.neoforge;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.chunk.ChunkCoord;
import com.theminespirit.core.chunk.ChunkDiff;
import com.theminespirit.core.chunk.SpiritTicketRetention;
import com.theminespirit.core.observation.BlockCoord;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;

public final class SpiritChunkTickets {
    public static final ResourceLocation CONTROLLER_ID =
            ResourceLocation.fromNamespaceAndPath(ModIdentity.MOD_ID, "spirit");

    private static final TicketController CONTROLLER =
            new TicketController(CONTROLLER_ID, SpiritChunkTickets::validateTickets);

    private SpiritChunkTickets() {}

    public static void registerControllers(RegisterTicketControllersEvent event) {
        event.register(CONTROLLER);
    }

    public static void apply(ServerLevel level, Entity owner, ChunkDiff diff) {
        for (ChunkCoord chunk : diff.toRemove()) {
            CONTROLLER.forceChunk(level, owner, chunk.x(), chunk.z(), false, true);
        }
        for (ChunkCoord chunk : diff.toAdd()) {
            CONTROLLER.forceChunk(level, owner, chunk.x(), chunk.z(), true, true);
        }
    }

    public static void wakeAll(ServerLevel level) {
        for (Map.Entry<UUID, BlockCoord> entry :
                SpiritRegistry.get(level).positions().entrySet()) {
            BlockCoord position = entry.getValue();
            CONTROLLER.forceChunk(
                    level,
                    entry.getKey(),
                    Math.floorDiv(position.x(), 16),
                    Math.floorDiv(position.z(), 16),
                    true,
                    true);
        }
    }

    private static void validateTickets(ServerLevel level, TicketHelper helper) {
        Set<UUID> owners = new LinkedHashSet<>(helper.getEntityTickets().keySet());
        Set<UUID> keep = SpiritTicketRetention.keepAtLoad(
                owners, SpiritRegistry.find(level).map(SpiritRegistry::alive));
        for (UUID owner : owners) {
            if (!keep.contains(owner)) {
                helper.removeAllTickets(owner);
            }
        }
    }
}
