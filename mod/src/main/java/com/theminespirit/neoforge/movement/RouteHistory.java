package com.theminespirit.neoforge.movement;

import com.theminespirit.core.route.RouteStep;
import com.theminespirit.core.route.RouteTrail;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RouteHistory {
    static final int KEEP = 5;
    private static final Map<UUID, RouteTrail> TRAILS = new HashMap<>();

    private RouteHistory() {}

    public static void record(SpiritEntity spirit, RouteStep step) {
        TRAILS.computeIfAbsent(spirit.getUUID(), id -> new RouteTrail(KEEP)).add(step);
    }

    public static String drain(SpiritEntity spirit) {
        RouteTrail trail = TRAILS.remove(spirit.getUUID());
        return trail == null ? "нет" : trail.last().toString();
    }
}
