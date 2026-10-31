package com.theminespirit.core.route;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class RouteThrottle {
    public static final long MIN_TICKS = 200;

    private final Map<UUID, Long> last = new HashMap<>();

    public boolean allow(UUID spirit, long now) {
        Objects.requireNonNull(spirit, "Дух не может быть null: ограничение считается на конкретного духа");
        Long previous = last.get(spirit);
        if (previous != null && now >= previous && now - previous < MIN_TICKS) {
            return false;
        }
        last.put(spirit, now);
        return true;
    }
}
