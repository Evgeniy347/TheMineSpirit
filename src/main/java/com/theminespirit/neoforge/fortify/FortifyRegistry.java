package com.theminespirit.neoforge.fortify;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;

public final class FortifyRegistry {
    private static final Map<ServerLevel, FortifyService> BY_LEVEL = new WeakHashMap<>();

    private FortifyRegistry() {
    }

    public static synchronized FortifyService get(ServerLevel level) {
        if (level == null) {
            throw new IllegalArgumentException("level не может быть null: служба укрепления живёт на уровне");
        }
        return BY_LEVEL.computeIfAbsent(level, FortifyService::new);
    }

    public static synchronized void clear() {
        BY_LEVEL.clear();
    }
}