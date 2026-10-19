package com.theminespirit.core.tactics;

import java.util.Optional;

public record TacticsMemory(
        Optional<TacticalOption> previous,
        DecisionSource previousSource,
        long decidedAt,
        TacticalState state,
        long stateSince,
        CombatMode mode,
        CombatMode orderedMode,
        Optional<Point> anchor,
        long lastSeenTick,
        double hpWindowRef,
        long hpWindowStart) {
    public static final long NEVER_SEEN = -1_000_000L;

    public TacticsMemory {
        FactChecks.required("TacticsMemory.previous", previous);
        FactChecks.required("TacticsMemory.previousSource", previousSource);
        FactChecks.required("TacticsMemory.state", state);
        FactChecks.required("TacticsMemory.mode", mode);
        FactChecks.required("TacticsMemory.orderedMode", orderedMode);
        FactChecks.required("TacticsMemory.anchor", anchor);
        FactChecks.range("TacticsMemory.hpWindowRef", hpWindowRef, 0, 1);
    }

    public static TacticsMemory initial(CombatMode ordered, long now, double hpFraction) {
        return new TacticsMemory(
                Optional.empty(),
                DecisionSource.SCORE,
                now,
                TacticalState.CALM,
                now,
                ordered,
                ordered,
                Optional.empty(),
                NEVER_SEEN,
                hpFraction,
                now);
    }
}
