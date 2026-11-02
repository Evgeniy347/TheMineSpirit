// lib: Из Baritone (github.com/cabaletta/baritone, src/main/java/baritone/cache/CachedRegion.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import org.jspecify.annotations.Nullable;

public final class NavCachedRegion {
    public static final int CHUNKS = 32;
    private static final int MASK = CHUNKS - 1;

    private final int regionX;
    private final int regionZ;
    private final NavCachedChunk[] chunks = new NavCachedChunk[CHUNKS * CHUNKS];

    public NavCachedRegion(int regionX, int regionZ) {
        this.regionX = regionX;
        this.regionZ = regionZ;
    }

    public void put(NavCachedChunk chunk) {
        if ((chunk.chunkX() >> 5) != regionX || (chunk.chunkZ() >> 5) != regionZ) {
            throw new IllegalArgumentException("Чанк " + chunk + " не принадлежит региону " + regionX + "," + regionZ);
        }
        chunks[index(chunk.chunkX() & MASK, chunk.chunkZ() & MASK)] = chunk;
    }

    public @Nullable NavCachedChunk chunk(int chunkX, int chunkZ) {
        if ((chunkX >> 5) != regionX || (chunkZ >> 5) != regionZ) {
            return null;
        }
        return chunks[index(chunkX & MASK, chunkZ & MASK)];
    }

    public boolean isCached(int chunkX, int chunkZ) {
        return chunk(chunkX, chunkZ) != null;
    }

    public int cachedChunks() {
        int count = 0;
        for (NavCachedChunk chunk : chunks) {
            if (chunk != null) {
                count++;
            }
        }
        return count;
    }

    public int regionX() {
        return regionX;
    }

    public int regionZ() {
        return regionZ;
    }

    private static int index(int x, int z) {
        return z * CHUNKS + x;
    }
}
