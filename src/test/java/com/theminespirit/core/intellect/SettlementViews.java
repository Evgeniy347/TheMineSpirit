package com.theminespirit.core.intellect;

import com.theminespirit.core.coordinator.SpiritRole;
import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;

public final class SettlementViews {
    private SettlementViews() {
    }

    public static SpiritSnapshot spirit(UUID id) {
        return spiritWithRole(id, SpiritRole.WORKER);
    }

    public static SpiritSnapshot spiritWithRole(UUID id, SpiritRole role) {
        return new SpiritSnapshot(id, new Point(0, 64, 0), 1.0, role, CombatMode.DEFENSIVE, 0, false,
                Optional.empty(), 0);
    }

    public static SettlementView empty() {
        return new SettlementView(0, 0, 0, 0, 0, 0, 0, 0, 0.5, List.of(), 0);
    }

    public static SettlementView settlement(double food, double nutrition, int ore, int iron, int weapons,
            int threats, double dayFraction) {
        return new SettlementView(3, 0, food, nutrition, ore, iron, weapons, threats, dayFraction, List.of(), 100);
    }

    public static SettlementView withSpirits(List<SpiritSnapshot> spirits) {
        List<SpiritSnapshot> ordered = new ArrayList<>(spirits);
        ordered.sort(Comparator.comparing(SpiritSnapshot::id));
        return new SettlementView(ordered.size(), 0, 2, 2, 64, 32, 2, 0, 0.5, ordered, 100);
    }

    public static Arbitrary<SettlementView> views() {
        Arbitrary<SpiritSnapshot> spirit = Combinators.combine(Arbitraries.create(UUID::randomUUID),
                Arbitraries.integers().between(0, 20).map(step -> step / 20.0),
                Arbitraries.of(SpiritRole.values()), Arbitraries.integers().between(0, 3),
                Arbitraries.longs().between(0, 10000))
                .as((id, hp, role, threats, tick) -> new SpiritSnapshot(id, new Point(0, 64, 0), hp, role,
                        CombatMode.DEFENSIVE, threats, false, Optional.empty(), tick));
        Arbitrary<List<SpiritSnapshot>> unique = spirit.list().ofMaxSize(6).map(list -> list.stream()
                .collect(Collectors.toMap(SpiritSnapshot::id, item -> item, (first, second) -> first,
                        LinkedHashMap::new))
                .values().stream().sorted(Comparator.comparing(SpiritSnapshot::id)).toList());
        Arbitrary<int[]> counts = Combinators.combine(Arbitraries.integers().between(0, 12),
                Arbitraries.integers().between(0, 128), Arbitraries.integers().between(0, 64),
                Arbitraries.integers().between(0, 8), Arbitraries.integers().between(0, 5))
                .as((extra, ore, iron, weapons, threats) -> new int[] {extra, ore, iron, weapons, threats});
        Arbitrary<double[]> stocks = Combinators.combine(Arbitraries.doubles().between(0, 5),
                Arbitraries.doubles().between(0, 5), Arbitraries.doubles().between(0, 1))
                .as((food, nutrition, day) -> new double[] {food, nutrition, day});
        return Combinators.combine(counts, stocks, unique, Arbitraries.longs().between(0, 100000),
                Arbitraries.integers().between(0, 6))
                .as((counted, stocked, spirits, tick, kids) -> {
                    int population = spirits.size() + counted[0];
                    return new SettlementView(population, Math.min(kids, population), stocked[0], stocked[1],
                            counted[1], counted[2], counted[3], counted[4], stocked[2], spirits, tick);
                });
    }
}
