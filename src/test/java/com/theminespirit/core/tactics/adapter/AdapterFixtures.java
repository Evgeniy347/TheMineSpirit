package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.EnvFacts;
import com.theminespirit.core.tactics.Orders;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.SelfFacts;
import com.theminespirit.core.tactics.Situation;
import com.theminespirit.core.tactics.TimeOfDay;
import java.util.ArrayList;
import java.util.List;

final class AdapterFixtures {
    static final double FAR = EnvFacts.FAR_DISTANCE;
    static final double RADIUS = TacticsAdapterSettings.defaults().factRadius();

    private AdapterFixtures() {
    }

    static SelfFacts self() {
        return new SelfFacts(new Point(0, 0, 0), 20, 20, 0, 6, 0.3, 1, 0, 0, false, 0, 0);
    }

    static EnvFacts env(TimeOfDay time) {
        return new EnvFacts(FAR, 0, 15, time, FAR, false, FAR, FAR);
    }

    static Orders orders(CombatMode mode) {
        return Orders.mode(mode);
    }

    static EnemyFact enemy(String type, String id, double x, double z) {
        return new EnemyFact(type, id, new Point(x, 0, z), 1, 0, true, true);
    }

    static SituationContext context(List<EnemyFact> enemies) {
        return new SituationContext(self(), enemies, env(TimeOfDay.DAY), orders(CombatMode.AGGRESSIVE), 0, 0);
    }

    static Situation collect(List<EnemyFact> enemies) {
        return CoreFactCollector.withDefaultSettings().collect(context(enemies));
    }

    static List<EnemyFact> many(String type, int count, double from) {
        List<EnemyFact> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(enemy(type, type + i, from + i, 0));
        }
        return result;
    }
}
