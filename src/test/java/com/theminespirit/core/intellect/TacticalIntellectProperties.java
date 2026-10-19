package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.coordinator.WorkReservations;
import com.theminespirit.core.intellect.activity.ChopActivity;
import com.theminespirit.core.intellect.activity.DefendActivity;
import com.theminespirit.core.intellect.activity.FarmActivity;
import com.theminespirit.core.intellect.activity.MineActivity;
import com.theminespirit.core.intellect.activity.RestActivity;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.LongRange;

class TacticalIntellectProperties {
    private static TacticalIntellect intellect() {
        return new TacticalIntellect(
                ActivityCatalog.adultsOnly(List.of(
                        new ChopActivity(),
                        new FarmActivity(),
                        new MineActivity(),
                        new RestActivity(),
                        new DefendActivity())),
                StrengthAssessorTests.PROFILES,
                StrengthAssessorTests.ARMOR);
    }

    @Provide
    Arbitrary<SettlementView> views() {
        return SettlementViews.views();
    }

    private static StrategicQueue queueWith(List<StrategicOrder> orders) {
        StrategicQueue queue = new StrategicQueue();
        for (StrategicOrder order : orders) {
            queue.enqueue(order);
        }
        return queue;
    }

    @Provide
    Arbitrary<List<EnemyFact>> enemies() {
        return Arbitraries.of("minecraft:zombie", "minecraft:skeleton")
                .flatMap(type -> Arbitraries.create(UUID::randomUUID)
                        .map(id -> new EnemyFact(type, id.toString(), new Point(5, 64, 0), 1, 0, true, true)))
                .list()
                .ofMaxSize(3);
    }

    @Property
    void decide_AnyInput_SpiritInOneListOnly(
            @ForAll("views") SettlementView view,
            @ForAll("enemies") List<EnemyFact> sighted,
            @ForAll @LongRange(min = 0, max = 100000) long now) {
        TacticalIntellect mind = intellect();
        mind.trackEnemies(sighted);
        TacticalDecision decision =
                mind.decide(view, new ActivityLedger(), new WorkReservations(), new StrategicQueue(), now);

        Set<UUID> seen = new HashSet<>();
        List<UUID> all = new ArrayList<>();
        decision.orders().stream().map(StrategicOrder::spirit).forEach(all::add);
        decision.strengths().stream().map(StrengthVerdict::spirit).forEach(all::add);
        decision.activities().assignments().stream()
                .map(ActivityAssignment::spirit)
                .forEach(all::add);
        for (UUID spirit : all) {
            assertTrue(
                    seen.add(spirit),
                    "Дух не более чем в одном списке решения: иначе у него и приказ, и бой, и работа сразу");
        }
    }

    @Property
    void decide_SameInput_Deterministic(
            @ForAll("views") SettlementView view,
            @ForAll("enemies") List<EnemyFact> sighted,
            @ForAll @LongRange(min = 0, max = 100000) long now) {
        List<StrategicOrder> orders = new ArrayList<>();
        int index = 0;
        for (SpiritSnapshot spirit : view.spirits()) {
            if (index % 2 == 0) {
                orders.add(new StrategicOrder(spirit.id(), "s-" + index, new ScriptCall("wait", Map.of()), now, 6000));
            }
            index++;
        }
        TacticalIntellect first = intellect();
        first.trackEnemies(sighted);
        TacticalIntellect second = intellect();
        second.trackEnemies(sighted);

        TacticalDecision one = first.decide(view, new ActivityLedger(), new WorkReservations(), queueWith(orders), now);
        TacticalDecision two =
                second.decide(view, new ActivityLedger(), new WorkReservations(), queueWith(orders), now);

        assertEquals(one, two, "Тот же вход даёт то же решение: иначе приказы зависят от случая");
    }

    @Property
    void decide_RepeatedTick_Unchanged(
            @ForAll("views") SettlementView view, @ForAll @LongRange(min = 0, max = 90000) long now) {
        TacticalIntellect mind = intellect();
        ActivityLedger ledger = new ActivityLedger();
        TacticalDecision first = mind.decide(view, ledger, new WorkReservations(), new StrategicQueue(), now);
        TacticalDecision second = mind.decide(view, ledger, new WorkReservations(), new StrategicQueue(), now + 1);

        assertEquals(
                first.activities().assignments(),
                second.activities().assignments(),
                "При неизменном входе результат неизменен: иначе задачи дёргаются между пересборками");
        assertEquals(
                first.strengths(),
                second.strengths(),
                "Оценка сил стабильна между тиками: иначе боевые приказы мигают");
    }
}
