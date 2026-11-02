// lib: Из Baritone (github.com/cabaletta/baritone, src/main/java/baritone/cache/CachedWorld.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class NavCachedWorld {
    private final Map<Long, NavCachedRegion> regions = new HashMap<>();

    public void put(NavCachedChunk chunk) {
        NavCachedRegion region = regions.computeIfAbsent(
                key(chunk.chunkX() >> 5, chunk.chunkZ() >> 5),
                ignored -> new NavCachedRegion(chunk.chunkX() >> 5, chunk.chunkZ() >> 5));
        region.put(chunk);
    }

    public @Nullable NavCachedChunk chunk(int chunkX, int chunkZ) {
        NavCachedRegion region = regions.get(key(chunkX >> 5, chunkZ >> 5));
        return region == null ? null : region.chunk(chunkX, chunkZ);
    }

    public boolean isCached(int blockX, int blockZ) {
        return chunk(blockX >> 4, blockZ >> 4) != null;
    }

    public @Nullable NavBlockType type(int blockX, int blockY, int blockZ) {
        NavCachedChunk chunk = chunk(blockX >> 4, blockZ >> 4);
        return chunk == null ? null : chunk.type(blockX & 15, blockY, blockZ & 15);
    }

    public int cachedChunks() {
        int count = 0;
        for (NavCachedRegion region : regions.values()) {
            count += region.cachedChunks();
        }
        return count;
    }

    public int cachedBytes() {
        int bytes = 0;
        for (NavCachedRegion region : regions.values()) {
            for (int chunkX = region.regionX() << 5; chunkX <= (region.regionX() << 5) + 31; chunkX++) {
                for (int chunkZ = region.regionZ() << 5; chunkZ <= (region.regionZ() << 5) + 31; chunkZ++) {
                    NavCachedChunk chunk = region.chunk(chunkX, chunkZ);
                    if (chunk != null) {
                        bytes += chunk.sizeInBytes();
                    }
                }
            }
        }
        return bytes;
    }

    private static long key(int regionX, int regionZ) {
        return ((long) regionX << 32) | (regionZ & 0xffffffffL);
    }
}
