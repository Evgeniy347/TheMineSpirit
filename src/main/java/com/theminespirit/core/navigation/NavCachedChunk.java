// lib: Из Baritone (github.com/cabaletta/baritone, src/main/java/baritone/cache/CachedChunk.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import java.util.Arrays;
import java.util.Objects;

public final class NavCachedChunk {
    public static final int CHUNK_SIZE = 16;
    private static final int CELLS = CHUNK_SIZE * CHUNK_SIZE;
    private static final int BITS = 2;
    private static final int BITS_PER_WORD = 64;

    private final int chunkX;
    private final int chunkZ;
    private final int minY;
    private final int height;
    private final long[] data;

    public NavCachedChunk(int chunkX, int chunkZ, int minY, int height, long[] data) {
        if (height < 1) {
            throw new IllegalArgumentException("Высота чанка " + height + " меньше одного блока");
        }
        int expected = words(height);
        if (data.length != expected) {
            throw new IllegalArgumentException("Данные чанка " + data.length + " слов, а нужна " + expected);
        }
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.minY = minY;
        this.height = height;
        this.data = data.clone();
    }

    public static NavCachedChunk pack(int chunkX, int chunkZ, int minY, int height, NavChunkSource source) {
        Objects.requireNonNull(source, "Источник блоков не может быть null: чанк не из чего упаковать");
        long[] data = new long[words(height)];
        int baseX = chunkX * CHUNK_SIZE;
        int baseZ = chunkZ * CHUNK_SIZE;
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                for (int x = 0; x < CHUNK_SIZE; x++) {
                    NavBlockType type = source.type(baseX + x, minY + y, baseZ + z);
                    put(data, index(x, y, z), type.bits());
                }
            }
        }
        return new NavCachedChunk(chunkX, chunkZ, minY, height, data);
    }

    public NavBlockType type(int x, int y, int z) {
        int localY = y - minY;
        if (x < 0 || x >= CHUNK_SIZE || z < 0 || z >= CHUNK_SIZE || localY < 0 || localY >= height) {
            return NavBlockType.AIR;
        }
        return NavBlockType.fromBits(get(data, index(x, localY, z)));
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    public int minY() {
        return minY;
    }

    public int height() {
        return height;
    }

    public int sizeInBytes() {
        return data.length * Long.BYTES;
    }

    static int words(int height) {
        long bits = (long) CELLS * height * BITS;
        return (int) ((bits + BITS_PER_WORD - 1) / BITS_PER_WORD);
    }

    private static int index(int x, int y, int z) {
        return y * CELLS + z * CHUNK_SIZE + x;
    }

    private static int get(long[] data, int index) {
        int bit = index * BITS;
        int word = bit / BITS_PER_WORD;
        int offset = bit % BITS_PER_WORD;
        return (int) ((data[word] >>> offset) & 0b11);
    }

    private static void put(long[] data, int index, int value) {
        int bit = index * BITS;
        int word = bit / BITS_PER_WORD;
        int offset = bit % BITS_PER_WORD;
        data[word] = (data[word] & ~(0b11L << offset)) | ((long) value << offset);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof NavCachedChunk chunk)) {
            return false;
        }
        return chunkX == chunk.chunkX
                && chunkZ == chunk.chunkZ
                && minY == chunk.minY
                && height == chunk.height
                && Arrays.equals(data, chunk.data);
    }

    @Override
    public int hashCode() {
        int hash = Objects.hash(chunkX, chunkZ, minY, height);
        return 31 * hash + Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        return "NavCachedChunk{" + chunkX + "," + chunkZ + " y=" + minY + "+" + height + "}";
    }
}
