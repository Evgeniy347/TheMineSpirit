package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.TaskStall;
import org.jspecify.annotations.Nullable;

final class StallText {
    private StallText() {}

    static String reason(
            boolean standing,
            @Nullable String script,
            String key,
            String at,
            boolean tactics,
            @Nullable String suspend) {
        String what = standing
                ? "задание стоит " + TaskStall.STALL_TICKS + " тиков: "
                        + (script == null ? "скрипт не стартовал" : "подход не двигается")
                : "нет прогресса " + TaskStall.NO_PROGRESS_TICKS + " тиков (" + script + ", " + key + ")";
        return what
                + " в " + at
                + (tactics ? ", тактика держит духа" : "")
                + (suspend == null ? "" : ", пауза: " + suspend);
    }
}
