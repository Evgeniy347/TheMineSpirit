package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

/**
 * Досягаемость постройки от ног духа: по горизонтали не дальше {@link #HORIZONTAL} клеток,
 * вверх — {@link #UP}, вниз — {@link #DOWN}. Прямая видимость не требуется (решение владельца 18.10):
 * дух ставит блок в поле зрения «по карте», чтобы стройка не тонула в подходах.
 */
public final class SpiritReach {
    public static final int HORIZONTAL = 3;
    public static final int UP = 15;
    public static final int DOWN = 3;

    private SpiritReach() {
    }

    public static boolean forbiddenStand(BlockCoord stand, BlockCoord cell) {
        Objects.requireNonNull(stand, "Стоянка не может быть null: без неё неизвестно, где дух встанет");
        Objects.requireNonNull(cell, "Клетка не может быть null: без неё неизвестно, что ставить");
        return stand.equals(cell) || stand.equals(new BlockCoord(cell.x(), cell.y() - 1, cell.z()));
    }

    public static boolean reachable(BlockCoord feet, BlockCoord target) {
        Objects.requireNonNull(feet, "Ноги духа не могут быть null: без них неизвестна точка отсчёта");
        Objects.requireNonNull(target, "Цель не может быть null: без неё неизвестно, до чего дотягиваться");
        int dx = Math.abs(target.x() - feet.x());
        int dz = Math.abs(target.z() - feet.z());
        int dy = target.y() - feet.y();
        return dx <= HORIZONTAL && dz <= HORIZONTAL && dy <= UP && dy >= -DOWN;
    }
}
