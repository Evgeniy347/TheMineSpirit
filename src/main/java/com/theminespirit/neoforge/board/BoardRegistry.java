package com.theminespirit.neoforge.board;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;

public final class BoardRegistry {
    private static final Map<ServerLevel, BoardService> BY_LEVEL = new ConcurrentHashMap<>();

    private BoardRegistry() {}

    public static BoardService get(ServerLevel level) {
        if (level == null) {
            throw new IllegalArgumentException("level не может быть null: доска привязана к своему уровню");
        }
        BoardService service = BY_LEVEL.computeIfAbsent(level, BoardService::create);
        StageBootstrap.apply(level, service);
        return service;
    }

    public static Optional<BoardService> peek(ServerLevel level) {
        return level == null ? Optional.empty() : Optional.ofNullable(BY_LEVEL.get(level));
    }

    public static void remove(ServerLevel level) {
        if (level == null) {
            return;
        }
        BY_LEVEL.remove(level);
        StageBootstrap.forget(level);
    }

    public static void closeAll() {
        BY_LEVEL.values().forEach(BoardService::close);
    }

    public static void clear() {
        BY_LEVEL.clear();
    }

    public static int size() {
        return BY_LEVEL.size();
    }
}
