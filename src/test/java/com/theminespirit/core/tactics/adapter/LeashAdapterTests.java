package com.theminespirit.core.tactics.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Orders;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.core.tactics.TacticalOutcome;
import com.theminespirit.core.tactics.TacticsConfig;
import com.theminespirit.core.tactics.TacticsEngine;
import com.theminespirit.core.tactics.TacticsMemory;
import com.theminespirit.core.tactics.TimeOfDay;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class LeashAdapterTests {
    private static final TacticsEngine ENGINE = new TacticsEngine(TacticsConfig.loadDefault());
    private static final Point POST = new Point(0, 0, 0);
    private static final double ZONE_RADIUS = 2.0;
    private static final double PURSUIT = 6.0;

    record Row(String name, double distance, Optional<OrdersBuilder.Zone> zone, boolean leashIgnores) {}

    static Stream<Row> rows() {
        Optional<OrdersBuilder.Zone> zone = Optional.of(new OrdersBuilder.Zone(POST, ZONE_RADIUS));
        return Stream.of(
                new Row("цель у самого поста", 1, zone, false),
                new Row("цель внутри поводка", 4, zone, false),
                new Row("цель за блок до границы", 5, zone, false),
                new Row("цель чуть внутри границы", PURSUIT - 0.01, zone, false),
                new Row("цель ровно на границе", PURSUIT, zone, false),
                new Row("цель чуть за границей", PURSUIT + 0.01, zone, true),
                new Row("цель за границей", 7, zone, true),
                new Row("цель далеко за границей", 20, zone, true),
                new Row("зоны нет: охрана становится защитой на месте", 7, Optional.empty(), false));
    }

    private static TacticalOutcome decide(double distance, Optional<OrdersBuilder.Zone> zone) {
        EnemyFact enemy = new EnemyFact("minecraft:zombie", "z", new Point(distance, 0, 0), 1, 0, true, true);
        Orders orders = OrdersBuilder.build(CombatMode.GUARD_AREA, zone, Optional.empty(), false);
        SituationContext context = new SituationContext(
                AdapterFixtures.self(), List.of(enemy), AdapterFixtures.env(TimeOfDay.DAY), orders, 100, 100);
        return ENGINE.step(
                CoreFactCollector.withDefaultSettings().collect(context),
                TacticsMemory.initial(CombatMode.GUARD_AREA, 0, 1),
                1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void step_TargetAtDistanceFromPost_LeashRuleFiresOnlyBeyondBorder(Row row) {
        TacticalOutcome outcome = decide(row.distance(), row.zone());

        assertEquals(
                row.leashIgnores(),
                "outside_leash_ignore".equals(outcome.trace().rule()),
                "Жёсткое правило поводка («" + row.name() + "», расстояние " + row.distance() + " при поводке "
                        + PURSUIT + "): оно срабатывает строго за границей, на границе решает счёт; сработало "
                        + outcome.trace().rule());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void pick_TargetBeyondLeash_NoTargetDesignated(Row row) {
        TacticalOutcome outcome = decide(row.distance(), row.zone());
        SituationContext context = new SituationContext(
                AdapterFixtures.self(),
                List.of(new EnemyFact("minecraft:zombie", "z", new Point(row.distance(), 0, 0), 1, 0, true, true)),
                AdapterFixtures.env(TimeOfDay.DAY),
                OrdersBuilder.build(CombatMode.GUARD_AREA, row.zone(), Optional.empty(), false),
                100,
                100);

        Optional<EnemyFact> target = TargetDesignation.pick(outcome, context);

        if (row.leashIgnores()) {
            assertTrue(
                    target.isEmpty(),
                    "Цель за поводком («" + row.name() + "») не выставляется: охранник не гонится, "
                            + "а возвращается к посту; выставлена " + target);
        }
        assertTrue(
                outcome.decision() != TacticalOption.FIGHT || target.isPresent(),
                "Решение FIGHT («" + row.name() + "») всегда несёт цель: иначе дух стоит среди врагов");
    }
}
