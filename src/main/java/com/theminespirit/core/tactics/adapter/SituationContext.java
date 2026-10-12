package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.EnvFacts;
import com.theminespirit.core.tactics.Orders;
import com.theminespirit.core.tactics.SelfFacts;
import com.theminespirit.core.tactics.Situation;
import java.util.List;

public record SituationContext(SelfFacts self, List<EnemyFact> enemies, EnvFacts env, Orders orders, int ticksSinceHit,
        int ticksSinceStrike) {
    public SituationContext {
        required("SituationContext.self", self);
        required("SituationContext.enemies", enemies);
        required("SituationContext.env", env);
        required("SituationContext.orders", orders);
        ticks("SituationContext.ticksSinceHit", ticksSinceHit);
        ticks("SituationContext.ticksSinceStrike", ticksSinceStrike);
        enemies = List.copyOf(enemies);
    }

    private static void required(String field, Object value) {
        if (value == null) {
            throw new IllegalArgumentException(field + " = null: адаптер обязан собрать факты мира до вызова ядра; "
                    + "проверьте сборку SituationContext");
        }
    }

    private static void ticks(String field, int value) {
        if (value < 0 || value > Situation.MAX_TICKS) {
            throw new IllegalArgumentException(field + " = " + value + ": допустимо от 0 до " + Situation.MAX_TICKS
                    + " тиков; проверьте счётчик тиков адаптера");
        }
    }
}
