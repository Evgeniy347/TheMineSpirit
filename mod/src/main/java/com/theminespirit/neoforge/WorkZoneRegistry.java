package com.theminespirit.neoforge;

import com.theminespirit.core.chunk.ChunkCoord;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;

public final class WorkZoneRegistry {
    private static final Map<ServerLevel, Set<ChunkCoord>> HELD = new ConcurrentHashMap<>();

    private WorkZoneRegistry() {}

    public static void update(ServerLevel level, Set<ChunkCoord> chunks) {
        HELD.put(level, chunks);
    }

    public static boolean holds(ServerLevel level, long chunkKey) {
        return holds(level, (int) chunkKey, (int) (chunkKey >> 32));
    }

    public static boolean holds(ServerLevel level, int chunkX, int chunkZ) {
        Set<ChunkCoord> chunks = HELD.get(level);
        return chunks != null && chunks.contains(new ChunkCoord(chunkX, chunkZ));
    }

    public static Set<ChunkCoord> held(ServerLevel level) {
        return HELD.getOrDefault(level, Set.of());
    }

    public static void clear() {
        HELD.clear();
    }
}
