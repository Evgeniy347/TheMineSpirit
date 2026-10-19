package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.Situation;
import java.util.List;

public record CoreFactCollector(TacticsAdapterSettings settings) implements FactCollector {
    public CoreFactCollector {
        if (settings == null) {
            throw new IllegalArgumentException(
                    "CoreFactCollector.settings = null: без настроек неизвестен факт-радиус; "
                            + "передайте TacticsAdapterSettings.loadDefault()");
        }
    }

    public static CoreFactCollector withDefaultSettings() {
        return new CoreFactCollector(TacticsAdapterSettings.loadDefault());
    }

    @Override
    public Situation collect(SituationContext context) {
        if (context == null) {
            throw new IllegalArgumentException(
                    "CoreFactCollector.collect: context = null; проверьте вызов сборщика фактов");
        }
        Point self = context.self().pos();
        double radius = settings.factRadius();
        List<EnemyFact> inRange = context.enemies().stream()
                .filter(enemy -> enemy.pos().distanceTo(self) <= radius)
                .toList();
        return new Situation(
                context.self(),
                inRange,
                context.env(),
                context.orders(),
                context.ticksSinceHit(),
                context.ticksSinceStrike());
    }
}
