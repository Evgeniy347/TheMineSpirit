package com.theminespirit.neoforge.board;

import com.theminespirit.neoforge.SpiritConfig;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Доска на шине событий: тик раз в BOARD_INTERVAL_TICKS по расписанию уровня. */
public final class BoardLifecycle {
    private BoardLifecycle() {
    }

    public static void register(IEventBus bus) {
        if (bus == null) {
            throw new IllegalArgumentException("bus не может быть null: без шины слушатель доски не встанет");
        }
        bus.addListener((ServerTickEvent.Post event) -> onServerTick(event));
        bus.addListener((ServerStoppingEvent event) -> onServerStopping(event));
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer() == null || !SpiritConfig.boardEnabled()) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            BoardRegistry.get(level).tick(level, level.getGameTime());
        }
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        BoardRegistry.closeAll();
        BoardRegistry.clear();
    }
}
