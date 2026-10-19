package com.theminespirit.core.building;

import java.util.Objects;

/**
 * Спуск якоря демо-стройки до твёрдого полного блока. Игрок смотрит на листву, траву или цветы — якорь
 * ставится не на них, а на землю под ними: иначе постройка повисает в кроне или на кустике.
 *
 * <p>Чистая функция: «твёрдость» слоя задаёт вызывающий (уровень знает блоки, ядро — нет).
 */
public final class GroundAnchor {
    @FunctionalInterface
    public interface SolidColumn {
        boolean solid(int y);
    }

    private GroundAnchor() {}

    public static int groundY(int fromY, int minY, SolidColumn column) {
        Objects.requireNonNull(column, "Столбец блоков не может быть null: без него неизвестно, где земля");
        for (int y = fromY; y >= minY; y--) {
            if (column.solid(y)) {
                return y;
            }
        }
        throw new IllegalArgumentException("Под точкой от y=" + fromY + " до низа мира " + minY
                + " нет твёрдого полного блока: якорь демо-стройки поставить не на что");
    }
}
