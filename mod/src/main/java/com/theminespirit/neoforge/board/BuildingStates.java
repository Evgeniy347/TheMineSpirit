package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

final class BuildingStates extends AbstractMap<String, Built> {
    private static final Map<ResourceKey<Level>, Snapshot> LAST = new HashMap<>();

    private final Map<String, Built> states;
    private final long revision;

    private BuildingStates(Map<String, Built> states, long revision) {
        this.states = states;
        this.revision = revision;
    }

    static BuildingStates of(ServerLevel level, Map<String, Built> states) {
        Objects.requireNonNull(level, "Мир состояний построек не может быть null: ревизия считается отдельно для мира");
        Map<String, Built> copied = Map.copyOf(
                Objects.requireNonNull(states, "Состояния построек не могут быть null: неизвестные задают ABSENT"));
        ResourceKey<Level> dimension = level.dimension();
        Snapshot before = LAST.get(dimension);
        long revision =
                revision(before == null ? Map.of() : before.states(), copied, before == null ? -1 : before.revision());
        LAST.put(dimension, new Snapshot(copied, revision));
        return new BuildingStates(copied, revision);
    }

    static long revision(Map<String, Built> before, Map<String, Built> after, long previous) {
        return before.equals(after) ? previous : previous + 1;
    }

    long revision() {
        return revision;
    }

    @Override
    public Set<Entry<String, Built>> entrySet() {
        return states.entrySet();
    }

    private record Snapshot(Map<String, Built> states, long revision) {}
}
