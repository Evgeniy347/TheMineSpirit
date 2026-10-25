package com.theminespirit.core.town;

import java.util.BitSet;
import java.util.Objects;

public record TownStreets(ColumnMask main, ColumnMask side, ColumnMask ring) {
    public TownStreets {
        Objects.requireNonNull(main, "Главные улицы не могут быть null");
        Objects.requireNonNull(side, "Боковые улицы не могут быть null");
        Objects.requireNonNull(ring, "Кольцевая улица не может быть null");
    }

    public boolean contains(int x, int z) {
        return main.contains(x, z) || side.contains(x, z) || ring.contains(x, z);
    }

    public static TownStreets lay(
            TerrainGrid grid, ColumnMask region, int centerX, int centerZ, TownRules.Streets rules) {
        Objects.requireNonNull(grid, "Рельеф не может быть null: улицы обходят воду");
        Objects.requireNonNull(region, "Контур не может быть null: улицы идут внутри него");
        Objects.requireNonNull(rules, "Правила улиц не могут быть null: ширины — данные");
        GridBits bits = new GridBits(region.width(), region.depth());
        BitSet inside = new BitSet(bits.size());
        for (ColumnMask.Cell cell : region.cells()) {
            inside.set(bits.at(cell.x() - region.minX(), cell.z() - region.minZ()));
        }
        int[] inset = bits.insetDistance(inside);
        BitSet main = new BitSet(bits.size());
        BitSet side = new BitSet(bits.size());
        BitSet ring = new BitSet(bits.size());
        int mainHalf = rules.main() / 2;
        int sideHalf = rules.side() / 2;
        for (int index = inside.nextSetBit(0); index >= 0; index = inside.nextSetBit(index + 1)) {
            int x = region.minX() + index % region.width();
            int z = region.minZ() + index / region.width();
            if (grid.water(x, z)) {
                continue;
            }
            int depth = inset[index];
            boolean inner = depth > rules.ringInset() + rules.ring();
            if (Math.abs(x - centerX) <= mainHalf || Math.abs(z - centerZ) <= mainHalf) {
                main.set(index);
            } else if (depth > rules.ringInset() && depth <= rules.ringInset() + rules.ring()) {
                ring.set(index);
            } else if (inner && (onLine(x - centerX, rules, sideHalf) || onLine(z - centerZ, rules, sideHalf))) {
                side.set(index);
            }
        }
        return new TownStreets(mask(region, main), mask(region, side), mask(region, ring));
    }

    private static boolean onLine(int offset, TownRules.Streets rules, int half) {
        int distance = Math.abs(offset);
        int nearest = Math.round((float) distance / rules.blockSize()) * rules.blockSize();
        return nearest > 0 && Math.abs(distance - nearest) <= half;
    }

    private static ColumnMask mask(ColumnMask region, BitSet bits) {
        return new ColumnMask(region.minX(), region.minZ(), region.width(), region.depth(), bits);
    }
}
