package com.theminespirit.core.inventory;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;

public final class UnloadedSpots {
    static final int SHOWN = 5;

    private UnloadedSpots() {}

    public static String line(List<BlockCoord> spots) {
        Objects.requireNonNull(spots, "Список точек не может быть null: пустой набор задают пустым списком");
        if (spots.isEmpty()) {
            return "";
        }
        List<BlockCoord> first = spots.subList(0, Math.min(SHOWN, spots.size()));
        StringBuilder text = new StringBuilder("контейнеров вне загруженных чанков: ")
                .append(spots.size())
                .append(" (первые ")
                .append(first.size())
                .append(": ");
        for (int index = 0; index < first.size(); index++) {
            if (index > 0) {
                text.append(", ");
            }
            BlockCoord at = first.get(index);
            text.append(at.x()).append(',').append(at.y()).append(',').append(at.z());
        }
        return text.append(")").toString();
    }
}
