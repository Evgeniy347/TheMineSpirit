package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

/** Клетка поверхности, важная для спавна мобов: уровень блочного света на ней и можно ли там поставить факел. */
public record LightCell(BlockCoord coord, int light, boolean placeable) {
    public LightCell {
        Objects.requireNonNull(coord, "Клетка света без координаты: неизвестно, где она");
        if (light < 0 || light > 15) {
            throw new IllegalArgumentException("Блочный свет " + light + " вне 0..15: такого уровня в игре нет");
        }
    }
}
