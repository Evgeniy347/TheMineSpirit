package com.theminespirit.core.tactics;

import java.util.Optional;

final class Memories {
    private Memories() {
    }

    static TacticsMemory fresh(Situation situation) {
        return TacticsMemory.initial(situation.orders().mode(), 0, situation.self().hpFraction());
    }

    static TacticsMemory decidedByRule(Situation situation, TacticalOption previous, long decidedAt) {
        TacticsMemory base = holding(situation, previous, decidedAt);
        return new TacticsMemory(base.previous(), DecisionSource.RULE, base.decidedAt(), base.state(), base.stateSince(),
                base.mode(), base.orderedMode(), base.anchor(), base.lastSeenTick(), base.hpWindowRef(),
                base.hpWindowStart());
    }

    static TacticsMemory holding(Situation situation, TacticalOption previous, long decidedAt) {
        TacticsMemory base = fresh(situation);
        return new TacticsMemory(Optional.of(previous), DecisionSource.SCORE, decidedAt, base.state(), base.stateSince(), base.mode(),
                base.orderedMode(), base.anchor(), base.lastSeenTick(), base.hpWindowRef(), base.hpWindowStart());
    }
}
