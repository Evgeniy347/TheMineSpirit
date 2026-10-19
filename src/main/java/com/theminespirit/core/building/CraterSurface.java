package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;

/**
 * Поверхность взрыва для планировщика засыпки: уровень окружающей земли по столбцу и текущие блоки.
 *
 * <p>Адаптер строит её по снимку «высоты поверхности до взрыва» или по соседям края; ядро не знает
 * о мире и работает только с этими ответами.
 */
public interface CraterSurface {
    BlockCoord center();

    int radius();

    int groundLevel(int x, int z);

    boolean isAir(int x, int y, int z);

    boolean isProtected(int x, int y, int z);
}
