package com.theminespirit.neoforge.coordinator;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class CoordinatorLifecycle {
    private CoordinatorLifecycle() {
    }

    public static void register(IEventBus bus) {
        if (bus == null) {
            throw new IllegalArgumentException(
                    "bus не может быть null: без шины событий слушатели координатора не зарегистрируются");
        }
        bus.addListener((ServerTickEvent.Post event) -> onServerTick(event));
        bus.addListener((ServerStoppingEvent event) -> onServerStopping(event));
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer() == null) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            CoordinatorRegistry.get(level).tick();
        }
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        CoordinatorRegistry.clear();
    }
}
