package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class StillWatch {
    public static final int AREA_RADIUS = 24;

    private record Area(BlockCoord min, BlockCoord max, long since) {}

    private final Map<UUID, Area> marks = new HashMap<>();

    public boolean still(UUID spirit, BlockCoord feet, long now, long windowTicks) {
        Objects.requireNonNull(spirit, "Дух не может быть null: неподвижность считается на конкретного духа");
        Objects.requireNonNull(feet, "Клетка духа не может быть null: без неё не видно, стоит ли он");
        if (windowTicks <= 0) {
            throw new IllegalArgumentException(
                    "Окно неподвижности " + windowTicks + " тиков не бывает неположительным");
        }
        Area last = marks.get(spirit);
        if (last == null || now < last.since()) {
            marks.put(spirit, new Area(feet, feet, now));
            return false;
        }
        BlockCoord min = new BlockCoord(
                Math.min(last.min().x(), feet.x()),
                Math.min(last.min().y(), feet.y()),
                Math.min(last.min().z(), feet.z()));
        BlockCoord max = new BlockCoord(
                Math.max(last.max().x(), feet.x()),
                Math.max(last.max().y(), feet.y()),
                Math.max(last.max().z(), feet.z()));
        if (span(min, max) > AREA_RADIUS) {
            marks.put(spirit, new Area(feet, feet, now));
            return false;
        }
        marks.put(spirit, new Area(min, max, last.since()));
        return now - last.since() >= windowTicks;
    }

    private static int span(BlockCoord min, BlockCoord max) {
        return Math.max(max.x() - min.x(), Math.max(max.y() - min.y(), max.z() - min.z()));
    }
}
