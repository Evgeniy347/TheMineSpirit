package com.theminespirit.core.tactics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class TraceBuilder {
    private static final int TOP_REASONS = 3;

    private TraceBuilder() {}

    static Trace build(
            Decision decision,
            Scores scores,
            ActionChoice action,
            RetreatChoice retreat,
            StateStep state,
            ModeStep mode) {
        Map<TacticalOption, Double> totals = new EnumMap<>(TacticalOption.class);
        for (TacticalOption option : TacticalOption.values()) {
            totals.put(option, scores.total(option));
        }
        return new Trace(
                decision.rule(),
                decision.hold(),
                decision.excludingRules(),
                totals,
                shares(scores),
                action.rule(),
                retreat.rule(),
                state.rule(),
                mode.rule(),
                whyNotFight(decision, scores));
    }

    private static List<Trace.FactorShare> shares(Scores scores) {
        List<Trace.FactorShare> shares = new ArrayList<>();
        for (Factor factor : Factor.values()) {
            shares.add(new Trace.FactorShare(
                    factor,
                    scores.factor(factor),
                    scores.contribution(TacticalOption.FIGHT, factor),
                    scores.contribution(TacticalOption.RETREAT, factor),
                    scores.contribution(TacticalOption.IGNORE, factor)));
        }
        return shares;
    }

    private static String whyNotFight(Decision decision, Scores scores) {
        if (decision.option() == TacticalOption.FIGHT) {
            return "";
        }
        if (decision.excluded().contains(TacticalOption.FIGHT)) {
            return "бой вычеркнут правилами " + decision.excludingRules();
        }
        if (!Decision.BY_SCORE.equals(decision.rule()) && !Decision.HELD.equals(decision.rule())) {
            return "решило правило " + decision.rule();
        }
        return topReasons(decision.option(), scores);
    }

    private static String topReasons(TacticalOption chosen, Scores scores) {
        Optional<String> reasons = Arrays.stream(Factor.values())
                .filter(factor -> gain(chosen, factor, scores) > 0)
                .sorted(Comparator.comparingDouble((Factor factor) -> gain(chosen, factor, scores))
                        .reversed())
                .limit(TOP_REASONS)
                .map(factor -> factor + " (+" + Math.round(gain(chosen, factor, scores) * 100) / 100.0 + ")")
                .reduce((left, right) -> left + ", " + right);
        return "перевес " + chosen + " над FIGHT: " + reasons.orElse("только удержание прежнего решения");
    }

    private static double gain(TacticalOption chosen, Factor factor, Scores scores) {
        return scores.contribution(chosen, factor) - scores.contribution(TacticalOption.FIGHT, factor);
    }
}
