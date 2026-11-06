package com.theminespirit.core.ui;

import java.util.Arrays;
import java.util.List;

public final class TerrainRaster {
    public static final TerrainRaster EMPTY = new TerrainRaster(0, 0, 0, 0, new int[0]);
    private static final int TILE = TerrainTile.SIZE;
    private final int x1;
    private final int z1;
    private final int width;
    private final int height;
    private final int[] pixels;

    private TerrainRaster(int x1, int z1, int width, int height, int[] pixels) {
        if (width < 0 || height < 0 || pixels.length != width * height) {
            throw new IllegalArgumentException("Растр карты " + width + "×" + height + " с " + pixels.length
                    + " пикселями: пикселей ровно ширина × высота");
        }
        this.x1 = x1;
        this.z1 = z1;
        this.width = width;
        this.height = height;
        this.pixels = pixels.clone();
    }

    public static TerrainRaster of(List<TerrainTile> tiles) {
        int minChunkX = Integer.MAX_VALUE;
        int minChunkZ = Integer.MAX_VALUE;
        int maxChunkX = Integer.MIN_VALUE;
        int maxChunkZ = Integer.MIN_VALUE;
        for (TerrainTile tile : tiles) {
            if (!tile.hasKnown()) {
                continue;
            }
            minChunkX = Math.min(minChunkX, tile.chunkX());
            minChunkZ = Math.min(minChunkZ, tile.chunkZ());
            maxChunkX = Math.max(maxChunkX, tile.chunkX());
            maxChunkZ = Math.max(maxChunkZ, tile.chunkZ());
        }
        if (minChunkX > maxChunkX) {
            return EMPTY;
        }
        int x1 = minChunkX * TILE;
        int z1 = minChunkZ * TILE;
        int width = (maxChunkX - minChunkX + 1) * TILE;
        int height = (maxChunkZ - minChunkZ + 1) * TILE;
        int[] pixels = new int[width * height];
        for (TerrainTile tile : tiles) {
            int baseX = tile.chunkX() * TILE - x1;
            int baseZ = tile.chunkZ() * TILE - z1;
            for (int localZ = 0; localZ < TILE; localZ++) {
                for (int localX = 0; localX < TILE; localX++) {
                    int id = tile.id(localX, localZ);
                    if (id != TerrainTile.UNKNOWN) {
                        pixels[(baseZ + localZ) * width + baseX + localX] = TerrainColor.argb(id);
                    }
                }
            }
        }
        return new TerrainRaster(x1, z1, width, height, pixels);
    }

    public int x1() {
        return x1;
    }

    public int z1() {
        return z1;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int[] pixels() {
        return pixels.clone();
    }

    public boolean isEmpty() {
        return width == 0 || height == 0;
    }

    public int argb(int worldX, int worldZ) {
        int localX = worldX - x1;
        int localZ = worldZ - z1;
        return localX < 0 || localZ < 0 || localX >= width || localZ >= height ? 0 : pixels[localZ * width + localX];
    }

    public TownMapView.Bounds bounds() {
        return isEmpty()
                ? new TownMapView.Bounds(0, 0, 0, 0)
                : new TownMapView.Bounds(x1, z1, x1 + width - 1, z1 + height - 1);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TerrainRaster raster
                && x1 == raster.x1
                && z1 == raster.z1
                && width == raster.width
                && height == raster.height
                && Arrays.equals(pixels, raster.pixels);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(pixels);
    }

    @Override
    public String toString() {
        return "TerrainRaster[" + x1 + "," + z1 + " " + width + "×" + height + "]";
    }
}
