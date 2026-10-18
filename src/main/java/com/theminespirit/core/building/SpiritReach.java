package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import java.util.Objects;

/**
 * Досягаемость работы с блоком от ног духа (решение владельца 18.10, «чит» досягаемости): по горизонтали не дальше
 * {@code horizontal} клеток, вверх — {@code up}, вниз — {@code down}; прямая видимость не нужна. Числа — горячая
 * таблица {@code building/build.json}; общее правило для стройки, станций, контейнеров, добычи и фермы.
 */
public record SpiritReach(int horizontal, int up, int down) {
    public SpiritReach {
        if (horizontal < 1 || up < 0 || down < 0) {
            throw new IllegalArgumentException("Досягаемость " + horizontal + "/" + up + "/" + down
                    + ": по горизонтали хотя бы 1, вверх и вниз — не меньше 0");
        }
    }

    public static SpiritReach current() {
        return DataTables.INSTANCE.building().current().reach();
    }

    public static boolean forbiddenStand(BlockCoord stand, BlockCoord cell) {
        Objects.requireNonNull(stand, "Стоянка не может быть null: без неё неизвестно, где дух встанет");
        Objects.requireNonNull(cell, "Клетка не может быть null: без неё неизвестно, что ставить");
        return stand.equals(cell) || stand.equals(new BlockCoord(cell.x(), cell.y() - 1, cell.z()));
    }

    public static boolean reachable(BlockCoord feet, BlockCoord target) {
        return current().reaches(feet, target);
    }

    public boolean reaches(BlockCoord feet, BlockCoord target) {
        Objects.requireNonNull(feet, "Ноги духа не могут быть null: без них неизвестна точка отсчёта");
        Objects.requireNonNull(target, "Цель не может быть null: без неё неизвестно, до чего дотягиваться");
        int dx = Math.abs(target.x() - feet.x());
        int dz = Math.abs(target.z() - feet.z());
        int dy = target.y() - feet.y();
        return dx <= horizontal && dz <= horizontal && dy <= up && dy >= -down;
    }

    public String describe() {
        return horizontal + " по горизонтали, " + up + " вверх и " + down + " вниз";
    }
}
