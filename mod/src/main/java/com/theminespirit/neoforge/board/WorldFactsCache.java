package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphState.Built;
import com.theminespirit.core.board.graph.GraphWorldFacts;
import com.theminespirit.core.town.SettlementScan;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownPlan;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

final class WorldFactsCache {
    static final long TTL_TICKS = 600;

    private static @Nullable Entry cached;
    private static @Nullable Sweep sweeping;

    private WorldFactsCache() {}

    static GraphWorldFacts facts(
            ServerLevel level,
            BoardService service,
            FirstStepsRunner.StageScan scan,
            Optional<TownPlan> town,
            Map<String, Built> buildings) {
        StageSite site = scan.stage();
        Optional<GraphWorldFacts> fresh =
                get(level, site, buildings, () -> compute(level, service, scan, town, buildings));
        if (fresh.isPresent()) {
            return fresh.orElseThrow();
        }
        Sweep sweep = sweeping;
        if (sweep != null && sweep.key().equals(new Key(level, site, Map.copyOf(buildings)))) {
            return sweep.facts();
        }
        return WorldAreaInputs.facts(
                level,
                service,
                scan,
                town,
                GraphFactInputs.buildingFacts(buildings, town, scan),
                SettlementScan.Strays.unmeasured());
    }

    private static @Nullable GraphWorldFacts compute(
            ServerLevel level,
            BoardService service,
            FirstStepsRunner.StageScan scan,
            Optional<TownPlan> town,
            Map<String, Built> buildings) {
        SettlementScan.Strays found = WorldAreaScan.scanOrNull(level, scan.stage(), town);
        if (found == null) {
            return null;
        }
        Key key = new Key(level, scan.stage(), Map.copyOf(buildings));
        long audit = System.nanoTime();
        Map<String, GraphFacts.Building> audited = BuildingAuditInputs.buildings(level, service, scan, buildings, town);
        GraphInputs.mark("world.audit", audit);
        long area = System.nanoTime();
        GraphWorldFacts facts = WorldAreaInputs.facts(level, service, scan, town, audited, found);
        GraphInputs.mark("world.area", area);
        sweeping = new Sweep(key, facts);
        return facts;
    }

    static Optional<GraphWorldFacts> get(
            ServerLevel level, Object site, Map<String, Built> buildings, Supplier<GraphWorldFacts> compute) {
        long now = level.getGameTime();
        Key key = new Key(level, site, Map.copyOf(buildings));
        Entry entry = cached;
        if (entry != null && fresh(entry.key().equals(key), now - entry.tick())) {
            return Optional.of(entry.facts());
        }
        GraphWorldFacts facts = compute.get();
        if (facts == null) {
            return Optional.empty();
        }
        cached = new Entry(key, now, facts);
        return Optional.of(facts);
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

    private record Sweep(Key key, GraphWorldFacts facts) {}
}
