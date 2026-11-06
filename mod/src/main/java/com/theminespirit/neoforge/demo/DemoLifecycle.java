package com.theminespirit.neoforge.demo;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class DemoLifecycle {
    private DemoLifecycle() {}

    public static void register(IEventBus bus) {
        if (bus == null) {
            throw new IllegalArgumentException("bus не может быть null: без шины слушатель демо-строек не встанет");
        }
        bus.addListener((ServerTickEvent.Post event) -> onServerTick(event));
        bus.addListener((ServerStoppingEvent event) -> DemoRegistry.clear());
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            DemoRegistry.get(level).tick();
        }
    }
}
