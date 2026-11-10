package com.theminespirit.core.policy;

import static com.theminespirit.core.Lookup.present;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

public final class PlacedBlockSet {
    public static final int MAX_PER_CHUNK = 4096;
    public static final int MAX_HORIZONTAL = 33_554_431;
    public static final int MIN_HORIZONTAL = -33_554_432;
    public static final int MAX_VERTICAL = 2047;
    public static final int MIN_VERTICAL = -2048;

    private static final int X_BITS = 26;
    private static final int Y_BITS = 12;
    private static final long X_MASK = (1L << X_BITS) - 1;
    private static final long Y_MASK = (1L << Y_BITS) - 1;
    private static final int Z_SHIFT = Y_BITS;
    private static final int X_SHIFT = Y_BITS + X_BITS;

    private final Map<Long, Chunk> chunks = new TreeMap<>();
    private int size;

    static final class Chunk {
        final Set<Long> positions = new LinkedHashSet<>();
        boolean overflowed;
    }

    public static long pack(BlockCoord coord) {
        Objects.requireNonNull(coord, "Координата не может быть null: её сжимают в long");
        if (coord.x() < MIN_HORIZONTAL
                || coord.x() > MAX_HORIZONTAL
                || coord.z() < MIN_HORIZONTAL
                || coord.z() > MAX_HORIZONTAL
                || coord.y() < MIN_VERTICAL
                || coord.y() > MAX_VERTICAL) {
            throw new IllegalArgumentException("Координата " + coord + " вне диапазона сжатия: x и z от "
                    + MIN_HORIZONTAL + " до " + MAX_HORIZONTAL + ", y от " + MIN_VERTICAL + " до " + MAX_VERTICAL);
        }
        return ((coord.x() & X_MASK) << X_SHIFT) | ((coord.z() & X_MASK) << Z_SHIFT) | (coord.y() & Y_MASK);
    }

    public static BlockCoord unpack(long packed) {
        int x = (int) (packed << (64 - X_SHIFT - X_BITS) >> (64 - X_BITS));
        int z = (int) (packed << (64 - Z_SHIFT - X_BITS) >> (64 - X_BITS));
        int y = (int) (packed << (64 - Y_BITS) >> (64 - Y_BITS));
        return new BlockCoord(x, y, z);
    }

    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public static int chunkXOf(long chunkKey) {
        return (int) (chunkKey >> 32);
    }

    public static int chunkZOf(long chunkKey) {
        return (int) chunkKey;
    }

    private static long chunkKeyOf(BlockCoord coord) {
        return chunkKey(coord.x() >> 4, coord.z() >> 4);
    }

    public boolean add(BlockCoord coord) {
        long packed = pack(coord);
        Chunk chunk = chunks.computeIfAbsent(chunkKeyOf(coord), key -> new Chunk());
        if (!chunk.positions.add(packed)) {
            return false;
        }
        size++;
        if (chunk.positions.size() > MAX_PER_CHUNK) {
            Iterator<Long> oldest = chunk.positions.iterator();
            oldest.next();
            oldest.remove();
            chunk.overflowed = true;
            size--;
        }
        return true;
    }

    public boolean remove(BlockCoord coord) {
        long packed = pack(coord);
        long key = chunkKeyOf(coord);
        Chunk chunk = chunks.get(key);
        if (chunk == null || !chunk.positions.remove(packed)) {
            return false;
        }
        size--;
        if (chunk.positions.isEmpty()) {
            chunks.remove(key);
        }
        return true;
    }

    public boolean contains(BlockCoord coord) {
        Chunk chunk = chunks.get(chunkKeyOf(coord));
        return chunk != null && chunk.positions.contains(pack(coord));
    }

    public int size() {
        return size;
    }

    public List<BlockCoord> inChunk(int chunkX, int chunkZ) {
        Chunk chunk = chunks.get(chunkKey(chunkX, chunkZ));
        if (chunk == null) {
            return List.of();
        }
        return chunk.positions.stream().map(PlacedBlockSet::unpack).toList();
    }

    public List<BlockCoord> cells() {
        List<BlockCoord> cells = new ArrayList<>(size);
        for (Chunk chunk : chunks.values()) {
            for (long packed : chunk.positions) {
                cells.add(unpack(packed));
            }
        }
        return List.copyOf(cells);
    }

    Set<Map.Entry<Long, Chunk>> chunkEntries() {
        return chunks.entrySet();
    }

    void restore(long chunkKey, boolean overflowed, long[] packed) {
        Chunk chunk = new Chunk();
        for (long position : packed) {
            chunk.positions.add(position);
        }
        chunk.overflowed = overflowed;
        chunks.put(chunkKey, chunk);
        size += chunk.positions.size();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof PlacedBlockSet that)
                || size != that.size
                || !chunks.keySet().equals(that.chunks.keySet())) {
            return false;
        }
        for (Map.Entry<Long, Chunk> entry : chunks.entrySet()) {
            Chunk mine = entry.getValue();
            Chunk theirs = present(that.chunks, entry.getKey(), "Чанки набора");
            if (mine.overflowed != theirs.overflowed
                    || !mine.positions.stream()
                            .toList()
                            .equals(theirs.positions.stream().toList())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(size, chunks.keySet());
    }
}
