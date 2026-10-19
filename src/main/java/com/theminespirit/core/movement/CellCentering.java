package com.theminespirit.core.movement;

/**
 * Стоит ли дух в центре выбранной клетки. Клетка столба прижата к стволу, а тело
 * духа — 0.6 шириной: у края клетки голова задевает бревно ствола на прыжке.
 * Запас до границы клетки (0.5 - 0.3 = 0.2) переживает сдвиг не больше 0.15.
 */
public final class CellCentering {
    public static final double TOLERANCE = 0.15;

    private CellCentering() {}
}
