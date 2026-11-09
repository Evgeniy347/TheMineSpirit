package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.TickMeter;
import com.theminespirit.neoforge.TickWatch;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

public final class BoardLifecycle {
    private static final Logger LOGGER = LogUtils.getLogger();

    private BoardLifecycle() {}

    public static void register(IEventBus bus) {
        if (bus == null) {
            throw new IllegalArgumentException("bus не может быть null: без шины слушатель доски не встанет");
        }
        bus.addListener((ServerTickEvent.Pre event) -> TickMeter.start());
        bus.addListener((ServerTickEvent.Post event) -> onServerTick(event));
        bus.addListener((ServerStoppingEvent event) -> onServerStopping());
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        TickMeter.finish().ifPresent(LOGGER::info);
        if (event.getServer() == null || !SpiritConfig.boardEnabled()) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            FloatingWatch.tick(level);
            long watch = TickWatch.start();
            long tick = level.getGameTime();
            try {
                BoardService service = BoardRegistry.get(level);
                service.tick(level, tick);
                StuckLog.log(level, service.board());
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
