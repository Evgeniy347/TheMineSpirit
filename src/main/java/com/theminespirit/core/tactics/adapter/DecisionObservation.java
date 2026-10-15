package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.tactics.TacticalOutcome;
import java.util.Optional;

public record DecisionObservation(long tick, String spiritId, String spiritName, TacticalOutcome outcome,
        Optional<String> target, DecisionOrder order, DecisionFacts facts) {
    public DecisionSnapshot snapshot() {
        return new DecisionSnapshot(outcome.decision(), outcome.mode(), target, order);
    }
}
