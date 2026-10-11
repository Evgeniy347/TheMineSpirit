package com.theminespirit.core.tactics;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

public final class Decider {
    private final HardRules hardRules;
    private final ScoreWeights weights;
    private final Hysteresis hysteresis;

    public Decider(HardRules hardRules, ScoreWeights weights) {
        this.hardRules = hardRules;
        this.weights = weights;
        this.hysteresis = new Hysteresis(weights);
    }

    public Hysteresis hysteresis() {
        return hysteresis;
    }

    public Decision decide(FactMap facts, Scores scores, int escapeRoutes, TacticsMemory memory, long now) {
        HardVerdict verdict = hardRules.evaluate(facts);
        if (verdict.decisive().isPresent()) {
            HardRule rule = verdict.decisive().get();
            HoldStatus hold = memory.previous().isPresent() ? HoldStatus.RELEASED_BY_RULE : HoldStatus.NONE;
            return new Decision(rule.decision().get(), rule.id(), verdict.excluded(), verdict.excludingRules(), hold);
        }
        Set<TacticalOption> candidates = EnumSet.allOf(TacticalOption.class);
        candidates.removeAll(verdict.excluded());
        if (candidates.isEmpty()) {
            TacticalOption fallback = escapeRoutes > 0 ? TacticalOption.RETREAT : TacticalOption.FIGHT;
            return new Decision(fallback, Decision.ALL_EXCLUDED, verdict.excluded(), verdict.excludingRules(),
                    HoldStatus.NONE);
        }
        double hpFraction = facts.number(FactName.SELF_HP_FRACTION);
        HoldStatus hold = hysteresis.status(memory, now, hpFraction, candidates);
        if (candidates.size() == 1) {
            return new Decision(candidates.iterator().next(), Decision.SOLE_CANDIDATE, verdict.excluded(),
                    verdict.excludingRules(), hold == HoldStatus.RELEASED_EXCLUDED ? hold : HoldStatus.NONE);
        }
        if (hold == HoldStatus.HELD) {
            return new Decision(memory.previous().get(), Decision.HELD, verdict.excluded(), verdict.excludingRules(),
                    hold);
        }
        Optional<TacticalOption> sticky = hold == HoldStatus.BONUS ? memory.previous() : Optional.empty();
        return new Decision(scores.best(candidates, weights, sticky), Decision.BY_SCORE, verdict.excluded(),
                verdict.excludingRules(), hold);
    }
}
