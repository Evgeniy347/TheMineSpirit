package com.theminespirit.core.board.route;

import java.util.Objects;
import java.util.Optional;

public final class QuarryAlerts {
    public static final long INTERVAL_TICKS = 6000;

    private QuarryAlerts() {}

    public static boolean due(long now, long last) {
        return last <= 0 || now < last || now - last >= INTERVAL_TICKS;
    }

    public static Optional<String> message(QuarryStatus status) {
        Objects.requireNonNull(status, "Статус не может быть null: без него нечего сообщать");
        return switch (status.state()) {
            case LOW ->
                Optional.of("Мало " + name(status.resource()) + " в месте добычи: остаток " + status.remainingPercent()
                        + "% — стратег ищет новое место");
            case EMPTY ->
                Optional.of("Запас " + name(status.resource()) + " в месте добычи иссяк — стратег ищет новое место");
            case ACTIVE, UNSET -> Optional.empty();
        };
    }

    private static String name(RouteResource resource) {
        return switch (resource) {
            case DIRT -> "земли";
            case SAND -> "песка";
            case SNOW -> "снега";
        };
    }
}
