package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class StillWatch {
    private record Mark(BlockCoord cell, long since) {}

    private final Map<UUID, Mark> marks = new HashMap<>();

    public boolean still(UUID spirit, BlockCoord feet, long now, long windowTicks) {
        Objects.requireNonNull(spirit, "Дух не может быть null: неподвижность считается на конкретного духа");
        Objects.requireNonNull(feet, "Клетка духа не может быть null: без неё не видно, стоит ли он");
        if (windowTicks <= 0) {
            throw new IllegalArgumentException(
                    "Окно неподвижности " + windowTicks + " тиков не бывает неположительным");
        }
        Mark last = marks.get(spirit);
        if (last == null || !last.cell().equals(feet) || now < last.since()) {
            marks.put(spirit, new Mark(feet, now));
            return false;
        }
        return now - last.since() >= windowTicks;
    }
}
