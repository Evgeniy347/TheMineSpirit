package com.theminespirit.neoforge.movement;

import com.theminespirit.core.route.RouteStep;
import com.theminespirit.core.route.RouteTrail;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.Entity;

public final class RouteHistory {
    static final int KEEP = 5;
    private static final Map<UUID, RouteTrail> TRAILS = new HashMap<>();

    private RouteHistory() {}

    public static void record(Entity spirit, RouteStep step) {
        TRAILS.computeIfAbsent(spirit.getUUID(), id -> new RouteTrail(KEEP)).add(step);
    }

    public static String drain(Entity spirit) {
        RouteTrail trail = TRAILS.remove(spirit.getUUID());
        return trail == null ? "нет" : trail.last().toString();
    }
}
