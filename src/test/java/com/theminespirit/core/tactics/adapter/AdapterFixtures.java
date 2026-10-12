package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.EnvFacts;
import com.theminespirit.core.tactics.HoldStatus;
import com.theminespirit.core.tactics.Orders;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.RetreatMethod;
import com.theminespirit.core.tactics.SelfFacts;
import com.theminespirit.core.tactics.Situation;
import com.theminespirit.core.tactics.TacticalAction;
import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.core.tactics.TacticalOutcome;
import com.theminespirit.core.tactics.TacticalState;
import com.theminespirit.core.tactics.TacticsMemory;
import com.theminespirit.core.tactics.TimeOfDay;
import com.theminespirit.core.tactics.Trace;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        return enemy(type, id, x, z, true);
    }

    static EnemyFact enemy(String type, String id, double x, double z, boolean visible) {
        return new EnemyFact(type, id, new Point(x, 0, z), 1, 0, visible, true);
    }

    static TacticalOutcome outcome(TacticalOption option, TacticalAction action, RetreatMethod method) {
        return new TacticalOutcome(option, action, method, false, TacticalState.CALM, CombatMode.AGGRESSIVE,
                Optional.empty(), trace(), TacticsMemory.initial(CombatMode.AGGRESSIVE, 0, 1));
    }

    static Trace trace() {
        return new Trace("score", HoldStatus.NONE, List.of(), Map.of(), List.of(), "action", Optional.empty(),
                Optional.empty(), Optional.empty(), "");
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
