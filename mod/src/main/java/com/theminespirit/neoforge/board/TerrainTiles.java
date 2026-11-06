package com.theminespirit.neoforge.board;

import com.theminespirit.core.ui.TerrainTile;
import com.theminespirit.core.ui.TownMapView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

public final class TerrainTiles {
    private static final int MARGIN = 48;
    private static final int TILE = TerrainTile.SIZE;
    private static final long TTL = 1200;
    private static final int CACHE_LIMIT = 8192;
    private static final Map<Key, Cached> CACHE = new ConcurrentHashMap<>();

    private TerrainTiles() {}

    public static List<TerrainTile> around(ServerLevel level, TownMapView.Bounds bounds) {
        int minChunkX = Math.floorDiv(bounds.x1() - MARGIN, TILE);
        int maxChunkX = Math.floorDiv(bounds.x2() + MARGIN, TILE);
        int minChunkZ = Math.floorDiv(bounds.z1() - MARGIN, TILE);
        int maxChunkZ = Math.floorDiv(bounds.z2() + MARGIN, TILE);
        if (CACHE.size() > CACHE_LIMIT) {
            CACHE.clear();
        }
        long now = level.getGameTime();
        List<TerrainTile> tiles = new ArrayList<>();
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                if (level.hasChunk(chunkX, chunkZ)) {
                    TerrainTile tile = new TerrainTile(chunkX, chunkZ, cached(level, chunkX, chunkZ, now));
                    if (tile.hasKnown()) {
                        tiles.add(tile);
                    }
                }
            }
        }
        return List.copyOf(tiles);
    }

    private static byte[] cached(ServerLevel level, int chunkX, int chunkZ, long now) {
        Key key = new Key(System.identityHashCode(level), level.dimension(), chunkX, chunkZ);
        Cached cached = CACHE.get(key);
        if (cached != null && now - cached.stamp() <= TTL) {
            return cached.ids();
        }
        byte[] ids = scan(level, chunkX, chunkZ);
        CACHE.put(key, new Cached(ids, now));
        return ids;
    }

    private static byte[] scan(ServerLevel level, int chunkX, int chunkZ) {
        byte[] ids = new byte[TerrainTile.COLUMNS];
        int minY = level.getMinBuildHeight();
        for (int localZ = 0; localZ < TILE; localZ++) {
            for (int localX = 0; localX < TILE; localX++) {
                int worldX = chunkX * TILE + localX;
                int worldZ = chunkZ * TILE + localZ;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ);
                if (top <= minY) {
                    ids[localZ * TILE + localX] = (byte) 0xFF;
                    continue;
                }
                BlockPos pos = new BlockPos(worldX, top - 1, worldZ);
                BlockState state = level.getBlockState(pos);
                MapColor color = state.getMapColor(level, pos);
                ids[localZ * TILE + localX] = (byte) color.id;
            }
        }
        return ids;
    }

    private record Key(int level, ResourceKey<Level> dimension, int chunkX, int chunkZ) {}

    private static final class Cached {
        private final byte[] ids;
        private final long stamp;

        private Cached(byte[] ids, long stamp) {
            this.ids = ids;
            this.stamp = stamp;
        }

        private byte[] ids() {
            return ids;
        }

        private long stamp() {
            return stamp;
        }
    }
}
