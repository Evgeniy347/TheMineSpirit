package com.theminespirit.core.tactics;

import java.util.EnumSet;
import java.util.Set;

public final class Decider {
    private final HardRules hardRules;
    private final ScoreWeights weights;

    public Decider(HardRules hardRules, ScoreWeights weights) {
        this.hardRules = hardRules;
        this.weights = weights;
    }

    public Decision decide(FactMap facts, Scores scores, int escapeRoutes) {
        HardVerdict verdict = hardRules.evaluate(facts);
        if (verdict.decisive().isPresent()) {
            HardRule rule = verdict.decisive().get();
            return new Decision(rule.decision().get(), rule.id(), verdict.excluded(), verdict.excludingRules());
        }
        Set<TacticalOption> candidates = EnumSet.allOf(TacticalOption.class);
        candidates.removeAll(verdict.excluded());
        if (candidates.isEmpty()) {
            TacticalOption fallback = escapeRoutes > 0 ? TacticalOption.RETREAT : TacticalOption.FIGHT;
            return new Decision(fallback, Decision.ALL_EXCLUDED, verdict.excluded(), verdict.excludingRules());
        }
        if (candidates.size() == 1) {
            return new Decision(candidates.iterator().next(), Decision.SOLE_CANDIDATE, verdict.excluded(),
                    verdict.excludingRules());
        }
        return new Decision(scores.best(candidates, weights), Decision.BY_SCORE, verdict.excluded(),
                verdict.excludingRules());
    }
}
