package com.theminespirit.core.tactics;

import java.util.Optional;

final class Memories {
    private Memories() {
    }

    static TacticsMemory fresh(Situation situation) {
        return TacticsMemory.initial(situation.orders().mode(), 0, situation.self().hpFraction());
    }

    static TacticsMemory holding(Situation situation, TacticalOption previous, long decidedAt) {
        TacticsMemory base = fresh(situation);
        return new TacticsMemory(Optional.of(previous), decidedAt, base.state(), base.stateSince(), base.mode(),
                base.orderedMode(), base.anchor(), base.lastSeenTick(), base.hpWindowRef(), base.hpWindowStart());
    }
}
