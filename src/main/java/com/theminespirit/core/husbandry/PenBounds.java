package com.theminespirit.core.husbandry;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CatalogLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record PenBounds(BlockCoord center, int radius) {
    public PenBounds {
        Objects.requireNonNull(center, "Центр загона не может быть null: без него неизвестно, где стадо");
        if (radius < CatalogLimits.HERD_PEN_RADIUS_MIN || radius > CatalogLimits.HERD_PEN_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус загона " + radius + " вне диапазона ["
                    + CatalogLimits.HERD_PEN_RADIUS_MIN + ", " + CatalogLimits.HERD_PEN_RADIUS_MAX
                    + "]: тесный загон давит стадо, широкий не удержать");
        }
    }

    public boolean inside(BlockCoord pos) {
        Objects.requireNonNull(pos, "Позиция не может быть null: неизвестно, что проверять");
        return distanceSquared(pos) <= (long) radius * radius;
    }

    public BlockCoord nearestInside(BlockCoord pos) {
        return nearestInside(pos, 0);
    }

    public BlockCoord lureTarget(BlockCoord pos, int preferredInset) {
        if (preferredInset < 0) {
            throw new IllegalArgumentException(
                    "Отступ возврата " + preferredInset + " отрицательный: он двигал бы цель наружу загона");
        }
        return nearestInside(pos, Math.min(preferredInset, radius - 1));
    }

    public BlockCoord nearestInside(BlockCoord pos, int margin) {
        Objects.requireNonNull(pos, "Позиция не может быть null: неизвестно, куда возвращать");
        if (margin < 0 || margin >= radius) {
            throw new IllegalArgumentException("Отступ возврата " + margin + " вне диапазона [0, " + radius
                    + "): отступ во весь радиус не оставляет места внутри загона");
        }
        if (inside(pos) && distanceSquared(pos) <= (long) (radius - margin) * (radius - margin)) {
            return pos;
        }
        int target = radius - margin;
        long dx = (long) pos.x() - center.x();
        long dz = (long) pos.z() - center.z();
        if (dx == 0 && dz == 0) {
            return new BlockCoord(center.x() + target, pos.y(), center.z());
        }
        double scale = target / Math.sqrt((double) (dx * dx + dz * dz));
        BlockCoord inside = new BlockCoord(
                center.x() + (int) Math.round(dx * scale), pos.y(), center.z() + (int) Math.round(dz * scale));
        while (!shallowInside(inside, target)) {
            int stepX = Integer.compare(center.x(), inside.x());
            int stepZ = Integer.compare(center.z(), inside.z());
            inside = new BlockCoord(inside.x() + stepX, pos.y(), inside.z() + stepZ);
        }
        return inside;
    }

    public List<AnimalRef> escapedOf(List<AnimalRef> herd) {
        Objects.requireNonNull(herd, "Снимок стада не может быть null: пустое стадо задают пустым списком");
        List<AnimalRef> escaped = new ArrayList<>();
        for (AnimalRef animal : herd) {
            Objects.requireNonNull(animal, "Запись о животном не может быть null: снимок собирается только из живых");
            if (!inside(animal.pos())) {
                escaped.add(animal);
            }
        }
        return List.copyOf(escaped);
    }

    private boolean shallowInside(BlockCoord pos, int target) {
        long dx = (long) pos.x() - center.x();
        long dz = (long) pos.z() - center.z();
        return dx * dx + dz * dz <= (long) target * target;
    }

    private long distanceSquared(BlockCoord pos) {
        long dx = (long) pos.x() - center.x();
        long dz = (long) pos.z() - center.z();
        return dx * dx + dz * dz;
    }
}
