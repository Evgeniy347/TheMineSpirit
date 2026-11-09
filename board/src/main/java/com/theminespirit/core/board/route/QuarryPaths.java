package com.theminespirit.core.board.route;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public final class QuarryPaths {
    private QuarryPaths() {}

    public static int length(BlockCoord from, BlockCoord to) {
        Objects.requireNonNull(from, "Начало пути не может быть null: путь считается от поселения");
        Objects.requireNonNull(to, "Конец пути не может быть null: путь ведёт к месту добычи");
        return Math.abs(to.x() - from.x()) + Math.abs(to.z() - from.z());
    }
}
