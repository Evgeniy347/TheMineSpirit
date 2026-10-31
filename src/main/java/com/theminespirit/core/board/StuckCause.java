package com.theminespirit.core.board;

import java.util.List;
import org.jspecify.annotations.Nullable;

public final class StuckCause {
    private static final List<String> MARKERS =
            List.of("не двигается", "задание стоит", "нет прогресса", "застрял", "перенос не найден", "путь закрыт");

    private StuckCause() {}

    public static boolean movement(@Nullable String reason) {
        if (reason == null) {
            return false;
        }
        for (String marker : MARKERS) {
            if (reason.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
