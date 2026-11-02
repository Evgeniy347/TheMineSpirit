package com.theminespirit.core.route;

import java.util.Objects;
import java.util.function.LongSupplier;

public final class RouteDeadline implements RouteBudget {
    private static final long NANOS_PER_MILLI = 1_000_000L;

    private final LongSupplier clock;
    private final long deadline;

    private RouteDeadline(LongSupplier clock, long deadline) {
        this.clock = clock;
        this.deadline = deadline;
    }

    public static RouteDeadline afterMillis(long millis) {
        return afterMillis(millis, System::nanoTime);
    }

    static RouteDeadline afterMillis(long millis, LongSupplier clock) {
        Objects.requireNonNull(clock, "Часы маршрута не могут быть null: без них срок поиска не проверить");
        if (millis < 0) {
            throw new IllegalArgumentException(
                    "Срок поиска " + millis + " мс отрицателен: отрицательный срок запрещает любой поиск");
        }
        long now = clock.getAsLong();
        long budget = millis > Long.MAX_VALUE / NANOS_PER_MILLI ? Long.MAX_VALUE : millis * NANOS_PER_MILLI;
        long deadline = now > Long.MAX_VALUE - budget ? Long.MAX_VALUE : now + budget;
        return new RouteDeadline(clock, deadline);
    }

    @Override
    public boolean exhausted() {
        return Long.compare(clock.getAsLong(), deadline) >= 0;
    }
}
