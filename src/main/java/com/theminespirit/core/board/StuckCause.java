package com.theminespirit.core.board;

import java.util.List;
import org.jspecify.annotations.Nullable;

public final class StuckCause {
    private static final List<String> MARKERS =
            List.of("не двигается", "задание стоит", "нет прогресса", "застрял", "перенос не найден", "путь закрыт");
    private static final List<String> NO_PATH_MARKERS =
            List.of("маршрут не найден", "нет пути", "путь не найден", "недостижим", "тупик", "пути нет");

    private StuckCause() {}

    public static boolean movement(@Nullable String reason) {
        return matches(reason, MARKERS);
    }

    public static boolean noPath(@Nullable String reason) {
        return matches(reason, NO_PATH_MARKERS);
    }

    private static boolean matches(@Nullable String reason, List<String> markers) {
        if (reason == null) {
            return false;
        }
        for (String marker : markers) {
            if (reason.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
