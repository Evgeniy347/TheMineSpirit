package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.tactics.Decision;
import com.theminespirit.core.tactics.HoldStatus;
import java.util.List;

public record DecisionRecord(long tick, String spiritId, String spiritName, String changed, String before,
        String after, String rule, HoldStatus hold, List<ReasonFactor> factors, DecisionFacts facts) {
    public static final String BY_SCORE = Decision.BY_SCORE;
    public static final String HELD = Decision.HELD;

    public DecisionRecord {
        factors = List.copyOf(factors);
    }

    public boolean byScore() {
        return BY_SCORE.equals(rule) || HELD.equals(rule);
    }
}
