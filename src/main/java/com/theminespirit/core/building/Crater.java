package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CatalogLimits;
import java.util.Objects;
import java.util.UUID;

/**
 * Запомненная воронка от взрыва: центр, радиус и уровень окружающей земли (снимок высот краёв).
 *
 * <p>По уровню края {@link CraterPlan} поднимает столбцы воронки до ровной поверхности.
 */
public record Crater(UUID id, BlockCoord center, int radius, int groundLevel, long createdTick) {
    public static final int RADIUS_MIN = 1;
    public static final int RADIUS_MAX = 16;

    public Crater {
        Objects.requireNonNull(id, "Id воронки не может быть null: по нему скрипт находит воронку");
        Objects.requireNonNull(center, "Центр воронки не может быть null: без него неизвестно, где засыпать");
        if (radius < RADIUS_MIN || radius > RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус воронки " + radius + " вне диапазона " + RADIUS_MIN + ".." + RADIUS_MAX
                    + ": воронка крипера не бывает такой");
        }
        if (groundLevel < CatalogLimits.WORLD_Y_MIN || groundLevel > CatalogLimits.WORLD_Y_MAX) {
            throw new IllegalArgumentException("Уровень земли " + groundLevel + " вне мира " + CatalogLimits.WORLD_Y_MIN
                    + ".." + CatalogLimits.WORLD_Y_MAX + ": проверьте снимок высот краёв");
        }
    }

    public boolean covers(BlockCoord pos) {
        Objects.requireNonNull(pos, "Позиция не может быть null: проверяется, накрывает ли воронка клетку");
        long dx = (long) pos.x() - center.x();
        long dz = (long) pos.z() - center.z();
        return dx * dx + dz * dz <= (long) radius * radius;
    }
}