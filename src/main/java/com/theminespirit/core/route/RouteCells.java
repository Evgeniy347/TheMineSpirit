package com.theminespirit.core.route;

final class RouteCells {
    private static final int EXPECTED = 1 << 14;
    private final RouteWorld world;
    private final LongIntTable cache = new LongIntTable(EXPECTED);
    private int reads;

    RouteCells(RouteWorld world) {
        this.world = world;
    }

    int at(int x, int y, int z) {
        long key = LongIntTable.key(x, y, z);
        int cell = cache.get(key);
        if (cell != LongIntTable.ABSENT) {
            return cell;
        }
        reads++;
        cell = world.cell(x, y, z);
        if (cell < 0) {
            throw new IllegalStateException(
                    "Клетка (" + x + ", " + y + ", " + z + ") закодирована отрицательным числом " + cell
                            + ": мир маршрута обязан отдавать RouteCell, иначе цены ходов бессмысленны");
        }
        cache.put(key, cell);
        return cell;
    }

    int reads() {
        return reads;
    }
}
