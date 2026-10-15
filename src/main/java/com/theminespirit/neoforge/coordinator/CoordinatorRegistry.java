package com.theminespirit.neoforge.coordinator;

import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CoordinatorRegistry {
    private static final Map<ServerLevel, SpiritCoordinator> COORDINATORS = new ConcurrentHashMap<>();

    private CoordinatorRegistry() {
    }

    public static SpiritCoordinator get(ServerLevel level) {
        if (level == null) {
            throw new IllegalArgumentException("level не может быть null: реестр без уровня не отдаёт координатора");
        }
        return COORDINATORS.computeIfAbsent(level, SpiritCoordinator::new);
    }

    public static void remove(ServerLevel level) {
        if (level == null) {
            return;
        }
        COORDINATORS.remove(level);
    }

    public static void clear() {
        COORDINATORS.clear();
    }

    public static int size() {
        return COORDINATORS.size();
    }
}
