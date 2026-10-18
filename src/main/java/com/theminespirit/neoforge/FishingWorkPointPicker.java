package com.theminespirit.neoforge;

import com.theminespirit.core.fishing.FishingData;
import com.theminespirit.core.fishing.FishingSpots;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;

/**
 * Выбор рабочей точки у воды в мире: строит вид воды вокруг духа, читает колонки и отдаёт
 * решение ядру {@link FishingSpots#findWorkPoint}. Мир читает только этот адаптер, выбор
 * места — чистая функция; точки чужих построек приходят списком и лишь сужают выбор.
 */
public final class FishingWorkPointPicker {
    private FishingWorkPointPicker() {
    }

    public static Optional<FishingSpots.Spot> pick(ServerLevel level, BlockCoord origin, FishingData data,
            int radius, List<BlockCoord> avoid, double avoidRadius) {
        Objects.requireNonNull(level, "Мир не может быть null: рабочая точка выбирается по блокам воды");
        Objects.requireNonNull(data, "Данные рыбалки не могут быть null: в них размер рабочей точки");
        FishingWaterView view = new FishingWaterView(level, origin, radius);
        while (!view.done()) {
            view.advance();
        }
        return FishingSpots.findWorkPoint(origin, view, data,
                (stand, water) -> allowed(stand, avoid, avoidRadius));
    }

    private static boolean allowed(BlockCoord stand, List<BlockCoord> avoid, double avoidRadius) {
        if (avoid == null || avoid.isEmpty()) {
            return true;
        }
        double limit = avoidRadius * avoidRadius;
        for (BlockCoord point : avoid) {
            long dx = (long) stand.x() - point.x();
            long dy = (long) stand.y() - point.y();
            long dz = (long) stand.z() - point.z();
            if (dx * dx + dy * dy + dz * dz < limit) {
                return false;
            }
        }
        return true;
    }
}
