package com.theminespirit.neoforge.board;

import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.TickWatch;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class BoardLifecycle {
    private BoardLifecycle() {}

    public static void register(IEventBus bus) {
        if (bus == null) {
            throw new IllegalArgumentException("bus не может быть null: без шины слушатель доски не встанет");
        }
        bus.addListener((ServerTickEvent.Post event) -> onServerTick(event));
        bus.addListener((ServerStoppingEvent event) -> onServerStopping());
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer() == null || !SpiritConfig.boardEnabled()) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            long watch = TickWatch.start();
            long tick = level.getGameTime();
            try {
                BoardService service = BoardRegistry.get(level);
                service.tick(level, tick);
            } finally {
                TickWatch.done("BoardService", watch);
            }
        }
    }

    private static void onServerStopping() {
        BoardRegistry.closeAll();
        BoardRegistry.clear();
    }
}
