package com.theminespirit.neoforge;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Правило владельца 21.10: без игроков на сервере чанки поселения и духи выгружены; как только игрок
 * подключён (в любом мире), поселение загружено целиком, а духи разбужены по сохранённым координатам.
 */
public final class SettlementChunkLifecycle {
    private static final Map<ServerLevel, Boolean> ONLINE = new ConcurrentHashMap<>();

    private SettlementChunkLifecycle() {}

    public static void register(IEventBus bus) {
        if (bus == null) {
            throw new IllegalArgumentException(
                    "bus не может быть null: без шины слушатель чанков поселения не встанет");
        }
        bus.addListener((ServerTickEvent.Post event) -> onServerTick(event));
        bus.addListener((ServerStoppingEvent event) -> clear());
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        boolean anyPlayerOnline = !server.getPlayerList().getPlayers().isEmpty();
        for (ServerLevel level : server.getAllLevels()) {
            boolean wasOnline = ONLINE.getOrDefault(level, false);
            if (anyPlayerOnline && !wasOnline) {
                SpiritChunkTickets.wakeAll(level);
            }
            ONLINE.put(level, anyPlayerOnline);
            SettlementChunkTickets.tick(level, anyPlayerOnline);
        }
    }

    private static void clear() {
        ONLINE.clear();
        SettlementChunkTickets.clear();
    }
}
