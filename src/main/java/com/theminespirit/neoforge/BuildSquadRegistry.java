package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildKey;
import com.theminespirit.core.building.BuildSquad;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class BuildSquadRegistry {
    private static final Map<Level, Map<BuildKey, BuildSquad>> BY_LEVEL = new WeakHashMap<>();

    private BuildSquadRegistry() {
    }

    public static synchronized BuildSquad join(Level level, BuildKey key, UUID spirit) {
        require(level, key, spirit);
        Map<BuildKey, BuildSquad> squads = BY_LEVEL.computeIfAbsent(level, ignored -> new HashMap<>());
        BuildSquad squad = squads.computeIfAbsent(key, ignored -> new BuildSquad());
        prune(level, squad);
        squad.join(spirit);
        return squad;
    }

    public static synchronized void leave(Level level, BuildKey key, UUID spirit) {
        require(level, key, spirit);
        Map<BuildKey, BuildSquad> squads = BY_LEVEL.get(level);
        if (squads == null) {
            return;
        }
        BuildSquad squad = squads.get(key);
        if (squad == null) {
            return;
        }
        squad.leave(spirit);
        if (squad.isEmpty()) {
            squads.remove(key);
        }
        if (squads.isEmpty()) {
            BY_LEVEL.remove(level);
        }
    }

    public static synchronized int size() {
        int total = 0;
        for (Map<BuildKey, BuildSquad> squads : BY_LEVEL.values()) {
            total += squads.size();
        }
        return total;
    }

    private static void prune(Level level, BuildSquad squad) {
        for (UUID id : squad.members()) {
            if (!alive(level, id)) {
                squad.leave(id);
            }
        }
    }

    private static boolean alive(Level level, UUID id) {
        return level instanceof ServerLevel server
                && server.getEntity(id) instanceof SpiritEntity spirit
                && !spirit.isRemoved();
    }

    private static void require(Level level, BuildKey key, UUID spirit) {
        Objects.requireNonNull(level, "Уровень не может быть null: отряд стройки живёт на уровне");
        Objects.requireNonNull(key, "Ключ стройки не может быть null: без него отряды разных схем путаются");
        Objects.requireNonNull(spirit, "Дух не может быть null: отряд держит конкретных строителей");
    }
}
