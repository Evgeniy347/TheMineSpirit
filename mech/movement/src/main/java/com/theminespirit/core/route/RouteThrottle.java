package com.theminespirit.core.route;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class RouteThrottle {
    public static final long MIN_TICKS = 200;

    private final long windowTicks;
    private final Map<UUID, Long> last = new HashMap<>();

    public RouteThrottle() {
        this(MIN_TICKS);
    }

    public RouteThrottle(long windowTicks) {
        if (windowTicks < 1) {
            throw new IllegalArgumentException(
                    "Окно ограничения " + windowTicks + " тиков не положительно: повтор нечем ограничивать");
        }
        this.windowTicks = windowTicks;
    }

    public boolean allow(UUID spirit, long now) {
        Objects.requireNonNull(spirit, "Дух не может быть null: ограничение считается на конкретного духа");
        Long previous = last.get(spirit);
        if (previous != null && now >= previous && now - previous < windowTicks) {
            return false;
        }
        last.put(spirit, now);
        return true;
    }
}
