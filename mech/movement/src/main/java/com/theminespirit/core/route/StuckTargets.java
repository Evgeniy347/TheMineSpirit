package com.theminespirit.core.route;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class StuckTargets {
    private final Map<UUID, Map<BlockCoord, Long>> bySpirit = new HashMap<>();

    public void note(UUID spirit, BlockCoord target, long now) {
        bySpirit.computeIfAbsent(spirit, id -> new HashMap<>()).put(target, now);
    }

    public boolean recent(UUID spirit, BlockCoord target, long now, long ttl) {
        Map<BlockCoord, Long> targets = bySpirit.get(spirit);
        if (targets == null) {
            return false;
        }
        targets.entrySet().removeIf(entry -> now - entry.getValue() >= ttl);
        Long at = targets.get(target);
        return at != null && now - at < ttl;
    }
}
