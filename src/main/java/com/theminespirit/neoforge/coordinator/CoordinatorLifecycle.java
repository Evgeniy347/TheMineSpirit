package com.theminespirit.neoforge.coordinator;

import com.theminespirit.core.coordinator.SettlementGroups;
import com.theminespirit.core.coordinator.SettlementKey;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class CoordinatorLifecycle {
    private CoordinatorLifecycle() {}

    public static void register(IEventBus bus) {
        if (bus == null) {
            throw new IllegalArgumentException(
                    "bus не может быть null: без шины событий слушатели координатора не зарегистрируются");
        }
        bus.addListener((ServerTickEvent.Post event) -> onServerTick(event));
        bus.addListener((ServerStoppingEvent event) -> onServerStopping());
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer() == null || !SpiritConfig.coordinatorEnabled()) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            long tick = level.getGameTime();
            if (!CoordinatorRegistry.beginSnapshotPeriod(level, tick)) {
                continue;
            }
            Map<SettlementKey, List<SpiritEntity>> groups = census(level);
            for (Map.Entry<SettlementKey, List<SpiritEntity>> group : groups.entrySet()) {
                CoordinatorRegistry.get(level, group.getKey()).tick(group.getValue(), tick);
            }
            CoordinatorRegistry.pruneIdle(level, tick);
        }
    }

    private static Map<SettlementKey, List<SpiritEntity>> census(ServerLevel level) {
        Map<SettlementKey, List<SpiritEntity>> groups = new LinkedHashMap<>();
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof SpiritEntity spirit) || spirit.isRemoved()) {
                continue;
            }
            SettlementKey key = SettlementGroups.keyOf(spirit.home(), spirit.getUUID());
            groups.computeIfAbsent(key, group -> new ArrayList<>()).add(spirit);
        }
        return groups;
    }

    private static void onServerStopping() {
        CoordinatorRegistry.clear();
    }
}
