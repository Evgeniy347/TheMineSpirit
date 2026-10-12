package com.theminespirit.core.tactics;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
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
        Decision decision = choose(facts, scores, escapeRoutes, memory, now);
        if (decision.option() == TacticalOption.RETREAT && escapeRoutes == 0) {
            NoRouteVerdict replacement = hardRules.noRouteReplacement(facts);
            Set<TacticalOption> excluded = EnumSet.noneOf(TacticalOption.class);
            excluded.addAll(decision.excluded());
            excluded.addAll(replacement.excluded());
            List<String> excludingRules = mergeExcluding(decision, replacement);
            return replacement.rule()
                    .map(rule -> new Decision(rule.noRouteOption().get(), rule.id(), excluded, excludingRules,
                            HoldStatus.NONE))
                    .orElseGet(() -> new Decision(TacticalOption.FIGHT, Decision.ALL_EXCLUDED, excluded,
                            excludingRules, HoldStatus.NONE));
        }
        return decision;
    }

    private static List<String> mergeExcluding(Decision decision, NoRouteVerdict replacement) {
        List<String> merged = new ArrayList<>(decision.excludingRules());
        for (String rule : replacement.excludingRules()) {
            if (!merged.contains(rule)) {
                merged.add(rule);
            }
        }
        return merged;
    }

    private Decision choose(FactMap facts, Scores scores, int escapeRoutes, TacticsMemory memory, long now) {
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
