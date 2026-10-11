package com.theminespirit.core.tactics;

import java.util.Optional;

public record TacticalOutcome(TacticalOption decision, TacticalAction action, RetreatMethod retreatMethod,
        boolean callHelp, TacticalState state, CombatMode mode, Optional<Point> returnTo, Trace trace,
        TacticsMemory memory) {
}
