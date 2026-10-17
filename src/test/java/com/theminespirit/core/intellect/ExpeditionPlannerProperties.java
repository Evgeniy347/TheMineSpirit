package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.coordinator.SpiritRole;
import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.night.NightLeash;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.Point;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class ExpeditionPlannerProperties {
    private static final ExpeditionPlanner PLANNER = new ExpeditionPlanner();
    private static final Point MINE = new Point(100, 40, 100);

    @Provide
    Arbitrary<List<SpiritSnapshot>> candidates() {
        return Combinators.combine(Arbitraries.create(UUID::randomUUID),
                Arbitraries.of(SpiritRole.values()), Arbitraries.integers().between(-50, 50),
                Arbitraries.integers().between(-50, 50))
                .as((id, role, x, z) -> new SpiritSnapshot(id, new Point(x, 64, z), 1, role, CombatMode.DEFENSIVE,
                        0, false, Optional.empty(), 0, 1.0))
                .list().ofMinSize(0).ofMaxSize(8).map(list -> list.stream()
                        .collect(Collectors.toMap(SpiritSnapshot::id, item -> item, (first, second) -> first,
                                LinkedHashMap::new))
                        .values().stream().toList());
    }

    @Provide
    Arbitrary<SettlementView> needyViews() {
        return SettlementViews.views().filter(view -> view.needsOre(64, 32, 2)
                && !NightLeash.withDefaultRules().isEvening(view.dayFraction()));
    }

    @Property
    void plan_NeedySettlement_TeamInRangeAndSuitable(@ForAll("needyViews") SettlementView view,
            @ForAll("candidates") List<SpiritSnapshot> candidates) {
        Optional<Expedition> planned = PLANNER.plan(view, candidates, MINE);

        long suitable = candidates.stream().filter(c -> c.role() == SpiritRole.WORKER).count();
        if (suitable < 2) {
            assertEquals(Optional.empty(), planned,
                    "Подходящих меньше двух — команды нет: в одиночку в шахту не ходят");
            return;
        }
        Expedition expedition = planned.orElseThrow();
        assertTrue(expedition.team().size() >= 2 && expedition.team().size() <= 3,
                "Команда в 2..3 духа: иначе либо опасно, либо толпа");
        Set<UUID> workers = new HashSet<>();
        for (SpiritSnapshot candidate : candidates) {
            if (candidate.role() == SpiritRole.WORKER) {
                workers.add(candidate.id());
            }
        }
        assertTrue(workers.containsAll(expedition.team()),
                "Все члены команды подходят: иначе в шахту пойдёт защитник вместо работника");
    }

    @Property
    void plan_SameInput_Deterministic(@ForAll("needyViews") SettlementView view,
            @ForAll("candidates") List<SpiritSnapshot> candidates) {
        assertEquals(PLANNER.plan(view, candidates, MINE), PLANNER.plan(view, candidates, MINE),
                "Тот же вход даёт ту же команду: иначе состав экспедиции зависит от случая");
    }
}
