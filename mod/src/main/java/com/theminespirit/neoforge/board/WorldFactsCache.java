package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.GraphState.Built;
import com.theminespirit.core.board.graph.GraphWorldFacts;
import com.theminespirit.core.town.TownPlan;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

final class WorldFactsCache {
    static final long TTL_TICKS = 200;

    private static @Nullable Entry cached;

    private WorldFactsCache() {}

    static GraphWorldFacts facts(
            ServerLevel level,
            BoardService service,
            FirstStepsRunner.StageScan scan,
            Optional<TownPlan> town,
            Map<String, Built> buildings) {
        return get(
                level,
                scan.stage(),
                buildings,
                () -> WorldAreaInputs.of(
                        level,
                        service,
                        scan,
                        town,
                        BuildingAuditInputs.buildings(level, service, scan, buildings, town)));
    }

    static GraphWorldFacts get(
            ServerLevel level, Object site, Map<String, Built> buildings, Supplier<GraphWorldFacts> compute) {
        long now = level.getGameTime();
        Key key = new Key(level, site, Map.copyOf(buildings));
        Entry entry = cached;
        if (entry != null && fresh(entry.key().equals(key), now - entry.tick())) {
            return entry.facts();
        }
        GraphWorldFacts facts = compute.get();
        cached = new Entry(key, now, facts);
        return facts;
    }

    static boolean fresh(boolean sameKey, long age) {
        return sameKey && age >= 0 && age < TTL_TICKS;
    }

    private record Key(ServerLevel level, Object site, Map<String, Built> buildings) {
        Key {
            Objects.requireNonNull(level, "Мир кэша фактов не может быть null");
            Objects.requireNonNull(site, "Место этапа кэша фактов не может быть null");
        }
    }

    private record Entry(Key key, long tick, GraphWorldFacts facts) {}
}
