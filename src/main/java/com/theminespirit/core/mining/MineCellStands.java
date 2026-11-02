package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public final class MineCellStands {
    private static final int NEAR = 4;

    private MineCellStands() {}

    public static List<BlockCoord> of(BlockCoord cell, BlockCoord feet, SafetyView view, Predicate<BlockCoord> usable) {
        return of(cell, feet, view, usable, false);
    }

    public static List<BlockCoord> of(
            BlockCoord cell, BlockCoord feet, SafetyView view, Predicate<BlockCoord> usable, boolean digIn) {
        Objects.requireNonNull(cell, "Клетка не может быть null: без неё неизвестно, к чему подходить");
        Objects.requireNonNull(feet, "Точка духа не может быть null: по ней ищутся стоянки");
        Objects.requireNonNull(view, "Вид мира не может быть null: по нему проверяется стоянка");
        Objects.requireNonNull(usable, "Отбор стоянок не может быть null: пустое правило задаётся истиной");
        List<BlockCoord> found = new ArrayList<>();
        for (BlockCoord stand : StandSpots.of(cell, view, feet)) {
            if (occupies(stand, cell) || !usable.test(stand)) {
                continue;
            }
            found.add(stand);
        }
        if (found.isEmpty() && digIn) {
            for (BlockCoord stand : MineReach.stands(feet, view)) {
                if (!occupies(stand, cell) && distanceSquared(stand, cell) <= NEAR * NEAR && usable.test(stand)) {
                    found.add(stand);
                }
            }
        }
        return List.copyOf(found);
    }

    private static long distanceSquared(BlockCoord from, BlockCoord to) {
        long dx = (long) to.x() - from.x();
        long dy = (long) to.y() - from.y();
        long dz = (long) to.z() - from.z();
        return dx * dx + dy * dy + dz * dz;
    }

    public static boolean occupies(BlockCoord feet, BlockCoord cell) {
        Objects.requireNonNull(feet, "Ноги духа не могут быть null: без них не видно, где стоит дух");
        Objects.requireNonNull(cell, "Клетка не может быть null: без неё неизвестно, куда ставить");
        boolean sameColumn = feet.x() == cell.x() && feet.z() == cell.z();
        return sameColumn && (cell.y() == feet.y() || cell.y() == feet.y() + 1);
    }
}
