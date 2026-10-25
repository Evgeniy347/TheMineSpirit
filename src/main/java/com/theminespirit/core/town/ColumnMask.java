package com.theminespirit.core.town;

import java.util.ArrayList;
import java.util.Base64;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

public final class ColumnMask {
    private final int minX;
    private final int minZ;
    private final int width;
    private final int depth;
    private final BitSet bits;

    public ColumnMask(int minX, int minZ, int width, int depth, BitSet bits) {
        if (width < 1 || depth < 1) {
            throw new IllegalArgumentException("Маска колонок " + width + "×" + depth
                    + ": у участка хотя бы одна колонка, иначе плану не на чем стоять");
        }
        this.minX = minX;
        this.minZ = minZ;
        this.width = width;
        this.depth = depth;
        this.bits = (BitSet)
                Objects.requireNonNull(bits, "Биты маски не могут быть null").clone();
    }

    public static ColumnMask rect(int x1, int z1, int x2, int z2) {
        int width = x2 - x1 + 1;
        int depth = z2 - z1 + 1;
        BitSet bits = new BitSet(width * depth);
        bits.set(0, width * depth);
        return new ColumnMask(x1, z1, width, depth, bits);
    }

    public int minX() {
        return minX;
    }

    public int minZ() {
        return minZ;
    }

    public int width() {
        return width;
    }

    public int depth() {
        return depth;
    }

    public boolean contains(int x, int z) {
        int dx = x - minX;
        int dz = z - minZ;
        return dx >= 0 && dz >= 0 && dx < width && dz < depth && bits.get(dz * width + dx);
    }

    public int count() {
        return bits.cardinality();
    }

    public boolean isEmpty() {
        return bits.isEmpty();
    }

    public List<Cell> cells() {
        List<Cell> cells = new ArrayList<>(bits.cardinality());
        for (int index = bits.nextSetBit(0); index >= 0; index = bits.nextSetBit(index + 1)) {
            cells.add(new Cell(minX + index % width, minZ + index / width));
        }
        return List.copyOf(cells);
    }

    public ColumnMask union(ColumnMask other) {
        return combine(other, true);
    }

    public ColumnMask minus(ColumnMask other) {
        Objects.requireNonNull(other, "Вычитаемая маска не может быть null");
        BitSet out = (BitSet) bits.clone();
        for (int index = bits.nextSetBit(0); index >= 0; index = bits.nextSetBit(index + 1)) {
            if (other.contains(minX + index % width, minZ + index / width)) {
                out.clear(index);
            }
        }
        return new ColumnMask(minX, minZ, width, depth, out);
    }

    public String encode() {
        return minX + "," + minZ + "," + width + "," + depth + ":"
                + Base64.getEncoder().encodeToString(bits.toByteArray());
    }

    public static ColumnMask decode(String text) {
        Objects.requireNonNull(text, "Текст маски не может быть null: план без контура не читается");
        int colon = text.indexOf(':');
        String[] head = colon < 0 ? new String[0] : text.substring(0, colon).split(",", -1);
        if (head.length != 4) {
            throw new IllegalArgumentException("Маска '" + text
                    + "' не в виде «x,z,ширина,глубина:биты»: сохранённый план повреждён — план строится заново");
        }
        BitSet bits = BitSet.valueOf(Base64.getDecoder().decode(text.substring(colon + 1)));
        return new ColumnMask(
                Integer.parseInt(head[0]),
                Integer.parseInt(head[1]),
                Integer.parseInt(head[2]),
                Integer.parseInt(head[3]),
                bits);
    }

    private ColumnMask combine(ColumnMask other, boolean union) {
        Objects.requireNonNull(other, "Вторая маска не может быть null");
        int x1 = union ? Math.min(minX, other.minX) : Math.max(minX, other.minX);
        int z1 = union ? Math.min(minZ, other.minZ) : Math.max(minZ, other.minZ);
        int x2 = union
                ? Math.max(minX + width, other.minX + other.width)
                : Math.min(minX + width, other.minX + other.width);
        int z2 = union
                ? Math.max(minZ + depth, other.minZ + other.depth)
                : Math.min(minZ + depth, other.minZ + other.depth);
        int w = Math.max(1, x2 - x1);
        int d = Math.max(1, z2 - z1);
        BitSet out = new BitSet(w * d);
        for (int z = z1; z < z1 + d; z++) {
            for (int x = x1; x < x1 + w; x++) {
                boolean here = union ? contains(x, z) || other.contains(x, z) : contains(x, z) && other.contains(x, z);
                if (here) {
                    out.set((z - z1) * w + (x - x1));
                }
            }
        }
        return new ColumnMask(x1, z1, w, d, out);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ColumnMask mask && cells().equals(mask.cells());
    }

    @Override
    public int hashCode() {
        return cells().hashCode();
    }

    @Override
    public String toString() {
        return "ColumnMask[" + minX + "," + minZ + " " + width + "×" + depth + ", колонок " + count() + "]";
    }

    public record Cell(int x, int z) {}
}
