package com.theminespirit.core.board.audit;

import com.theminespirit.core.board.ConstructionBatches.ChunkCell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Arrays;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class ChunkCache {
    public static final int COLUMNS = 16 * 16;
    public static final int MAX_LAYERS = 64;
    public static final int NO_Y = Integer.MIN_VALUE;

    private final ChunkCell chunk;
    private final BlockDict dict;
    private final int minY;
    private final int layers;
    private final short[] cells;
    private final short[] surface;
    private long filledTick = -1;
    private long lastVisit = -1;
    private int unknown;

    public ChunkCache(ChunkCell chunk, BlockDict dict, int minY, int layers) {
        this.chunk = Objects.requireNonNull(chunk, "Чанк кэша не может быть null: иначе клетки лягут не туда");
        this.dict = Objects.requireNonNull(dict, "Словарь не может быть null: в нём коды блоков");
        if (layers <= 0 || layers > MAX_LAYERS) {
            throw new IllegalArgumentException("Окно кэша " + layers + " слоёв от y=" + minY + ": допустимо 1…"
                    + MAX_LAYERS + ", иначе память чанка растёт до небес вместо envelope плана");
        }
        this.minY = minY;
        this.layers = layers;
        this.cells = new short[COLUMNS * layers];
        this.surface = new short[COLUMNS];
        this.unknown = this.cells.length;
    }

    public boolean holds(BlockCoord cell) {
        return (cell.x() >> 4) == chunk.x()
                && (cell.z() >> 4) == chunk.z()
                && cell.y() >= minY
                && cell.y() < minY + layers;
    }

    public void put(BlockCoord cell, String id) {
        set(cell, dict.intern(Objects.requireNonNull(id, "Блок клетки не может быть null: пустота — air")));
    }

    public void markUnknown(BlockCoord cell) {
        set(cell, BlockDict.UNKNOWN);
    }

    public @Nullable String block(BlockCoord cell) {
        short code = cells[index(cell)];
        return dict.known(code) ? dict.at(code) : null;
    }

    public boolean known(BlockCoord cell) {
        return dict.known(cells[index(cell)]);
    }

    public void putSurface(int lx, int lz, int y) {
        if (lx < 0 || lx >= 16 || lz < 0 || lz >= 16) {
            throw new IllegalArgumentException(
                    "Столб " + lx + "," + lz + " вне чанка 16×16: индекс уйдёт в чужой столб");
        }
        surface[lz * 16 + lx] = (short) (y - minY + 1);
    }

    public int surfaceAt(int lx, int lz) {
        short stored = surface[lz * 16 + lx];
        return stored == 0 ? NO_Y : minY + stored - 1;
    }

    public int unknown() {
        return unknown;
    }

    public int filled() {
        return cells.length - unknown;
    }

    public boolean empty() {
        return unknown == cells.length;
    }

    public void clear() {
        Arrays.fill(cells, BlockDict.UNKNOWN);
        Arrays.fill(surface, (short) 0);
        unknown = cells.length;
        filledTick = -1;
    }

    public ChunkCell chunk() {
        return chunk;
    }

    public int minY() {
        return minY;
    }

    public int layers() {
        return layers;
    }

    public long filledTick() {
        return filledTick;
    }

    public void filled(long tick) {
        filledTick = tick;
    }

    public long lastVisit() {
        return lastVisit;
    }

    public void visited(long tick) {
        lastVisit = tick;
    }

    private void set(BlockCoord cell, short code) {
        int at = index(cell);
        short previous = cells[at];
        if (previous == code) {
            return;
        }
        cells[at] = code;
        unknown += code == BlockDict.UNKNOWN ? 1 : previous == BlockDict.UNKNOWN ? -1 : 0;
    }

    private int index(BlockCoord cell) {
        if (!holds(cell)) {
            throw new IllegalArgumentException("Клетка " + cell + " вне кэша чанка " + chunk + " (y=" + minY + "…"
                    + (minY + layers) + "): запись мимо окна исказила бы соседнюю клетку");
        }
        return (cell.y() - minY) * COLUMNS + ((cell.z() & 15) * 16) + (cell.x() & 15);
    }
}
