package com.theminespirit.core.tactics.adapter;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.ModeGeometry;
import com.theminespirit.core.tactics.ModeParams;
import com.theminespirit.core.tactics.Orders;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.Situation;
import com.theminespirit.core.tactics.TacticalOutcome;
import com.theminespirit.core.tactics.TacticsConfig;
import com.theminespirit.core.tactics.TacticsEngine;
import com.theminespirit.core.tactics.TacticsMemory;
import com.theminespirit.core.tactics.TimeOfDay;
import java.util.List;
import java.util.Optional;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;

class LeashAdapterProperties {
    private static final TacticsConfig CONFIG = TacticsConfig.loadDefault();
    private static final TacticsEngine ENGINE = new TacticsEngine(CONFIG);
    private static final Point POST = new Point(0, 0, 0);
    private static final double ZONE_RADIUS = 2.0;

    private static SituationContext context(double distance) {
        EnemyFact enemy = new EnemyFact("minecraft:zombie", "z", new Point(distance, 0, 0), 1, 0, true, true);
        Orders orders = OrdersBuilder.build(CombatMode.GUARD_AREA, Optional.of(new OrdersBuilder.Zone(POST, ZONE_RADIUS)),
                Optional.empty(), false);
        return new SituationContext(AdapterFixtures.self(), List.of(enemy), AdapterFixtures.env(TimeOfDay.DAY), orders,
                100, 100);
    }

    @Property(tries = 200)
    void leashLeft_AnyTargetDistance_NeverExceedsPursuitRadius(@ForAll @DoubleRange(min = 0, max = 32) double distance) {
        Situation situation = CoreFactCollector.withDefaultSettings().collect(context(distance));
        ModeParams params = CONFIG.modes().of(CombatMode.GUARD_AREA);

        ModeGeometry geometry = ModeGeometry.of(situation, params, Optional.empty());

        assertTrue(geometry.leashLeft() <= params.pursuit(ZONE_RADIUS),
                "Запас поводка не больше радиуса преследования " + params.pursuit(ZONE_RADIUS) + ": цель на расстоянии "
                        + distance + " дала " + geometry.leashLeft() + "; запас больше радиуса значил бы, что цель дальше от поста, чем сам пост");
    }

    @Property(tries = 200)
    void pick_TargetBeyondLeash_NeverDesignated(@ForAll @DoubleRange(min = 6.01, max = 32) double distance) {
        SituationContext context = context(distance);
        TacticalOutcome outcome = ENGINE.step(CoreFactCollector.withDefaultSettings().collect(context),
                TacticsMemory.initial(CombatMode.GUARD_AREA, 0, 1), 1);

        Optional<EnemyFact> target = TargetDesignation.pick(outcome, context);

        assertTrue(target.isEmpty(), "Цель за поводком (" + distance + " > 6) не выставляется: решение "
                + outcome.decision() + ", правило " + outcome.trace().rule());
    }
}
