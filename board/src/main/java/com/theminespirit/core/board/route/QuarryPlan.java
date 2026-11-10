package com.theminespirit.core.board.route;

import com.theminespirit.core.town.PlotLeveling;
import java.util.Objects;

public record QuarryPlan(QuarryArea area, PlotLeveling leveling, int reserve, int columns, boolean waterLimited) {
    public QuarryPlan {
        Objects.requireNonNull(area, "Область плана не может быть null: без неё непонятно, где копают");
        Objects.requireNonNull(leveling, "Срезка плана не может быть null: пустой карьер — пустой план");
        if (reserve < 0 || columns < 0) {
            throw new IllegalArgumentException(
                    "Запас " + reserve + " и колонок " + columns + ": отрицательный запас невозможен");
        }
    }

    public boolean empty() {
        return reserve == 0;
    }

    public int cutVolume() {
        return leveling.cutVolume();
    }
}
