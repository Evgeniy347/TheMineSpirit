package com.theminespirit.neoforge.demo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;

/** Демо-стройки по уровням: список активных сессий живёт в памяти сервера и не переживает перезапуск. */
public final class DemoRegistry {
    private static final Map<ServerLevel, DemoService> BY_LEVEL = new ConcurrentHashMap<>();

    private DemoRegistry() {}

    public static DemoService get(ServerLevel level) {
        if (level == null) {
            throw new IllegalArgumentException("level не может быть null: демо-стройка привязана к уровню");
        }
        return BY_LEVEL.computeIfAbsent(level, DemoService::create);
    }

    public static void clear() {
        BY_LEVEL.clear();
    }
}
